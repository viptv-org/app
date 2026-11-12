#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import struct
import subprocess
import tempfile
import time


def run(args, env, timeout=30):
    return subprocess.run(args, env=env, capture_output=True, check=True, timeout=timeout)


class Ipc:
    def __init__(self, path):
        self.socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.socket.settimeout(3)
        self.socket.connect(str(path))
        self.stream = self.socket.makefile("rwb", buffering=0)
        self.request_id = 0

    def call(self, *command):
        self.request_id += 1
        self.stream.write((json.dumps({"command": command, "request_id": self.request_id}) + "\n").encode())
        while True:
            line = self.stream.readline(64 * 1024)
            if not line:
                raise RuntimeError("mpv IPC closed")
            reply = json.loads(line)
            if reply.get("request_id") == self.request_id:
                if reply.get("error") != "success":
                    raise RuntimeError(str(reply))
                return reply.get("data")

    def property(self, name):
        return self.call("get_property", name)

    def subtitle_text(self):
        try:
            return self.property("sub-text")
        except RuntimeError as error:
            if "property unavailable" not in str(error):
                raise
            return ""

    def close(self):
        self.stream.close()
        self.socket.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mpv", required=True)
    parser.add_argument("--ffmpeg", required=True)
    parser.add_argument("--library-path", default="")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    env = dict(os.environ)
    if args.library_path:
        env["LD_LIBRARY_PATH"] = args.library_path
    args.output.parent.mkdir(parents=True, exist_ok=True)
    report = {"scope": "owned-local-file/null-output-only", "networkAllowed": False}
    with tempfile.TemporaryDirectory(prefix="viptv-subtitle-desktop-", dir="/tmp") as temporary:
        root = Path(temporary)
        wav = root / "owned-silence.wav"
        size = 48_000 * 2 * 2 * 700
        with wav.open("wb") as stream:
            stream.write(struct.pack("<4sI4s4sIHHIIHH4sI", b"RIFF", size + 36, b"WAVE", b"fmt ", 16,
                                     1, 2, 48_000, 192_000, 4, 16, b"data", size))
            stream.truncate(44 + size)
        fixture = root / "owned.mp4"
        run([args.ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-threads", "1", "-filter_threads", "1",
             "-f", "lavfi", "-i", "color=black:size=128x72:rate=1:duration=700", "-i", str(wav),
             "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", "-threads", "1", "-shortest", str(fixture)], env)
        report["fixtureSha256"] = hashlib.sha256(fixture.read_bytes()).hexdigest()
        report["fixtureBytes"] = fixture.stat().st_size
        captions = root / "owned.srt"
        captions.write_text("1\n00:09:50,000 --> 00:09:51,000\nAnchored local cue\n")
        ipc_path = root / "mpv.sock"
        log = root / "mpv.log"
        ipc = None
        with log.open("wb") as output:
            player = subprocess.Popen([args.mpv, "--no-config", "--no-terminal", "--pause=yes", "--keep-open=yes",
                                       "--vo=null", "--ao=null", "--force-window=yes", "--sub-auto=no", "--sub-file=" + str(captions),
                                       "--input-ipc-server=" + str(ipc_path), str(fixture)], env=env, stdout=output, stderr=output)
            try:
                deadline = time.monotonic() + 10
                while not ipc_path.exists():
                    if player.poll() is not None or time.monotonic() > deadline:
                        raise RuntimeError("mpv startup failed: " + log.read_text()[-2000:])
                    time.sleep(.02)
                ipc = Ipc(ipc_path)
                while True:
                    try:
                        if ipc.property("duration") >= 699:
                            break
                    except RuntimeError:
                        pass
                    if time.monotonic() > deadline:
                        raise RuntimeError("mpv file load timeout")
                    time.sleep(.02)
                while True:
                    tracks = ipc.property("track-list")
                    selected = [track for track in tracks if track["type"] == "audio" and track.get("selected")]
                    if len(selected) == 1:
                        break
                    if time.monotonic() > deadline:
                        raise RuntimeError("Exact selected audio track required: " + str(tracks) + log.read_text()[-2000:])
                    time.sleep(.02)
                report["audioTrack"] = {key: selected[0].get(key) for key in ["id", "ff-index", "codec", "audio-channels", "demux-samplerate"]}
                report["mpvVersion"] = ipc.property("mpv-version")
                report["audioParams"] = ipc.property("audio-params")
                report["playlistEntriesBefore"] = len(ipc.property("playlist"))
                ipc.call("seek", 592.1, "absolute+exact")
                deadline = time.monotonic() + 4
                while abs(ipc.property("time-pos") - 592.1) > .1:
                    if time.monotonic() > deadline:
                        raise RuntimeError("mpv seek did not settle")
                    time.sleep(.02)
                report["originalCue"] = ipc.subtitle_text()
                ipc.call("set_property", "pause", False)
                ipc.call("set_property", "sub-speed", 1.001)
                ipc.call("set_property", "sub-delay", 1.46)
                deadline = time.monotonic() + 3
                while True:
                    cue = ipc.subtitle_text()
                    if cue or time.monotonic() > deadline:
                        break
                    time.sleep(.02)
                report["anchoredCue"] = cue
                report["subSpeed"] = ipc.property("sub-speed")
                report["subDelay"] = ipc.property("sub-delay")
                report["playlistEntriesAfter"] = len(ipc.property("playlist"))
                report["playerPidUnchanged"] = player.poll() is None
                windows = []
                for epoch, start in [(1, 592), (2, 100)]:
                    command = [args.ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "info", "-threads", "1", "-filter_threads", "1",
                               "-protocol_whitelist", "file,pipe", "-ss", str(start), "-t", "2", "-copyts", "-i", str(fixture),
                               "-map", "0:" + str(selected[0]["ff-index"]), "-vn", "-sn", "-dn", "-af",
                               "aformat=sample_fmts=s16:sample_rates=16000:channel_layouts=mono,asetnsamples=n=320:p=0,ashowinfo",
                               "-f", "s16le", "pipe:1"]
                    decoded = run(command, env, timeout=8)
                    timestamps = [float(value) for value in re.findall(r"pts_time:([0-9.\-]+)", decoded.stderr.decode())]
                    if len(decoded.stdout) > 64_000 or not timestamps or abs(timestamps[0] - start) > .05:
                        raise RuntimeError("PCM byte/timestamp budget failed: " + decoded.stderr.decode()[-2000:])
                    windows.append({"epoch": epoch, "requestedStart": start, "firstPts": timestamps[0], "lastPts": timestamps[-1],
                                    "frames": len(timestamps), "pcmBytes": len(decoded.stdout)})
                report["boundedDecoderWindows"] = windows
                report["fixtureUnchanged"] = report["fixtureSha256"] == hashlib.sha256(fixture.read_bytes()).hexdigest()
                report["anchoredPropertyQualified"] = not report["originalCue"] and cue == "Anchored local cue"
            finally:
                if ipc is not None:
                    ipc.close()
                player.terminate()
                try:
                    player.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    player.kill(); player.wait(timeout=3)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report))
    return 0 if report["anchoredPropertyQualified"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
