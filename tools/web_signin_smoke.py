#!/usr/bin/env python3
"""Browser smoke check: text typed into the web app's fields arrives exactly (app#55, app#105).

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

The fourth visitor (`password`) signs in, picks the profile, opens
Settings › Password, reads the boxes and computed visibility of the three native
fields there (a Compose interop node with no exact width collapses to 0×N and stays
`visibility: hidden` at 0,0), types into all three, and submits the change.

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
element has since rewritten), not an app defect. See viptv-org/app#105.

Three more scenarios type into fields other than the sign-in form, in the built-in UI preview
(no backend involved): `search` types into the Search page field, `settings` types into
the add-on filter in Settings and checks that the filtered list follows, and `dialog` types
into the Name field of the Add profile dialog. Both compare the
field's value with what was typed after the page has settled.

`--repeat N` runs every selected scenario N times, each in a fresh browser context.

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

SIGNIN_SCENARIOS = ("enter", "keyboard", "button", "password")
# scenario: (preview route, opener button, textbox name, text to type, text the page then shows, focused on open)
FIELD_SCENARIOS = {
    "search":  ("/#ui-preview/Search/Movie", None, "Search", "the wild robot 2024 smoke", None, True),
    "settings": ("/#ui-preview/Settings/Addons", None, "Search add-ons", "Add-on B", "Showing 1 of 3 add-ons", False),
    "dialog":  ("/#ui-preview/Profiles/Locked", "Add profile", "Name", "Kids Room 01", None, True),
    "escape":  ("/#ui-preview/Profiles/Locked", "Add profile", "Name", "", None, True),
    "tab":     ("/#ui-preview/Profiles/Locked", "Add profile", "Name", "", None, True),
}
SCENARIOS = SIGNIN_SCENARIOS + tuple(FIELD_SCENARIOS) + ("device", "device-signin")
USERNAME = "smoke-visitor"
PASSWORD = "smoke-password-55"
CURRENT_PASSWORD = "smoke-current-password-446"
NEW_PASSWORD = "smoke-new-password-446"

# The change-password form renders three native inputs: current, new, confirmation.
PASSWORD_FIELDS = (CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)

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


def wait_until(page, condition, timeout_ms=30000):
    for _ in range(max(1, timeout_ms // 250)):
        if condition():
            return True
        page.wait_for_timeout(250)
    return False


def visible_box(locator):
    """The first matching node that occupies page space; a Compose node can repeat in the semantics DOM."""
    for index in range(locator.count()):
        box = locator.nth(index).bounding_box()
        if box and box["width"] > 0 and box["height"] > 0:
            return box
    return None


def click_box(page, box, settle):
    page.mouse.click(box["x"] + box["width"] / 2, box["y"] + box["height"] / 2)
    page.wait_for_timeout(settle)


def click_role(page, check, scenario, role, name, settle):
    box = visible_box(page.get_by_role(role, name=name))
    if not check(box is not None, f"[{scenario}] the {role} '{name}' is on the page"):
        return False
    click_box(page, box, settle)
    return True


def click_control(page, check, scenario, name, settle):
    """Compose exposes a clickable as an aria-label, a button or just its text, depending on the modifier."""
    box = (visible_box(page.get_by_label(name)) or visible_box(page.get_by_role("button", name=name))
           or visible_box(page.get_by_text(name)))
    if not check(box is not None, f"[{scenario}] the '{name}' control is on the page"):
        return False
    click_box(page, box, settle)
    return True


def login_then_bootstrap(log):
    """The /api/auth/login posts, and whether a /api/bootstrap followed the first one."""
    with LOG_LOCK:
        entries = list(log)
    logins = [e for e in entries if e["method"] == "POST" and e["path"] == "/api/auth/login"]
    followed = bool(logins) and any(e["path"] == "/api/bootstrap" for e in entries[entries.index(logins[0]):])
    return logins, followed


LOG_LOCK = threading.Lock()


def fake_backend(dist: Path, log: list, keys: Path, session: dict):

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
                    return self.reply_json(200, {"authenticated": session.get("device_authenticated", False)})
                if path == "/api/auth/me":
                    return self.reply_json(200, {"account": {"username": USERNAME}})
                # A change-password route that answers a GET with 405 is available; 404 would hide the Password row.
                if path == "/api/auth/password":
                    return self.reply_json(405, {"error": "Method not allowed"})
                if path == "/api/bootstrap":
                    if not login_then_bootstrap(log)[0]:
                        return self.reply_json(401, {"error": "Sign in first"})
                    identity = {"csrf_token": "smoke-csrf", "profiles": [{"id": "smoke-profile", "name": "Smoke QA"}]}
                    if session["profile"]:
                        identity["profile_id"] = session["profile"]
                    return self.reply_json(200, {"identity": identity})
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
            path = self.path.split("?", 1)[0]
            if path == "/api/auth/login":
                session["profile"] = None
                return self.reply_json(200, {"csrf_token": "smoke-csrf"})
            if path == "/api/auth/profile":
                session["profile"] = body.get("profile_id") if isinstance(body, dict) else None
                return self.reply_json(200, {"csrf_token": "smoke-csrf"})
            if path == "/api/auth/password":
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


def visit_field(browser, origin, scenario, args, check):
    """One fresh visitor opens a preview page and types into one text field."""
    route, opener, name, typed, shown, autofocus = FIELD_SCENARIOS[scenario]
    context = browser.new_context(ignore_https_errors=True, viewport={"width": args.width, "height": args.height})
    page = context.new_page()
    if args.cpu_throttle != 1:
        context.new_cdp_session(page).send("Emulation.setCPUThrottlingRate", {"rate": args.cpu_throttle})
    errors = []
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(origin + route)
    ready = opener and page.get_by_role("button", name=opener) or page.get_by_role("textbox", name=name)
    for _ in range(120):
        if page.evaluate(CANVAS_COUNT) > 0 and ready.count() > 0:
            break
        page.wait_for_timeout(250)
    page.wait_for_timeout(1000)
    if opener:
        box = ready.bounding_box()
        page.mouse.click(box["x"] + box["width"] / 2, box["y"] + box["height"] / 2)
        page.get_by_role("textbox", name=name).wait_for(timeout=10000)
        page.wait_for_timeout(500)
    field = page.get_by_role("textbox", name=name)
    if not check(field.count() == 1, f"[{scenario}] the textbox '{name}' is on the page ({field.count()})"):
        return context.close()
    handle = field.element_handle()
    if autofocus:
        check(page.evaluate("(el) => document.activeElement === el", handle),
              f"[{scenario}] the field '{name}' has DOM focus on open")
    else:
        field.click()
        page.wait_for_timeout(args.settle)
    page.wait_for_timeout(args.settle)

    if scenario == "escape":
        page.keyboard.press("Escape")
        page.wait_for_timeout(500)
        page.screenshot(path=str(args.out / f"{scenario}-escaped.png"))
        check(page.get_by_role("textbox", name=name).count() == 0,
              f"[{scenario}] Escape closed the dialog")
        check(not errors, f"[{scenario}] no page errors {errors}")
        return context.close()

    if scenario == "tab":
        page.keyboard.press("Tab")
        page.wait_for_timeout(300)
        after_tab = page.evaluate("""() => { const a = document.activeElement;
            return a ? { tag: a.tagName, label: a.getAttribute('aria-label') } : null; }""")
        check(page.evaluate("(el) => document.activeElement !== el", handle),
              f"[{scenario}] Tab moved focus off the field")
        check(after_tab is not None and after_tab["tag"] != "BODY",
              f"[{scenario}] Tab landed on a control, not the page body ({after_tab})")
        page.keyboard.press("Shift+Tab")
        page.wait_for_timeout(300)
        check(page.evaluate("(el) => document.activeElement === el", handle),
              f"[{scenario}] Shift+Tab returned focus to the field")
        check(not errors, f"[{scenario}] no page errors {errors}")
        return context.close()

    page.keyboard.press("Control+A")
    page.keyboard.type(typed, delay=args.type_delay)
    page.wait_for_timeout(1500)
    page.screenshot(path=str(args.out / f"{scenario}-typed.png"))
    got = page.get_by_role("textbox", name=name).input_value()
    check(got == typed, f"[{scenario}] the field holds exactly what was typed (typed {typed!r}, got {got!r})")
    if shown:
        check(page.get_by_text(shown).count() > 0, f"[{scenario}] the page followed the typed text: '{shown}'")
    check(not errors, f"[{scenario}] no page errors {errors}")
    context.close()


def visit_password(browser, origin, args, log, check):
    """Settings › Password (app#446): the change-password form is a native form, so read its DOM nodes."""
    scenario = "password"
    context = browser.new_context(ignore_https_errors=True, viewport={"width": args.width, "height": args.height})
    page = context.new_page()
    errors = []
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(origin + "/")
    wait_until(page, lambda: page.evaluate(CANVAS_COUNT) > 0 and page.get_by_role("textbox", name="Username").count() > 0)
    page.wait_for_timeout(1000)
    shot = lambda name: page.screenshot(path=str(args.out / f"{scenario}-{name}.png"))
    shot("01-signed-out")

    def sign_in():
        if not click_role(page, check, scenario, "textbox", "Username", args.settle):
            return False
        page.keyboard.type(USERNAME, delay=args.type_delay)
        if not click_role(page, check, scenario, "textbox", "Password", args.settle):
            return False
        page.keyboard.type(PASSWORD, delay=args.type_delay)
        return click_role(page, check, scenario, "button", "Sign in", args.settle)

    if not sign_in():
        return context.close()
    if not check(wait_until(page, lambda: page.get_by_text("Who’s watching?").count() > 0),
                 f"[{scenario}] the profile picker opened after signing in"):
        return context.close()
    if not click_control(page, check, scenario, "Smoke QA", args.settle):
        return context.close()
    if not wait_until(page, lambda: page.get_by_label("Profile menu").count() > 0):
        check(False, f"[{scenario}] the signed-in shell appeared after picking the profile")
        return context.close()
    shot("02-home")

    if not click_control(page, check, scenario, "Profile menu", args.settle):
        return context.close()
    if not check(wait_until(page, lambda: page.get_by_text("Settings").count() > 0),
                 f"[{scenario}] the profile menu lists Settings"):
        shot("03-menu")
        return context.close()
    if not click_control(page, check, scenario, "Settings", args.settle):
        return context.close()
    if not check(wait_until(page, lambda: page.get_by_text("Password").count() > 0),
                 f"[{scenario}] Settings lists the Password page"):
        shot("03-settings")
        return context.close()
    if not click_control(page, check, scenario, "Password", args.settle):
        return context.close()

    inputs = page.locator("input.viptv-cred-input")
    if not check(wait_until(page, lambda: inputs.count() == len(PASSWORD_FIELDS)),
                 f"[{scenario}] the change-password form rendered {inputs.count()} native fields"):
        shot("04-password")
        return context.close()
    page.wait_for_timeout(args.settle)
    shot("04-password")

    placed = []
    for index, field in enumerate(("current_password", "new_password", "new_password_confirmation")):
        state = inputs.nth(index).evaluate("el => { const box = el.getBoundingClientRect(); const style = getComputedStyle(el);"
                                         " return { x: box.x, y: box.y, width: box.width, height: box.height, visibility: style.visibility }; }")
        placed.append(state["visibility"] == "visible" and state["width"] > 100 and state["height"] > 40 and state["x"] > 0 and state["y"] > 0)
        check(placed[-1], f"[{scenario}] the {field} field is on the page: {state['visibility']}, "
                           f"{state['width']:.0f}×{state['height']:.0f} at {state['x']:.0f},{state['y']:.0f}")
    if not all(placed):
        return context.close()

    for index, value in enumerate(PASSWORD_FIELDS):
        inputs.nth(index).click()
        page.wait_for_timeout(200)
        page.keyboard.type(value, delay=args.type_delay)
    page.wait_for_timeout(500)
    shot("05-filled")
    typed = [inputs.nth(index).input_value() for index in range(len(PASSWORD_FIELDS))]
    check(typed == list(PASSWORD_FIELDS), f"[{scenario}] all three fields took what was typed ({typed})")

    submit = page.locator("button.viptv-cred-submit")
    check(submit.count() == 1 and submit.is_enabled(), f"[{scenario}] Change password is enabled once the three fields are filled")
    submit.click()
    posted = lambda: [e for e in log if e["method"] == "POST" and e["path"] == "/api/auth/password"]
    if not check(wait_until(page, lambda: len(posted()) > 0), f"[{scenario}] Change password posted /api/auth/password"):
        return context.close()
    page.wait_for_timeout(1500)
    shot("06-changed")
    body = posted()[0]["body"]
    check(isinstance(body, dict) and body.get("current_password") == CURRENT_PASSWORD and body.get("new_password") == NEW_PASSWORD,
          f"[{scenario}] the change carried the typed current and new password (fake values; got {body})")
    check(len(posted()) == 1, f"[{scenario}] the change was posted once ({len(posted())})")
    check(page.get_by_text("Password changed").count() > 0, f"[{scenario}] Settings confirms the change")
    check(not errors, f"[{scenario}] no page errors {errors}")
    print(f"     [{scenario}] requests: " + json.dumps([f'{e["method"]} {e["path"]}' for e in log]))
    context.close()


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


def visit_device(browser, origin, scenario, args, log, check):
    context = browser.new_context(ignore_https_errors=True, viewport={"width": args.width, "height": args.height})
    page = context.new_page()
    page.goto(origin + "/device")
    if scenario == "device-signin":
        form = page.locator("form.viptv-cred-form")
        if not check(wait_until(page, lambda: form.count() == 1), "[device-signin] native credential form is present"):
            return context.close()
        username = form.locator('input[name="username"][autocomplete="username"]')
        password = form.locator('input[name="password"][autocomplete="current-password"]')
        check(username.count() == 1 and password.count() == 1, "[device-signin] stable autofill field names and purposes")
        username.fill(USERNAME)
        password.fill(PASSWORD)
        password.press("Enter")
        check(wait_until(page, lambda: bool(login_then_bootstrap(log)[0])), "[device-signin] Enter posts login")
        logins = login_then_bootstrap(log)[0]
        check(len(logins) == 1 and logins[0]["body"] == {"username": USERNAME, "password": PASSWORD},
              "[device-signin] exactly one login carries filled credentials")
    field = page.locator('input[aria-label="Device code"]')
    if not check(wait_until(page, lambda: field.count() == 1 and field.is_visible()), "[device] native code input is visible"):
        return context.close()
    field.click()
    page.keyboard.type("12345678")
    check(wait_until(page, lambda: field.input_value() == "12345678"), "[device] typing preserves all eight digits")
    field.fill("87-65 43210")
    check(wait_until(page, lambda: field.input_value() == "87654321"), "[device] pasted code is normalized and limited to eight digits")
    field.press("Enter")
    posted = lambda: [e for e in log if e["path"] == "/api/auth/device/lookup" and e["method"] == "POST"]
    check(wait_until(page, lambda: bool(posted())), "[device] Enter looks up the code")
    check(bool(posted()) and posted()[0]["body"] == {"user_code": "87654321"}, "[device] lookup receives the typed code")
    page.screenshot(path=str(args.out / f"{scenario}.png"))
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
    parser.add_argument("--repeat", type=int, default=1, help="Run every selected scenario this many times")
    parser.add_argument("--type-delay", type=int, default=20, help="Milliseconds between typed keys")
    parser.add_argument("--settle", type=int, default=300, help="Milliseconds to wait after focusing a field before typing")
    parser.add_argument("--cpu-throttle", type=int, default=1, help="Chrome DevTools CPU slowdown factor. 6 makes app#105 "
                        "(typed characters arriving out of order) fail on every run; 1 is the real-world default")
    parser.add_argument("--width", type=int, default=1920)
    parser.add_argument("--height", type=int, default=1080)
    args = parser.parse_args()
    if args.cpu_throttle < 1 or args.repeat < 1:
        sys.exit("--cpu-throttle is a CPU slowdown factor of 1 or more; --repeat counts runs from 1")
    dist = args.dist.resolve()
    if not (dist / "index.html").is_file():
        sys.exit(f"No web build at {dist}; run ./gradlew :composeApp:wasmJsBrowserDistribution")
    args.out.mkdir(parents=True, exist_ok=True)
    log: list = []
    keys = Path(tempfile.mkdtemp(prefix="web-signin-smoke-"))
    session = {"profile": None}
    server = fake_backend(dist, log, keys, session)
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
            for run in range(1, args.repeat + 1):
                if args.repeat > 1:
                    print(f"--- run {run}/{args.repeat}")
                for scenario in args.scenario or SCENARIOS:
                    with LOG_LOCK:
                        log.clear()
                    session["profile"] = None
                    session["device_authenticated"] = scenario == "device"
                    if scenario in ("device", "device-signin"):
                        visit_device(browser, origin, scenario, args, log, check)
                    elif scenario in FIELD_SCENARIOS:
                        visit_field(browser, origin, scenario, args, check)
                    elif scenario == "password":
                        visit_password(browser, origin, args, log, check)
                    else:
                        visit(browser, origin, scenario, args, log, check)
            browser.close()
    finally:
        server.shutdown()
        shutil.rmtree(keys, ignore_errors=True)
    print(f"screenshots in {args.out}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
