#!/usr/bin/env python3
"""Browser smoke check: a fresh, signed-out visitor can sign in on the web app (app#55).

Serves a built web distribution next to a fake signed-out backend, opens `/` in a
fresh Chromium context, and checks that

1. Compose attaches its drawing surface (a <canvas>, also inside shadow roots)
   without any resize or interaction,
2. the Username and Password fields take keyboard input, and
3. signing in posts /api/auth/login with what was typed and reaches the profile
   picker (a /api/bootstrap request follows the login).

Three fresh visitors each fill the form by clicking where the fields are drawn:
`enter` presses Enter in Password (app#96), `keyboard` presses Enter in Username
to move to Password and Enter again to sign in, and `button` clicks Sign in.

No real account or server is involved: every /api request is answered here.
The web app accepts only an HTTPS origin, so the fake serves HTTPS with a
throwaway self-signed certificate (needs `openssl`) that the browser is told
to accept.

Usage (after `./gradlew :composeApp:wasmJsBrowserDistribution`):

    python3 tools/web_signin_smoke.py \
        --dist composeApp/build/dist/wasmJs/productionExecutable \
        --out /tmp/web-signin [--headed]

Headed on a machine without a display or GPU:

    xvfb-run -a -s "-screen 0 1920x1080x24" python3 tools/web_signin_smoke.py --headed \
        --browser-arg=--use-angle=swiftshader --browser-arg=--enable-unsafe-swiftshader

`--cpu-throttle N` divides the browser's CPU by N (Chrome DevTools throttling), which
is how a starved main thread reaches the Compose web text input. At `--cpu-throttle 6`
this check fails on every run with scrambled characters: that is app#105, a Compose
Multiplatform defect (queued DOM edits are applied against a text the hidden input
element has since rewritten), not an app defect. See docs/WEB_TEXT_INPUT_ORDER.md.

Exits 0 when every check passes and writes screenshots to --out.
"""
import argparse
import json
import mimetypes
import re
import shutil
import ssl
import subprocess
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from playwright.sync_api import sync_playwright

SCENARIOS = ("enter", "keyboard", "button")
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


def launch(p, args):
    """Playwright's own Chromium, else the newest numbered build in its cache (the Python package may pin an absent one)."""
    options = {"headless": not args.headed, "args": args.browser_arg}
    if args.chromium:
        return p.chromium.launch(executable_path=args.chromium, **options)
    try:
        return p.chromium.launch(**options)
    except Exception:
        cache = Path.home() / ".cache" / "ms-playwright"
        builds = [c for c in cache.glob("chromium-*/chrome-linux*/chrome") if re.fullmatch(r"chromium-\d+", c.parts[-3])]
        if not builds:
            raise
        newest = max(builds, key=lambda c: int(c.parts[-3].split("-")[1]))
        return p.chromium.launch(executable_path=str(newest), **options)


def login_then_bootstrap(log):
    """The /api/auth/login posts, and whether a /api/bootstrap followed the first one."""
    with LOG_LOCK:
        entries = list(log)
    logins = [e for e in entries if e["method"] == "POST" and e["path"] == "/api/auth/login"]
    followed = bool(logins) and any(e["path"] == "/api/bootstrap" for e in entries[entries.index(logins[0]):])
    return logins, followed


LOG_LOCK = threading.Lock()


