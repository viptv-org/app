#!/usr/bin/env python3
"""Browser smoke check: a visitor without WebGL2 can read the page's fallback notice (app#104).

Serves a built web distribution over HTTPS, opens `/` in Chromium with WebGL disabled, and
checks that

1. the browser really reports no WebGL2 context (otherwise the run proves nothing),
2. Compose stopped on its own WebGL2 check and wrote its notice into `#viptv-ui-layer`,
3. the notice's computed text colour reaches WCAG AA contrast against the page background,
4. the rendered screenshot is not the solid black page the bug reported: light text pixels
   are visible, and every word of the notice sits inside the viewport,
5. with WebGL2 available the page shell is unchanged (the fallback styling is scoped to the
   no-WebGL2 case, not applied to the working app).

No account, backend or real server is involved. The web app accepts only an HTTPS origin, so
the static server uses a throwaway self-signed certificate (needs `openssl`) that the browser
is told to accept.

Usage (after `./gradlew :composeApp:wasmJsBrowserDistribution`):

    python3 tools/web_webgl2_fallback_smoke.py \
        --dist composeApp/build/dist/wasmJs/productionExecutable \
        --out /tmp/web-webgl2-fallback

Exits 0 when every check passes and writes a screenshot to --out.
"""
import argparse
import mimetypes
import re
import shutil
import ssl
import subprocess
import sys
import tempfile
import threading
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from playwright.sync_api import sync_playwright
from PIL import Image

# Both flags together: the browser reports neither webgl nor webgl2, which is what a blocked
# GPU or disabled hardware acceleration looks like to the app.
NO_WEBGL2_ARGS = ["--disable-webgl", "--disable-webgl2"]
NOTICE = "requires WebGL2"
NOTICE_IN_DOM = """() => {
  const layer = document.querySelector('#viptv-ui-layer');
  return layer ? layer.textContent.trim() : '';
}"""
# The notice's computed text colour, the page background behind it, and the layer's layout.
NOTICE_COLOURS = """() => {
  const layer = document.querySelector('#viptv-ui-layer');
  const body = document.body;
  return {
    text: getComputedStyle(layer).color,
    background: getComputedStyle(body).backgroundColor,
    display: getComputedStyle(layer).display,
  };
}"""
# Whether any word of the notice is clipped or pushed outside the viewport.
NOTICE_IN_VIEWPORT = """() => {
  const layer = document.querySelector('#viptv-ui-layer');
  const range = document.createRange();
  range.selectNodeContents(layer);
  return [...range.getClientRects()].every(r =>
    r.left >= 0 && r.top >= 0 &&
    r.right <= window.innerWidth && r.bottom <= window.innerHeight);
}"""


def launch(p, args, flags):
    """Playwright's own Chromium, else the newest numbered build in its cache (the Python package may pin an absent one)."""
    options = {"headless": not args.headed, "args": flags + args.browser_arg}
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


def static_server(dist: Path, keys: Path):

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            path = self.path.split("?", 1)[0]
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

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1", "-subj", "/CN=127.0.0.1",
                    "-keyout", str(keys / "key.pem"), "-out", str(keys / "cert.pem")], check=True, capture_output=True)
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.load_cert_chain(keys / "cert.pem", keys / "key.pem")
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def channel(value):
    """Relative luminance of an 'rgb(r, g, b)' computed colour."""
    parts = [int(v) / 255 for v in re.findall(r"[\d.]+", value)[:3]]
    linear = [p / 12.92 if p <= 0.04045 else ((p + 0.055) / 1.055) ** 2.4 for p in parts]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast(foreground, background):
    a, b = sorted((channel(foreground), channel(background)), reverse=True)
    return (a + 0.05) / (b + 0.05)


def light_text_pixels(shot: Path):
    """Pixels clearly lighter than the page background, i.e. the notice's glyphs in the screenshot."""
    image = Image.open(shot).convert("RGB")
    background = Counter(image.getdata()).most_common(1)[0][0]  # the flat page colour fills most of the frame
    pixels = image.getdata()
    light = [p for p in pixels if sum(p) / 3 > sum(background) / 3 + 40]
    return len(light) / (image.width * image.height), background


