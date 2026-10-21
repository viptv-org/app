package org.viptv.baselineprofile;

import android.content.Intent;
import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import kotlin.Unit;
import org.junit.Rule;
import org.junit.Test;

/**
 * Collects the hot paths of Home: hero composition, shelf paging (right/left)
 * and row crossings (down/up). The deterministic preview session (packaged
 * artwork, fixed cards) needs no account, so this runs on any emulator:
 *   ./gradlew :baselineprofile:assembleDebugAndroidTest :androidApp:assembleRelease
 *   adb install -t androidApp/build/outputs/apk/release/androidApp-release.apk
 *   adb install baselineprofile/build/outputs/apk/androidTest/debug/baselineprofile-debug-androidTest.apk
 *   adb shell am instrument -w -e class org.viptv.baselineprofile.BaselineProfileGenerator#homeNavigation \
 *     org.viptv.baselineprofile.test/androidx.test.runner.AndroidJUnitRunner
 * then pull the printed profile path into androidApp/src/release/generated/baselineProfiles/.
 */
public class BaselineProfileGenerator {
    @Rule
    public BaselineProfileRule rule = new BaselineProfileRule();

    @Test
    public void homeNavigation() {
        rule.collect(
            "org.viptv.experiment",
            /* maxIterations = */ 15,
            /* stableMinIterations = */ 10,
            /* collectWithInstrumentation = */ null,
            /* measureBlock = */ scope -> {
                scope.startActivityAndWait(new Intent()
                    .setComponent(new android.content.ComponentName("org.viptv.experiment", "org.viptv.experiment.MainActivity"))
                    .putExtra("ui_preview", true)
                    .putExtra("ui_screen", "Home"));
                UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
                sleep(4_000);
                // From the hero into the first shelf, then page across it.
                device.pressDPadDown(); sleep(700);
                for (int i = 0; i < 6; i++) { device.pressDPadRight(); sleep(150); }
                for (int i = 0; i < 6; i++) { device.pressDPadLeft(); sleep(150); }
                // Row crossings and the billboard return — the heaviest path.
                for (int i = 0; i < 3; i++) {
                    device.pressDPadDown(); sleep(550);
                    device.pressDPadDown(); sleep(550);
                    device.pressDPadUp(); sleep(550);
                }
                device.pressDPadUp(); sleep(600);
                return Unit.INSTANCE;
            });
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
