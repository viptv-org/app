#!/usr/bin/env python3
"""Browser smoke check: a fresh, signed-out visitor can sign in on the web app (app#55).

Serves a built web distribution next to a fake signed-out backend, opens `/` in a
fresh Chromium context, and checks that

1. Compose attaches its drawing surface (a <canvas>, also inside shadow roots)
   without any resize or interaction,
2. the Username and Password fields take keyboard input, and
3. Sign in posts /api/auth/login with what was typed and reaches the profile
   picker (a /api/bootstrap request follows the login).

No real account or server is involved: every /api request is answered here.
The web app accepts only an HTTPS origin, so the fake serves HTTPS with a
throwaway self-signed certificate (needs `openssl`) that the browser is told
to accept.

Usage (after `./gradlew :composeApp:wasmJsBrowserDistribution`):

    python3 tools/web_signin_smoke.py \
        --dist composeApp/build/dist/wasmJs/productionExecutable \
        --out /tmp/web-signin [--headed]

Exits 0 when every check passes and writes screenshots to --out.
"""
import argparse
import json
import mimetypes
import ssl
import subprocess
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from playwright.sync_api import sync_playwright

USERNAME = "smoke-visitor"
PASSWORD = "smoke-password-55"

# Any canvas, including ones Compose places inside a shadow root.
CANVAS_COUNT = """() => {
  const roots = [document];
  let count = 0;
  while (roots.length) {
    const root = roots.pop();
    count += root.querySelectorAll('canvas').length;
    root.querySelectorAll('*').forEach(el => { if (el.shadowRoot) roots.push(el.shadowRoot); });
  }
  return count;
}"""


def chromium_binary():
    """The newest Chromium in the Playwright cache, for when the Python package's pinned build is absent."""
    cache = Path.home() / ".cache" / "ms-playwright"
    found = sorted(cache.glob("chromium-*/chrome-linux*/chrome"), key=lambda c: int(c.parts[-3].split("-")[1]))
    return str(found[-1]) if found else None