def fake_backend(dist: Path, log: list, keys: Path):

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def record(self, body=None):
            with LOG_LOCK:
                log.append({"method": self.command, "path": self.path.split("?", 1)[0], "body": body})

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
                    if not login_then_bootstrap(log)[0]:
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
            if self.path.split("?", 1)[0] == "/api/auth/login":
                return self.reply_json(200, {"csrf_token": "smoke-csrf"})
            return self.reply_json(404, {"error": "Not in the smoke fake"})

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
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
    if args.cpu_throttle != 1:
        context.new_cdp_session(page).send("Emulation.setCPUThrottlingRate", {"rate": args.cpu_throttle})
        print(f"[{scenario}] CPU throttled {args.cpu_throttle}x")
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
        target = page.get_by_role(role, name=name)
        box = target.bounding_box() if target.count() == 1 else None
        if not check(box is not None, f"[{scenario}] the {role} '{name}' is on the page"):
            return False
        page.mouse.click(box["x"] + box["width"] / 2, box["y"] + box["height"] / 2)
        page.wait_for_timeout(args.settle)
        return True

    if not click("textbox", "Username"):
        return context.close()
    page.keyboard.type(USERNAME, delay=args.type_delay)
    if scenario == "keyboard":
        page.keyboard.press("Enter")  # Enter in Username moves to Password
        page.wait_for_timeout(args.settle)
    elif not click("textbox", "Password"):
        return context.close()
    page.keyboard.type(PASSWORD, delay=args.type_delay)
    page.wait_for_timeout(400)
    shot("02-form-filled")
    if scenario in ("enter", "keyboard"):
        page.keyboard.press("Enter")  # app#96: Enter in Password submits the form
    elif not click("button", "Sign in"):
        return context.close()
    for _ in range(40):
        if login_then_bootstrap(log)[1]:
            break
        page.wait_for_timeout(250)
    page.wait_for_timeout(1500)  # long enough for a second, duplicate post to show up
    shot("03-after-sign-in")
    logins, followed = login_then_bootstrap(log)
    body = logins[0]["body"] if logins else None
    check(len(logins) == 1, f"[{scenario}] signing in posted /api/auth/login once ({len(logins)})")
    check(isinstance(body, dict) and body.get("username") == USERNAME and body.get("password") == PASSWORD,
          f"[{scenario}] the login carried the typed username and password (fake values; got {body})")
    check(followed, f"[{scenario}] the signed-in bootstrap followed the login")
    check(page.get_by_text("Who’s watching?").count() > 0 and page.get_by_text("Smoke QA").count() > 0,
          f"[{scenario}] the profile picker shows the fake profile")
    check(not errors, f"[{scenario}] no page errors {errors}")
    print(f"     [{scenario}] requests: " + json.dumps([f'{e["method"]} {e["path"]}' for e in log]))
    context.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dist", type=Path, default=Path("composeApp/build/dist/wasmJs/productionExecutable"))
    parser.add_argument("--out", type=Path, default=Path("/tmp/web-signin-smoke"))
    parser.add_argument("--headed", action="store_true")
    parser.add_argument("--browser-arg", action="append", default=[], help="Extra Chromium flag, repeatable. Headed under "
                        "Xvfb without a GPU needs --browser-arg=--use-angle=swiftshader --browser-arg=--enable-unsafe-swiftshader")
    parser.add_argument("--chromium", help="Chromium executable; default: Playwright's own, else the newest in ~/.cache/ms-playwright")
    parser.add_argument("--scenario", action="append", choices=SCENARIOS, help="Run only these; default: all")
    parser.add_argument("--type-delay", type=int, default=20, help="Milliseconds between typed keys")
    parser.add_argument("--settle", type=int, default=300, help="Milliseconds to wait after focusing a field before typing")
    parser.add_argument("--cpu-throttle", type=int, default=1, help="Chrome DevTools CPU slowdown factor. 6 makes app#105 "
                        "(typed characters arriving out of order) fail on every run; 1 is the real-world default")
    parser.add_argument("--width", type=int, default=1920)
    parser.add_argument("--height", type=int, default=1080)
    args = parser.parse_args()
    dist = args.dist.resolve()
    if not (dist / "index.html").is_file():
        sys.exit(f"No web build at {dist}; run ./gradlew :composeApp:wasmJsBrowserDistribution")
    args.out.mkdir(parents=True, exist_ok=True)
    log: list = []
    keys = Path(tempfile.mkdtemp(prefix="web-signin-smoke-"))
    server = fake_backend(dist, log, keys)
    origin = f"https://127.0.0.1:{server.server_port}"
    failures = []

    def check(ok, message):
        print(("PASS " if ok else "FAIL ") + message)
        if not ok:
            failures.append(message)
        return ok

    try:
        with sync_playwright() as p:
            browser = launch(p, args)
            for scenario in args.scenario or SCENARIOS:
                with LOG_LOCK:
                    log.clear()
                visit(browser, origin, scenario, args, log, check)
            browser.close()
    finally:
        server.shutdown()
        shutil.rmtree(keys, ignore_errors=True)
    print(f"screenshots in {args.out}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
