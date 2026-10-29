plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Shipped ABIs: each has the Rust engine, the FFmpeg audio decoder and the torrent runtime.
val shippedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")
// -Pviptv.abis=x86_64 (comma list) builds and packages only those ABIs, for fast emulator loops.
// Without it every shipped ABI is built. Builds that are distributed must not pass it.
val selectedAbis: List<String> = providers.gradleProperty("viptv.abis").orNull?.let { value ->
    val requested = value.split(",").map(String::trim).filter(String::isNotEmpty)
    val unknown = requested - shippedAbis
    if (requested.isEmpty() || unknown.isNotEmpty()) {
        throw GradleException("viptv.abis: unknown ABI ${unknown.joinToString().ifEmpty { "(empty)" }}; supported: ${shippedAbis.joinToString()}")
    }
    shippedAbis.filter { it in requested }
} ?: shippedAbis
// Every build gets its own version from git (docs/APP_UPDATES.md): versionCode is a base plus the
// commit count, so a later commit on main always has a higher one, and the same commit always
// builds the same version. Without git (a source archive) the build is 0.1.0+unknown.
val versionCodeBase = 1000
fun gitOutput(vararg args: String): String? = try {
    providers.exec { commandLine("git", *args); workingDir = rootDir; isIgnoreExitValue = true }
        .standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
} catch (_: Exception) { null }
val commitCount = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull()
val commitSha = gitOutput("rev-parse", "--short=7", "HEAD")
val appVersionCode = versionCodeBase + (commitCount ?: 0)
val appVersionName = if (commitCount != null && commitSha != null) "0.1.$commitCount+$commitSha" else "0.1.0+unknown"
android {
    namespace = "org.viptv.experiment"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.viptv.experiment"; minSdk = 26; targetSdk = 36; versionCode = appVersionCode; versionName = appVersionName
        // Package only ABIs with both the Rust engine and FFmpeg audio decoder.
        ndk { abiFilters += selectedAbis }
        // Optional prefilled sign-in server for dev builds: -Pviptv.defaultServer=https://host
        val defaultServer = providers.gradleProperty("viptv.defaultServer").orNull.orEmpty()
        require(defaultServer.isEmpty() || Regex("https://[A-Za-z0-9.-]+(:[0-9]+)?").matches(defaultServer)) { "viptv.defaultServer must be an https origin" }
        buildConfigField("String", "DEFAULT_SERVER", "\"$defaultServer\"")
        // Updates come from <signed-in server>/updates/android/update.json. -Pviptv.updateFeed=<url>
        // replaces that address (a future GitHub Releases feed, or a loopback test feed).
        val updateFeed = providers.gradleProperty("viptv.updateFeed").orNull.orEmpty()
        require(updateFeed.isEmpty() || Regex("(https://|http://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/)[^\"\\\\\\s]*").matches(updateFeed)) {
            "viptv.updateFeed must be an https URL (plain http only on 127.0.0.1 or localhost)" }
        buildConfigField("String", "UPDATE_FEED", "\"$updateFeed\"")
        buildConfigField("boolean", "APP_UPDATES", "true")
        // App Links (the sign-in QR opens the app): the default server's host. Android cannot verify a
        // port, so those servers, and builds without one, get a host that matches nothing real.
        manifestPlaceholders["viptvLinkHost"] = Regex("https://([A-Za-z0-9.-]+)").matchEntire(defaultServer)?.groupValues?.get(1) ?: "viptv-links.invalid"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        getByName("debug") {
            if (providers.gradleProperty("viptv.torrentQa").orNull == "true") {
                applicationIdSuffix = ".torrentqa"
                // The feed's APK is org.viptv.experiment; this package cannot install it over itself.
                buildConfigField("boolean", "APP_UPDATES", "false")
            }
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("qa") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }
    sourceSets["qa"].apply {
        kotlin.srcDir("src/debug/kotlin")
        res.srcDir("src/debug/res")
        manifest.srcFile("src/debug/AndroidManifest.xml")
    }
    if (providers.gradleProperty("viptv.torrentQa").orNull == "true") {
        sourceSets["debug"].kotlin.srcDir("src/torrentQa/kotlin")
        sourceSets["debug"].res.srcDir("src/torrentQa/res")
        sourceSets["debug"].manifest.srcFile("src/torrentQa/AndroidManifest.xml")
    }
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/rustJni").get().asFile)
    // Shared torrent runtime (TORRENT_RUNTIME_REF). Without it the app never advertises native torrents.
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/torrentRuntime/jniLibs").get().asFile)
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/torrentRuntime/assets").get().asFile)
}
dependencies {
    // Diagnostic semantics inspection must compile against the shared app's UI
    // version, rather than activity-compose's minimum Compose 1.0 API surface.
    "qaImplementation"("org.jetbrains.compose.ui:ui:1.13.0-alpha02")
    "debugImplementation"("org.jetbrains.compose.ui:ui:1.13.0-alpha02")
    listOf("media3-exoplayer", "media3-exoplayer-hls", "media3-ui").forEach { module ->
        "qaImplementation"("androidx.media3:$module:1.11.1")
        "debugImplementation"("androidx.media3:$module:1.11.1")
    }
    implementation(project(":composeApp"))
    // The release APK ships a generated baseline profile from
    // src/release/generated/baselineProfiles (see the :baselineprofile module):
    // the ONN's JIT was the single biggest source of missed vsyncs
    // (8-9.6 % -> 0.3-0.4 % with full AOT in measurement).
    implementation("androidx.activity:activity-compose:1.10.1")
    "qaImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    "debugImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

val buildAndroidEngine by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "tools/build_engine.py", "android", "--abis", selectedAbis.joinToString(","))
    inputs.property("abis", selectedAbis)
    inputs.files(rootProject.fileTree("native/core/crates") { include("**/*.rs", "**/Cargo.toml") }, rootProject.file("native/core/Cargo.lock"), rootProject.file("tools/build_engine.py"))
    outputs.dir(layout.buildDirectory.dir("generated/rustJni"))
}
tasks.named("preBuild") { dependsOn(buildAndroidEngine) }

// -Pviptv.torrentRuntime=false builds without it (no Go/playback-gateway); torrents then need a gateway.
val torrentRuntime = providers.gradleProperty("viptv.torrentRuntime").orNull != "false"
val buildTorrentRuntime by tasks.registering(Exec::class) {
    onlyIf { torrentRuntime }
    workingDir(rootProject.projectDir)
    commandLine("python3", "tools/build_torrent_runtime.py", "--abis", selectedAbis.joinToString(","))
    inputs.property("abis", selectedAbis)
    inputs.files(rootProject.file("TORRENT_RUNTIME_REF"), rootProject.file("tools/build_torrent_runtime.py"), file("src/main/java/org/playbackgateway/runtime/NativeRuntime.java"))
    outputs.dir(layout.buildDirectory.dir("generated/torrentRuntime"))
}
tasks.named("preBuild") { dependsOn(buildTorrentRuntime) }

// Guard: the debug APK packages exactly the selected ABIs (all shipped ABIs without viptv.abis).
val checkDebugApkAbis by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks that the debug APK packages exactly the ABIs selected by viptv.abis."
    dependsOn("assembleDebug")
    workingDir(rootProject.projectDir)
    val apk = layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk")
    commandLine("python3", "tools/check_apk_abis.py", apk.get().asFile.path, selectedAbis.joinToString(","))
}
