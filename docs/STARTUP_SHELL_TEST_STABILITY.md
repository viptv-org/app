# Startup-shell scene test stability — 2026-10-15

The full desktop gate observed a `LayoutNode … not found in RectList` exception while closing the second offscreen scene, after startup assertions had passed. The unchanged isolated test then passed. Pinned Compose 1.13.0-alpha02 source shows ImageComposeScene defaults effects/recomposition to Dispatchers.Unconfined and disposes its composition before closing the frame recomposer.

The test now gives both scenes and the controller one explicit StandardTestDispatcher/TestCoroutineScheduler, pumps that scheduler around manually rendered frames, and guarantees controller/image-loader/singleton cleanup in nested finally blocks even if a scene throws. It does not suppress disposal errors, remove assertions, change production rendering, or replace actual App recreation with a stub. The complete two-scene scenario runs five times in the same test to exercise repeated initialization/disposal.

Current Context7 Compose and kotlinx.coroutines docs were fetched; the pinned sources and coroutine scheduler contract were checked. After the test-only change, the targeted test passed and the full **229-test desktop suite passed with zero failures/skips** in 26 seconds. Receipts: [targeted](evidence/startup-shell-test-2026-10-15/targeted.log), [full suite](evidence/startup-shell-test-2026-10-15/full-desktop.log), [test XML](evidence/startup-shell-test-2026-10-15/TEST-org.viptv.app.StartupShellUiTest.xml). This establishes the observed test stabilization; it does not claim a production Compose-library defect was fixed.


The final shared desktop phase, including the profile administration additions, then passed **236 tests with zero failures, errors or skips**. The startup test still executes all five complete recreation/cleanup cycles. [Final suite summary](evidence/profile-administration-2026-10-15/full-desktop-summary.json). The test-only source remains frozen for the separately recorded production Wasm/release build.

## 2026-10-27 update: the cause was a thread race (app#27)

The explicit scheduler above did not stop the flake. Compose's `RectManager` posts its layout callbacks to `MainUIDispatcher`, the Swing event thread, 16 ms after a layout change. A scene driven from the JUnit worker thread races that callback, which corrupts the scene's `RectList`. The exception either fails the UI test (app#16) or escapes through the Recomposer's job into kotlinx-coroutines-test, failing the next `runTest` with `UncaughtExceptionsBeforeTest` (app#27). Every desktop scene test now runs on the event thread through `UiThreadRule`, and `UiThreadConfinementTest` covers both the race and the convention.
