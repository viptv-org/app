import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android { namespace = "org.viptv.app.shared"; compileSdk = 37; minSdk = 26; androidResources.enable = true }
    jvm("desktop")
    wasmJs { browser(); binaries.executable() }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework { baseName = "VIPTVShared"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation("com.composables:icons-lucide-cmp:2.2.1")
            implementation("com.composables:composeunstyled-toggle-switch:2.10.0")
            implementation("io.github.alexzhirkevich:qrose:1.3.1")
            implementation("dev.chrisbanes.haze:haze-blur:2.0.1")
            implementation(compose.components.resources)
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.ktor:ktor-client-core:3.4.0")
            implementation("io.coil-kt.coil3:coil-compose:3.6.3")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.6.3")
        }
        val nativePlayerMain by creating {
            dependsOn(commonMain.get())
            kotlin.srcDir(rootProject.file("native/core/generated/native-kotlin"))
            dependencies {
                implementation("io.ktor:ktor-client-core:3.4.0")
                implementation("io.ktor:ktor-client-okhttp:3.4.0")
            }
        }
        androidMain {
            dependsOn(nativePlayerMain)
            dependencies {
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
                implementation("net.java.dev.jna:jna:5.17.0@aar")
                implementation(project(":media3-ffmpeg"))
                listOf("media3-exoplayer", "media3-exoplayer-hls", "media3-exoplayer-dash", "media3-ui").forEach { implementation("androidx.media3:$it:1.11.1") }
            }
        }
        val desktopMain by getting {
            dependsOn(nativePlayerMain)
            resources.srcDir(layout.buildDirectory.dir("nativeResources"))
            dependencies {
                implementation("io.github.yuroyami:kiteplayer-compose:0.3.0")
                implementation("io.github.yuroyami:kiteplayer-core:0.3.0-viptv-presentation.4") { version { strictly("0.3.0-viptv-presentation.4") } }
                implementation("io.github.yuroyami:kiteplayer-output:0.3.0-viptv-presentation.4") { version { strictly("0.3.0-viptv-presentation.4") } }
                implementation("io.github.yuroyami:kiteplayer-compose-ui:0.3.0-viptv-presentation.4") {
                    version { strictly("0.3.0-viptv-presentation.4") }
                }
                implementation("io.github.yuroyami:kiteplayer-compose-video:0.3.0-viptv-presentation.4") {
                    version { strictly("0.3.0-viptv-presentation.4") }
                }
                implementation(compose.desktop.currentOs); implementation("net.java.dev.jna:jna:5.17.0")
            }
        }
        // The manual nativePlayerMain dependsOn edges disable the default
        // hierarchy template, so the iOS source set must be wired explicitly.
        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.4.0")
            }
        }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        wasmJsMain.dependencies {
            implementation(npm("hls.js", "1.7.3"))
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
            implementation("io.ktor:ktor-client-core:3.4.0")
            implementation("io.ktor:ktor-client-js:3.4.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.4.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}

val prepareKitePlayer by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "tools/build_player.py")
    inputs.files(rootProject.fileTree("native/kiteplayer"), rootProject.file("tools/build_player.py"))
    outputs.dir(rootProject.layout.buildDirectory.dir("kiteplayer/maven"))
}
tasks.matching {
    it.name.startsWith("compile") && it.name.contains("Desktop")
}.configureEach { dependsOn(prepareKitePlayer) }

val buildDesktopEngine by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "tools/build_engine.py", "desktop")
    inputs.files(rootProject.fileTree("native/core/crates") { include("**/*.rs", "**/Cargo.toml") }, rootProject.file("native/core/Cargo.lock"), rootProject.file("tools/build_engine.py"))
    outputs.dir(layout.buildDirectory.dir("nativeResources"))
}
tasks.matching { it.name == "desktopProcessResources" }.configureEach { dependsOn(buildDesktopEngine) }
// Same Java version can have different native dependencies across JDK vendors.
// Compose's default runtime cache metadata does not distinguish those builds.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    inputs.property("viptvPackagingJdkHome", javaHome)
    inputs.file(javaHome.map { file("$it/release") }).withPathSensitivity(PathSensitivity.NONE)
}

compose.resources { publicResClass = true; packageOfResClass = "org.viptv.app.resources" }
compose.desktop {
    application {
        mainClass = "org.viptv.app.MainKt"
        nativeDistributions { targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb); packageName = "VIPTV-Experiment"; packageVersion = "0.1.0"; macOS { packageVersion = "1.0.0" } }
    }
}
