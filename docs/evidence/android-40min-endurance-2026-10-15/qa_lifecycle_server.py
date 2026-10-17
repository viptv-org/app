#!/usr/bin/env python3
"""Synthetic HTTPS API facade for real Android Activity/controller lifecycle QA.

No real accounts, providers, tracking service or production backend are involved.
Only method/path counts and synthetic media-clock observations are retained.
"""
import argparse
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import ssl
import threading
import time
from urllib.parse import urlsplit
from urllib.parse import parse_qs
from datetime import datetime, timezone


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--managed", action="store_true", help="Model unique gateway leases and absolute seek rebasing")
    parser.add_argument("--direct", action="store_true", help="Negotiate direct HLS; seeks stay in the actual renderer")
    parser.add_argument("--rolling", action="store_true", help="Advance the real playlist origin continuously for endurance QA")
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--media", type=Path, required=True)
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--bootstrap-delay-ms", type=int, default=0)
    parser.add_argument("--calendar-later-delay-ms", type=int, default=0)
    parser.add_argument("--calendar-count", type=int, default=0)
    parser.add_argument("--home-count", type=int, default=1)
    parser.add_argument("--artwork", type=Path)
    args = parser.parse_args()
    assert not (args.direct and args.managed)
    maximum = len(list(args.media.glob("segment-*.ts")))
    assert maximum >= 16
    counts = Counter()
    state = {"selected": None, "version": 1, "expired": False, "events": [], "progress": [], "media_started": None, "playlists": [], "requests": [], "measurements": [], "leases": {}}
    lock = threading.Lock()
    profiles = [{"id": "one", "name": "First fixture profile"}, {"id": "two", "name": "Second fixture profile"}]
    card = {"identity": {"id": "lifecycle-fixture", "kind": "movie", "ids": []}, "provider_id": "addon:1",
            "title": "Lifecycle fixture", "description": "Synthetic local media", "badges": [],
            "metadata_origin": {"provider_id": "stremio", "connection_id": "addon:1", "incarnation": "a" * 32}}
    scope = {"connection_id": "fixture", "account_id": "fixture", "profile_id": "one", "revision": 1}
    preferences = {"revision": 1, "preferences": {"source_selection": "ask"}}

    class Handler(BaseHTTPRequestHandler):
        def answer(self, value, status=200, content_type="application/json"):
            if self.request_path.startswith("/api/"):
                state["requests"].append({"method": self.command, "path": self.request_path,
                                          "query": self.request_query, "elapsed_ms": round((time.monotonic() - self.request_started) * 1000)})
            body = value if isinstance(value, bytes) else json.dumps(value).encode()
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            self.handle_request()

        def do_POST(self):
            self.handle_request()

        def do_PUT(self):
            self.handle_request()

        def do_DELETE(self):
            self.handle_request()

        def handle_request(self):
            self.request_started = time.monotonic()
            path = urlsplit(self.path).path
            self.request_path = path
            self.request_query = urlsplit(self.path).query
            query = parse_qs(self.request_query)
            if path == "/api/v3/bootstrap":
                time.sleep(args.bootstrap_delay_ms / 1000)
            if path == "/api/v3/calendar" and int(query.get("skip", [0])[0]) > 0:
                time.sleep(args.calendar_later_delay_ms / 1000)
            data = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
            with lock:
                counts[f"{self.command} {path}"] += 1
                if path == "/qa/status":
                    return self.answer({"counts": counts, "events": state["events"], "progress": state["progress"], "selected": state["selected"], "playlists": state["playlists"], "requests": state["requests"], "measurements": state["measurements"], "delivery_kind": "direct" if args.direct else "gateway", "leases": state["leases"]})
                if path == "/qa/performance" and self.command == "POST":
                    state["measurements"].append(data)
                    return self.answer({"ok": True})
                if path == "/qa/reset-media" and self.command == "POST":
                    state["media_started"] = None
                    return self.answer({"ok": True})
                if path == "/qa/expire":
                    state["expired"] = True
                    return self.answer({"ok": True})
                if path == "/api/auth/device/login":
                    if data.get("username") != "lifecycle-fixture" or data.get("password") != "synthetic-fixture-password":
                        return self.answer({}, 401)
                    return self.answer({"access_token": "fixture-access-1", "refresh_token": "fixture-refresh-1"})
                if path == "/api/auth/device/refresh":
                    if data.get("refresh_token") != f"fixture-refresh-{state['version']}":
                        return self.answer({}, 401)
                    state["version"] += 1; state["expired"] = False
                    return self.answer({"access_token": f"fixture-access-{state['version']}", "refresh_token": f"fixture-refresh-{state['version']}"})
                if path.startswith("/media/"):
                    return self.media(path)
                if path == "/assets/fixture.jpg" and args.artwork:
                    return self.answer(args.artwork.read_bytes(), content_type="image/jpeg")
                if state["expired"] or self.headers.get("Authorization") != f"Bearer fixture-access-{state['version']}":
                    return self.answer({}, 401)
                if path == "/api/v3/bootstrap":
                    cards = [card] + [dict(card, identity={"id": f"home-{i}", "kind": "movie", "ids": []}, title=f"Home fixture {i:03d}") for i in range(1, args.home_count)]
                    rows = [{"id": f"fixture-{i}", "title": f"Fixture titles {i + 1}", "provider_id": "stremio", "state": "Ready", "cards": cards[i:i + 24]} for i in range(0, len(cards), 24)]
                    home = {"scope": scope, "revision": 1, "featured": cards[:5], "rows": rows, "complete": True} if state["selected"] else None
                    return self.answer({"identity": {"profiles": profiles, "profile_id": state["selected"]}, "home": home,
                                        "library": {"cards": [], "complete": True}, "library_membership": {"identities": [], "revision": 1, "complete": True},
                                        "preferences": preferences})
                if path == "/api/v3/calendar":
                    skip = int(query.get("skip", [0])[0]); limit = int(query.get("limit", [100])[0])
                    end = min(args.calendar_count, skip + limit)
                    date = datetime.now(timezone.utc).replace(hour=12, minute=0, second=0, microsecond=0).isoformat().replace("+00:00", "Z")
                    entries = [{"id": f"calendar-{i}", "date": date, "episode": i + 1, "season": 1,
                                "card": dict(card, identity={"id": f"calendar-title-{i}", "kind": "series", "ids": []}, title=f"Calendar fixture {i:04d}")}
                               for i in range(skip, end)]
                    return self.answer({"entries": entries, "complete": end >= args.calendar_count, "next_cursor": str(end) if end < args.calendar_count else None})
                if path == "/api/auth/profile":
                    if data.get("profile_id") not in ("one", "two"):
                        return self.answer({}, 400)
                    state["selected"] = data["profile_id"]
                    return self.answer({})
                if path == "/api/v3/search":
                    return self.answer({"rows": [{"id": "fixture-search", "title": "Fixture titles", "provider_id": "stremio", "state": "Ready", "cards": [card]}], "complete": True})
                if path == "/api/v3/meta/movie/lifecycle-fixture":
                    return self.answer({"item": card, "episodes": []})
                if path == "/api/v2/streams":
                    return self.answer({"id": "fixture-discovery"})
                if path == "/api/v2/streams/fixture-discovery":
                    return self.answer({"events": [{"source": "fixture", "streams": [{"id": "fixture-stream", "name": "Synthetic HLS", "presentation": {"delivery_format": "hls"}}]}], "done": True})
                if path == "/api/v3/profiles/one/source-ranking":
                    return self.answer({"streams": [{"id": "fixture-stream"}]})
                if path == "/api/v2/playback":
                    lease_id = f"fixture-lease-{len(state['leases']) + 1}" if args.managed else "fixture-lease"
                    requested = max(600.0, float(data.get("position", 0)))
                    base = int((requested - 600) // 2) if args.managed else 0
                    state["leases"][lease_id] = {"base": base, "requested": requested, "started": time.monotonic()}
                    media_path = f"/media/{lease_id}/index.m3u8" if args.managed else "/media/index.m3u8"
                    return self.answer({"id": lease_id, "status": "ready", "renew_after_seconds": 30,
                                        "delivery": {"kind": "direct" if args.direct else "gateway", "url": f"https://127.0.0.1:{self.server.server_port}{media_path}",
                                                     "format": "hls", "position": requested - 600 - base * 2 if args.managed else 0, "timeline_offset_seconds": 600 + base * 2, "duration": 600 + maximum * 2, "live": False}})
                if path.startswith("/api/v2/playback/fixture-lease"):
                    return self.answer({})
                if path == "/api/v3/subtitles/movie/lifecycle-fixture":
                    return self.answer({"scope": scope, "candidates": [], "providers": [], "complete": True})
                if path == "/api/v3/profiles/one/playback-events":
                    state["events"].append({key: data.get(key) for key in ("event", "sequence", "position_millis", "duration_millis")})
                    return self.answer({"state": "recorded", "action": data.get("event"), "completed": False})
                if path == "/api/v3/profiles/one/progress":
                    record = data.get("record", {})
                    state["progress"].append({key: record.get(key) for key in ("position_millis", "duration_millis")})
                    return self.answer({})
                return self.answer({"fixture_error": "Unexpected endpoint"}, 404)

        def media(self, path):
            if state["media_started"] is None:
                state["media_started"] = time.monotonic()
            elapsed = time.monotonic() - state["media_started"]
            base = 0
            lease_match = re.fullmatch(r"/media/(fixture-lease-\d+)/(index.m3u8|segment-\d{3,4}\.ts)", path)
            if lease_match:
                lease = state["leases"].get(lease_match.group(1))
                if not lease:
                    return self.answer({}, 404)
                base = lease["base"]
                elapsed = time.monotonic() - lease["started"]
                path = "/media/" + lease_match.group(2)
            if path == "/media/index.m3u8":
                first = max(2, int(elapsed // 2) - 20) if args.rolling and elapsed >= 6 else (2 if elapsed >= 6 else 0)
                last = min(maximum, 10 + int(elapsed // 2)) if args.rolling else min(maximum, first + 10 + max(0, int((elapsed - 6) // 2)))
                first += base
                last = min(maximum, last + base)
                if len(state["playlists"]) < (2000 if args.rolling else 3):
                    state["playlists"].append({"elapsed_ms": round(elapsed * 1000), "first_sequence": first, "last_exclusive": last, "base_sequence": base})
                playlist = f"#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:{first}\n"
                playlist += "".join(f"#EXTINF:2.000000,\nsegment-{index:03d}.ts\n" for index in range(first, last))
                return self.answer(playlist.encode(), content_type="application/vnd.apple.mpegurl")
            if path == "/media/format-eac3.mkv":
                return self.answer((args.media / "format-eac3.mkv").read_bytes(), content_type="video/x-matroska")
            if re.fullmatch(r"/media/segment-\d{3,4}\.ts", path):
                file = args.media / path.rsplit("/", 1)[-1]
                if file.is_file():
                    return self.answer(file.read_bytes(), content_type="video/mp2t")
            return self.answer({}, 404)

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    if args.artwork:
        card["poster"] = card["landscape"] = f"https://127.0.0.1:{server.server_port}/assets/fixture.jpg"
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(args.root / "fixture.crt", args.root / "fixture.key")
    server.socket = context.wrap_socket(server.socket, server_side=True)
    print(json.dumps({"origin": f"https://127.0.0.1:{server.server_port}", "port": server.server_port}), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
