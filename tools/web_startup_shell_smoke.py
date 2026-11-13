#!/usr/bin/env python3
"""Exercise the source HTML startup lifecycle over HTTPS without building Wasm."""
import argparse
import tempfile
from pathlib import Path

from playwright.sync_api import expect, sync_playwright
from web_webgl2_fallback_smoke import static_server


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--resources', type=Path, default=Path('composeApp/src/wasmJsMain/resources'))
    parser.add_argument('--chromium', default='/usr/bin/chromium')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory() as temporary, sync_playwright() as playwright:
        server = static_server(args.resources.resolve(), Path(temporary))
        browser = playwright.chromium.launch(executable_path=args.chromium, headless=True)
        try:
            origin = f'https://127.0.0.1:{server.server_port}'
            for outcome in ('ready', 'script-error', 'initialization-error', 'no-webgl2'):
                context = browser.new_context(ignore_https_errors=True)
                page = context.new_page()
                if outcome == 'no-webgl2':
                    page.add_init_script("HTMLCanvasElement.prototype.getContext = function () { return null; }")
                pending = []
                page.route('**/composeApp.js', lambda route: pending.append(route))
                try:
                    page.goto(origin, wait_until='commit')
                    if outcome == 'no-webgl2':
                        expect(page.locator('html')).to_have_class('no-webgl2')
                        expect(page.locator('#viptv-startup')).to_be_hidden()
                        pending.pop().fulfill(status=200, content_type='text/javascript', body=
                            "document.getElementById('viptv-ui-layer').textContent = 'This browser requires WebGL2';")
                        expect(page.locator('#viptv-ui-layer')).to_contain_text('requires WebGL2')
                        assert page.locator('#viptv-ui-layer').evaluate("e => getComputedStyle(e).color") == 'rgb(218, 216, 211)'
                        print('PASS no-webgl2')
                        continue
                    startup = page.get_by_role('status', name='Starting VIPTV')
                    expect(startup).to_be_visible()
                    expect(startup).to_contain_text('Getting VIPTV ready…')
                    page.evaluate("document.querySelector('#viptv-ui-layer').appendChild(document.createElement('canvas'))")
                    expect(startup).to_be_visible()
                    route = pending.pop()
                    if outcome == 'script-error':
                        route.abort()
                    else:
                        body = 'Promise.reject(new Error("fixture initialization failed"))' if outcome == 'initialization-error' else ''
                        route.fulfill(status=200, content_type='text/javascript', body=body)
                    if outcome == 'ready':
                        expect(startup).to_be_visible()
                        page.evaluate("window.dispatchEvent(new Event('viptv-first-frame'))")
                        expect(page.locator('#viptv-startup')).to_have_count(0)
                        page.evaluate('Promise.reject(new Error("fixture after first frame")); void 0')
                        expect(page.locator('#viptv-startup')).to_have_count(0)
                    else:
                        expect(page.get_by_role('alert')).to_contain_text('VIPTV could not start.')
                        retry = page.get_by_role('button', name='Try again')
                        expect(retry).to_be_visible()
                        with page.expect_navigation(wait_until='commit'):
                            retry.click()
                        expect(page.get_by_role('status', name='Starting VIPTV')).to_be_visible()
                    print(f'PASS {outcome}')
                finally:
                    for route in pending:
                        route.abort()
                    context.close()
        finally:
            browser.close()
            server.shutdown()


if __name__ == '__main__':
    main()