def visit_fallback(browser, origin, args, check):
    context = browser.new_context(ignore_https_errors=True, viewport={"width": args.width, "height": args.height})
    page = context.new_page()
    page.goto(origin + "/")
    try:
        page.wait_for_function(f"() => ({NOTICE_IN_DOM})().includes({NOTICE!r})", timeout=60000)
    except Exception as error:
        check(False, f"the WebGL2 notice reached #viptv-ui-layer ({error})")
    page.wait_for_timeout(500)
    shot = args.out / "no-webgl2.png"
    page.screenshot(path=str(shot))

    webgl2 = page.evaluate("() => !!document.createElement('canvas').getContext('webgl2')")
    check(webgl2 is False, f"the browser really has no WebGL2 context ({webgl2})")
    notice = page.evaluate(NOTICE_IN_DOM)
    check(NOTICE in notice, f"Compose wrote its notice into #viptv-ui-layer ({notice!r})")
    colours = page.evaluate(NOTICE_COLOURS)
    ratio = contrast(colours["text"], colours["background"])
    check(ratio >= 4.5, f"the notice text meets WCAG AA contrast on the page background "
                        f"({colours['text']} on {colours['background']} = {ratio:.2f}:1, needs 4.5:1)")
    share, background = light_text_pixels(shot)
    check(share > 0.0005, f"light text is visible in the screenshot ({share * 100:.3f}% of pixels lighter than "
                          f"rgb{background}; a solid black page has 0)")
    check(page.evaluate(NOTICE_IN_VIEWPORT), "every word of the notice is inside the viewport")
    context.close()


def visit_webgl2(browser, origin, args, check):
    """A browser with WebGL2 keeps the plain page shell; the fallback styling must stay scoped to the broken case."""
    context = browser.new_context(ignore_https_errors=True, viewport={"width": args.width, "height": args.height})
    page = context.new_page()
    page.goto(origin + "/")
    page.wait_for_function("() => !!document.createElement('canvas').getContext('webgl2')", timeout=60000)
    page.wait_for_timeout(1500)
    scoped = page.evaluate("() => document.documentElement.classList.contains('no-webgl2')")
    check(scoped is False, "a WebGL2 browser is not marked as the fallback")
    colours = page.evaluate(NOTICE_COLOURS)
    check(colours["display"] == "block", f"#viptv-ui-layer keeps its plain page-shell layout ({colours['display']})")
    context.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dist", type=Path, default=Path("composeApp/build/dist/wasmJs/productionExecutable"))
    parser.add_argument("--out", type=Path, default=Path("/tmp/web-webgl2-fallback"))
    parser.add_argument("--headed", action="store_true")
    parser.add_argument("--browser-arg", action="append", default=[], help="Extra Chromium flag, repeatable")
    parser.add_argument("--chromium", help="Chromium executable; default: Playwright's own, else the newest in ~/.cache/ms-playwright")
    parser.add_argument("--width", type=int, default=1280)
    parser.add_argument("--height", type=int, default=720)
    args = parser.parse_args()
    dist = args.dist.resolve()
    if not (dist / "index.html").is_file():
        sys.exit(f"No web build at {dist}; run ./gradlew :composeApp:wasmJsBrowserDistribution")
    args.out.mkdir(parents=True, exist_ok=True)
    keys = Path(tempfile.mkdtemp(prefix="web-webgl2-fallback-"))
    server = static_server(dist, keys)
    origin = f"https://127.0.0.1:{server.server_port}"
    failures = []

    def check(ok, message):
        print(("PASS " if ok else "FAIL ") + message)
        if not ok:
            failures.append(message)
        return ok

    try:
        with sync_playwright() as p:
            browser = launch(p, args, NO_WEBGL2_ARGS)
            visit_fallback(browser, origin, args, check)
            browser.close()
            browser = launch(p, args, [])
            visit_webgl2(browser, origin, args, check)
            browser.close()
    finally:
        server.shutdown()
        shutil.rmtree(keys, ignore_errors=True)
    print(f"screenshot in {args.out}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()