plugins { id("com.android.application") version "9.1.0" }
android {
    namespace = "org.viptv.subtitleprobe"
    compileSdk = 37
    defaultConfig { applicationId = "org.viptv.subtitleprobe"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1" }
    sourceSets["main"].java.apply {
        srcDir("../../composeApp/src/androidMain/kotlin")
        include("org/viptv/app/player/Media3PcmProbe.kt", "org/viptv/app/player/SubtitlePcmProbeActivity.kt")
    }
}
dependencies { implementation("androidx.media3:media3-exoplayer:1.11.1") }
