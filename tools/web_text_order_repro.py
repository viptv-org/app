#!/usr/bin/env python3
"""Check that a Compose web text field keeps typed characters in order under CPU throttling.

This is the app-independent half of app#105: it drives the standalone Compose-only
reproduction in `docs/upstream/compose-web-text-order` (one `BasicTextField`, no VIPTV
code), so a failure here is a Compose Multiplatform defect rather than app behaviour.
README there covers why the reproduction exists and what it shows; the diagnosis is in
viptv-org/app#105.

The page publishes Compose's own field value to `window.__typed`; the check compares
that with the string it typed, and prints the DOM backing field as a cross-check.

Usage (build the reproduction first, see the README):

    python3 tools/web_text_order_repro.py --runs 3           # red: Compose scrambles keys
    python3 tools/web_text_order_repro.py --cpu-throttle 1  # green control run

Exits 0 when every run kept the typed order and 1 when a run reordered or lost
characters. No real account or server is involved.
"""
import argparse
import shutil
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from web_signin_smoke import fake_backend, launch  # noqa: E402  (shared fake HTTPS server + browser picker)
from playwright.sync_api import sync_playwright  # noqa: E402

REPRO = Path("docs/upstream/compose-web-text-order/build/dist/wasmJs/productionExecutable")
TEXT = "smoke-visitor-compose-web"

BACKING_FIELD_TEXT = """() => {
  const roots = [document];
  const found = [];
  while (roots.length) {
    const root = roots.pop();
    root.querySelectorAll('.compose-backing-field').forEach(el => found.push(el.textContent));
    root.querySelectorAll('*').forEach(el => { if (el.shadowRoot) roots.push(el.shadowRoot); });
  }
  return found;
}"""


def run(page, origin, args):
    page.goto(origin + "/")
    for _ in range(120):
        if page.get_by_role("textbox").count() > 0:
            break
        page.wait_for_timeout(250)
    page.wait_for_timeout(1000)
    field = page.get_by_role("textbox").first
    box = field.bounding_box()
    if box is None:
        sys.exit("The reproduction page never showed its text field; check --dist")
    page.mouse.click(box["x"] + box["width"] / 3, box["y"] + box["height"] / 2)
    page.wait_for_timeout(args.settle)
    page.keyboard.type(args.text, delay=args.type_delay)
    page.wait_for_timeout(800)
    value = page.evaluate("() => window.__typed")
    backing = page.evaluate(BACKING_FIELD_TEXT)
    return value, backing


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dist", type=Path, default=REPRO)
    parser.add_argument("--runs", type=int, default=1)
    parser.add_argument("--text", default=TEXT, help=f"String to type (default {TEXT!r})")
    parser.add_argument("--cpu-throttle", type=int, default=60, help="Chrome DevTools CPU slowdown factor; "
                        "60 makes this fail on every run, 1 is the control")
    parser.add_argument("--type-delay", type=int, default=20, help="Milliseconds between typed keys")
    parser.add_argument("--settle", type=int, default=300, help="Milliseconds to wait after clicking the field")
    parser.add_argument("--headed", action="store_true")
    parser.add_argument("--browser-arg", action="append", default=[], help="Extra Chromium flag, repeatable")
    parser.add_argument("--chromium", help="Chromium executable; default: Playwright's own, else the newest cached build")
    args = parser.parse_args()
    dist = args.dist.resolve()
    if args.cpu_throttle < 1:
        sys.exit("--cpu-throttle is a CPU slowdown factor of 1 or more")
    if not (dist / "index.html").is_file():
        sys.exit(f"No reproduction build at {dist}; see docs/upstream/compose-web-text-order/README.md")
    log: list = []
    keys = Path(tempfile.mkdtemp(prefix="web-text-order-"))
    server = fake_backend(dist, log, keys)
    origin = f"https://127.0.0.1:{server.server_port}"
    failures = 0
    try:
        with sync_playwright() as p:
            browser = launch(p, args)
            for run_number in range(1, args.runs + 1):
                context = browser.new_context(ignore_https_errors=True, viewport={"width": 1280, "height": 720})
                page = context.new_page()
                errors = []
                page.on("pageerror", lambda e: errors.append(str(e)))
                if args.cpu_throttle != 1:
                    context.new_cdp_session(page).send("Emulation.setCPUThrottlingRate", {"rate": args.cpu_throttle})
                value, backing = run(page, origin, args)
                ok = value == args.text
                failures += 0 if ok else 1
                print(f"{'PASS' if ok else 'FAIL'} run {run_number}/{args.runs} cpu x{args.cpu_throttle} "
                      f"key delay {args.type_delay}ms: typed {args.text!r}, Compose field {value!r}"
                      + ("" if ok else f", DOM backing field {backing!r}")
                      + (f", page errors {errors}" if errors else ""))
                context.close()
            browser.close()
    finally:
        server.shutdown()
        shutil.rmtree(keys, ignore_errors=True)
    print(f"{args.runs - failures}/{args.runs} runs kept the typed order")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()