plugins { id("com.android.application") version "9.1.0" }
android {
    namespace = "org.viptv.subtitleprobe"
    compileSdk = 37
    defaultConfig { applicationId = "org.viptv.subtitleprobe"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1" }
    sourceSets["main"].kotlin.srcDir("build/generated/probeSource")
}
val stagePcmProbe by tasks.registering(Copy::class) {
    from("../../composeApp/src/androidMain/kotlin/org/viptv/app/player/Media3PcmProbe.kt")
    into(layout.buildDirectory.dir("generated/probeSource"))
}
tasks.named("preBuild") { dependsOn(stagePcmProbe) }
dependencies { implementation("androidx.media3:media3-exoplayer:1.11.1") }
