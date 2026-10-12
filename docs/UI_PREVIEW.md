# UI review entry points

The explicit preview renders production UI components with a fixed dataset and copied artwork from the current ZIP boards. It never authenticates, calls a metadata provider, or writes account/profile data. Preview interactions change the visible scene; they do not prove a backend or playback operation. Each capture identifies whether it uses preview data or a real connection.

Desktop launch: `VIPTV_UI_PREVIEW=1 VIPTV_UI_SCREEN=Home ./gradlew :composeApp:run`. `VIPTV_UI_SCREEN` accepts an existing `Screen` name, including Profiles, Home, Search, Details, Sources, Movies, Series, Library, History, Settings and Player. The default desktop window is 1440×900.

Android QA launch after installing the experimental APK: `adb -s emulator-5580 shell am start -S -n org.viptv.experiment/.MainActivity --ez ui_preview true --es ui_screen Settings`. The phone review uses a disposable emulator; the physical Android device is untouched. The same extras work on the disposable TV emulator (`emulator-5582`); TV mode follows Android's device configuration. Starting without the extras restores the ordinary application entry point.

Browser preview: append `#ui-preview/Home` or another supported screen name to the development app URL. An isolated headless Chromium test rendered eight locally bundled posters with no runtime errors or failed resource requests. The enabled interactive browser-tool inventory is empty; this runtime check uses the installed Playwright test runner.

The fixture is in `preview/PreviewData.kt`; routing is in `preview/UiPreview.kt`. `composeResources/files/ui-preview` contains byte-preserved assets from the supplied D3HomeWin/D3Search/D3Player boards. Ordinary app navigation remains in `App.kt`.

`#playback-qa` is a separate public-MDN-video test entry point. It has no account, provider or history connection. Real UI Play, Pause, seek, mute and speed controls passed in isolated Chromium; the observed video ended the check paused at 2.786 seconds, volume 0 and speed 0.5. Normal web entry still requires its backend.
