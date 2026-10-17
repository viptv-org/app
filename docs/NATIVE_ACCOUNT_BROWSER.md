# Native account browser actions — 2026-10-15

Native account/dashboard links previously always threw an unavailable error. Android now starts the system HTTPS handler with `ACTION_VIEW`, `CATEGORY_BROWSABLE` and `FLAG_ACTIVITY_NEW_TASK`, using an application context attached by MainActivity. It does not retain an Activity or depend on package-visibility queries before starting. Desktop uses `Desktop.browse` only when desktop and browse actions are supported. Unsupported/headless hosts and missing handlers retain the UI fallback.

Native URLs must be HTTPS with a real host, no user information and a valid port. Non-web handlers, malformed authorities and credential-bearing addresses are rejected. Authorization query encoding remains intact. URLs and query contents are not logged by these launchers. This does not grant device sessions browser management authority; the external web page still requires its own browser authentication.

TV Account actions now display the validated configured server address in a selectable fallback dialog if launching fails. The existing phone/desktop web actions already show the address. The fallback supports TVs with no browser rather than ending at a generic unavailable message.

Verification:

- `AccountBrowserTest`: two tests cover encoded authorization query preservation, local/private HTTPS and IPv6 addresses, and rejection of non-web schemes, embedded credentials, malformed hosts/ports and control characters.
- `AccountBrowserUiTest`: a rendered TV Account action whose launcher fails shows the configured address and useful instructions, and dismisses correctly. [TV capture](evidence/native-account-browser-2026-10-15/tv-browser-fallback.png).
- Actual Android implicit intent dispatch passed on the dedicated Android TV emulator. A test handler was manually selected only for the reserved `viptv-browser-fixture.invalid` host. It verified the production launcher's action, categories, new-task flag and encoded query bytes, without any network request, real account or external service. [Dispatch log](evidence/native-account-browser-2026-10-15/android-intent.log).

The Android proof used optimized QA APK SHA-256 `8af70f8097fd04b3a5a9a672ee5a4852a1e8cf5ccdadc29075047078531c6ba3`, installed only on the private `viptv-lifecycle-isolated` AVD at emulator-5586. It was a remote-app diagnostic candidate with native-engine rebuilding intentionally skipped; it is not the final native ABI/distribution candidate. No user device or browser was controlled. Actual Windows/macOS/Linux default-browser launching was not exercised; its supported/unsupported paths compile and the URL/fallback behavior is tested.

The same implicit dispatch proof passed again on the final performance diagnostic APK `89c2d2ad86da8e469f54710e359f315f5db8531698ba841af4d9cc08546a6745`: [repeat dispatch receipt](evidence/native-account-browser-2026-10-15/android-intent-final.log). The private emulator was stopped afterward.

API documentation was fetched through Context7 and checked against [Android context launch behavior](https://developer.android.com/reference/android/content/ContextWrapper#startActivity(android.content.Intent,%20android.os.Bundle)), [desktop browser support](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/Desktop.html) and [URI semantics](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html). Test-only App Link selection follows the [platform verification tooling](https://developer.android.com/training/app-links/verify-applinks); the private fixture selection does not assert real domain verification or a bundled browser implementation.
