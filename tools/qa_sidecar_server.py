#!/usr/bin/env python3
"""Serve generated public test media with a deterministic rolling HLS window."""
import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
import threading
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--port", type=int, default=0)
    args = parser.parse_args()
    root = args.root.resolve()
    assert (root / "segment-015.ts").is_file(), "Generate the 32-second/two-second-segment fixture first"
    lock = threading.Lock()
    started = None

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            nonlocal started
            with lock:
                if started is None:
                    started = time.monotonic()
                elapsed = time.monotonic() - started
                first = 2 if elapsed >= 6 else 0
                last = min(16, first + 10 + max(0, int((elapsed - 6) // 2)))
            path = self.path.split("?", 1)[0]
            content_type = "application/vnd.apple.mpegurl"
            if path == "/index.m3u8":
                body = ('#EXTM3U\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="text",NAME="Embedded",LANGUAGE="en",'
                        'DEFAULT=NO,AUTOSELECT=YES,URI="subtitles.m3u8"\n'
                        '#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=320x180,CODECS="avc1.42c00c,mp4a.40.2",SUBTITLES="text"\n'
                        'media.m3u8\n').encode()
            elif path in ("/media.m3u8", "/subtitles.m3u8"):
                subtitle = path == "/subtitles.m3u8"
                body = (f"#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:{first}\n" +
                        "".join(f"#EXTINF:2.000000,\n{'sub' if subtitle else 'segment'}-{n:03d}.{'vtt' if subtitle else 'ts'}\n"
                                for n in range(first, last))).encode()
            elif re.fullmatch(r"/sub-\d{3}\.vtt", path):
                content_type = "text/vtt"
                body = ("WEBVTT\nX-TIMESTAMP-MAP=LOCAL:00:00:00.000,MPEGTS:126000\n\n"
                        "00:00:00.000 --> 00:01:00.000\nEmbedded fixture\n").encode()
            elif re.fullmatch(r"/segment-\d{3}\.ts", path) and (root / path[1:]).is_file():
                content_type = "video/mp2t"
                body = (root / path[1:]).read_bytes()
            else:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(json.dumps({"port": server.server_port, "emulator_uri": f"http://10.0.2.2:{server.server_port}/index.m3u8"}), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
