plugins { id("com.android.application") version "9.1.0" }
android {
    namespace = "org.viptv.subtitleprobe"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.viptv.subtitleprobe"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1"
        ndk { abiFilters += "x86_64" }
    }
    sourceSets["main"].kotlin.srcDir("build/generated/probeSource")
    sourceSets["main"].jniLibs.srcDir("build/generated/nativeCore")
    sourceSets["main"].assets.srcDir("build/generated/probeAssets")
}
val stagePcmProbe by tasks.registering(Copy::class) {
    listOf("Media3PcmProbe.kt", "Media3SubtitleRenderersFactory.kt", "AndroidSubtitleSyncWorker.kt").forEach {
        from("../../composeApp/src/androidMain/kotlin/org/viptv/app/player/$it")
    }
    listOf("SubtitleSync.kt", "SubtitleSyncProtocol.kt", "SidecarCueTimeline.kt", "ProviderTextCues.kt").forEach {
        from("../../composeApp/src/commonMain/kotlin/org/viptv/app/player/$it")
    }
    from("../../native/core/generated/native-kotlin/uniffi/viptv_core/viptv_core.kt")
    into(layout.buildDirectory.dir("generated/probeSource"))
}
val stageNativeCore by tasks.registering(Copy::class) {
    providers.gradleProperty("viptv.subtitleCoreSo").orNull?.let { from(it) }
    into(layout.buildDirectory.dir("generated/nativeCore/x86_64"))
}
val stageNativeNotices by tasks.registering(Copy::class) {
    from("../../native/core/THIRD_PARTY_LICENSES/webrtc-vad.txt") { rename { "webrtc-vad.LICENSE" } }
    from("../../native/core/crates/viptv-vad/vendor/source-manifest.json") { rename { "webrtc-vad-source.json" } }
    from("../../native/core/crates/viptv-vad/vendor/patches/48k-input-offset.patch") { rename { "webrtc-vad-48k.patch" } }
    into(layout.buildDirectory.dir("generated/probeAssets/licenses"))
}
tasks.named("preBuild") { dependsOn(stagePcmProbe, stageNativeCore, stageNativeNotices) }
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("net.java.dev.jna:jna:5.17.0@aar")
}