def fake_backend(dist: Path, log: list):
    lock = threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def record(self, body=None):
            with lock:
                log.append({"method": self.command, "path": self.path, "body": body})

        def reply_json(self, status, value):
            data = json.dumps(value).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            path = self.path.split("?", 1)[0]
            if path.startswith("/api/"):
                self.record()
                if path == "/api/auth/status":
                    return self.reply_json(200, {"authenticated": False})
                if path == "/api/bootstrap":
                    signed_in = any(e["path"] == "/api/auth/login" for e in log)
                    if not signed_in:
                        return self.reply_json(401, {"error": "Sign in first"})
                    return self.reply_json(200, {"identity": {"csrf_token": "smoke-csrf", "profiles": [
                        {"id": "smoke-profile", "name": "Smoke QA"}]}})
                return self.reply_json(404, {"error": "Not in the smoke fake"})
            file = (dist / path.lstrip("/")).resolve()
            if not file.is_file() or dist not in file.parents:
                file = dist / "index.html"
            data = file.read_bytes()
            kind = "application/wasm" if file.suffix == ".wasm" else (mimetypes.guess_type(file.name)[0] or "application/octet-stream")
            self.send_response(200)
            self.send_header("Content-Type", kind)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_POST(self):
            length = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(length).decode() if length else ""
            try:
                body = json.loads(raw) if raw else None
            except ValueError:
                body = raw
            self.record(body)
            if self.path == "/api/auth/login":
                return self.reply_json(200, {"csrf_token": "smoke-csrf"})
            return self.reply_json(404, {"error": "Not in the smoke fake"})

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    keys = Path(tempfile.mkdtemp(prefix="web-signin-smoke-"))
    subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1", "-subj", "/CN=127.0.0.1",
                    "-keyout", str(keys / "key.pem"), "-out", str(keys / "cert.pem")], check=True, capture_output=True)
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.load_cert_chain(keys / "cert.pem", keys / "key.pem")
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def visit(browser, origin, scenario, args, log, check):
    """One fresh visitor: a new browser context, no cookies, no resize, no prior interaction."""
    context = browser.new_context(ignore_https_errors=True, viewport={"width": args.width, "height": args.height})
    page = context.new_page()
    errors = []
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(origin + "/")
    for _ in range(120):
        if page.evaluate(CANVAS_COUNT) > 0 and page.get_by_role("textbox", name="Username").count() > 0:
            break
        page.wait_for_timeout(250)
    page.wait_for_timeout(1000)
    shot = lambda name: page.screenshot(path=str(args.out / f"{scenario}-{name}.png"))
    shot("01-fresh-visitor")
    canvases = page.evaluate(CANVAS_COUNT)
    check(canvases > 0, f"[{scenario}] Compose canvas attached on a cold load without resizing ({canvases})")
    check(any(e["path"] == "/api/auth/status" for e in log), f"[{scenario}] the signed-out restore probe ran")

    def click(role, name):
        # Compose mirrors its semantics into an accessibility DOM inside its shadow root; the pointer
        # goes to the canvas at that node's position, exactly where the visitor sees the control.
        box = page.get_by_role(role, name=name).bounding_box()
        assert box, f"No {role} '{name}' on the page"
        page.mouse.click(box["x"] + box["width"] / 2, box["y"] + box["height"] / 2)
        page.wait_for_timeout(300)

    click("textbox", "Username")
    page.keyboard.type(USERNAME, delay=20)
    click("textbox", "Password")
    page.keyboard.type(PASSWORD, delay=20)
    page.wait_for_timeout(400)
    shot("02-form-filled")
    if scenario == "enter":
        page.keyboard.press("Enter")  # app#96: Enter in Password submits the form
    else:
        click("button", "Sign in")
    logins = []
    for _ in range(40):
        logins = [e for e in log if e["method"] == "POST" and e["path"] == "/api/auth/login"]
        if logins and any(e["path"] == "/api/bootstrap" for e in log[log.index(logins[0]):]):
            break
        page.wait_for_timeout(250)
    page.wait_for_timeout(1500)
    shot("03-after-sign-in")
    body = logins[0]["body"] if logins else None
    check(len(logins) == 1, f"[{scenario}] signing in posted /api/auth/login once ({len(logins)})")
    check(isinstance(body, dict) and body.get("username") == USERNAME and body.get("password") == PASSWORD,
          f"[{scenario}] the login carried the typed username and password")
    check(bool(logins) and any(e["path"] == "/api/bootstrap" for e in log[log.index(logins[0]):]),
          f"[{scenario}] the signed-in bootstrap followed (profile picker)")
    check(not errors, f"[{scenario}] no page errors {errors}")
    print(f"     [{scenario}] requests: " + json.dumps([f'{e["method"]} {e["path"]}' for e in log]))
    context.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dist", type=Path, default=Path("composeApp/build/dist/wasmJs/productionExecutable"))
    parser.add_argument("--out", type=Path, default=Path("/tmp/web-signin-smoke"))
    parser.add_argument("--headed", action="store_true")
    parser.add_argument("--chromium", help="Chromium executable; default: Playwright's own, else the newest in ~/.cache/ms-playwright")
    parser.add_argument("--width", type=int, default=1920)
    parser.add_argument("--height", type=int, default=1080)
    args = parser.parse_args()
    dist = args.dist.resolve()
    if not (dist / "index.html").is_file():
        sys.exit(f"No web build at {dist}; run ./gradlew :composeApp:wasmJsBrowserDistribution")
    args.out.mkdir(parents=True, exist_ok=True)
    log: list = []
    server = fake_backend(dist, log)
    origin = f"https://127.0.0.1:{server.server_port}"
    failures = []

    def check(ok, message):
        print(("PASS " if ok else "FAIL ") + message)
        if not ok:
            failures.append(message)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not args.headed, executable_path=args.chromium or chromium_binary())
        for scenario in ("enter", "button"):
            log.clear()
            visit(browser, origin, scenario, args, log, check)
        browser.close()
    server.shutdown()
    print(f"screenshots in {args.out}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
