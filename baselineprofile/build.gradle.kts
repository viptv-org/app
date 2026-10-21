// Generates the shipped baseline profile by driving real Home navigation.
// The androidx.baselineprofile gradle plugin does not support AGP 9 yet, so
// this is a plain library module whose instrumented test is run manually:
//   ./gradlew :baselineprofile:assembleDebugAndroidTest :androidApp:assembleRelease
//   adb install -t androidApp/build/outputs/apk/release/androidApp-release.apk
//   adb install baselineprofile/build/outputs/apk/androidTest/debug/...apk
//   adb shell am instrument -w -e class org.viptv.baselineprofile.BaselineProfileGenerator#homeNavigation \
//     org.viptv.baselineprofile.test/androidx.test.runner.AndroidJUnitRunner
//   adb pull /sdcard/Android/media/org.viptv.baselineprofile/ ... -> androidApp/src/release/generated/baselineProfiles/
plugins {
    id("com.android.library")
}

android {
    namespace = "org.viptv.baselineprofile"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    androidTestImplementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
    // Java test sources only: the macro benchmark DSL is Kotlin-first, so the
    // stdlib is needed for kotlin.Unit. No Kotlin plugin (AGP 9 built-in Kotlin).
    androidTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
