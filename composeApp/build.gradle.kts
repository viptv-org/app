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
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
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
                implementation("io.github.yuroyami:kiteplayer-compose:0.3.0")
                implementation("io.ktor:ktor-client-core:3.4.0")
                implementation("io.ktor:ktor-client-okhttp:3.4.0")
            }
        }
        androidMain {
            dependsOn(nativePlayerMain)
            dependencies { implementation("net.java.dev.jna:jna:5.17.0@aar") }
        }
        val desktopMain by getting {
            dependsOn(nativePlayerMain)
            resources.srcDir(layout.buildDirectory.dir("nativeResources"))
            dependencies { implementation(compose.desktop.currentOs); implementation("net.java.dev.jna:jna:5.17.0") }
        }
        wasmJsMain.dependencies {
            implementation(npm("hls.js", "1.7.3"))
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
            implementation("io.ktor:ktor-client-core:3.4.0")
            implementation("io.ktor:ktor-client-js:3.4.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.4.0")
        }
    }
}

val buildDesktopEngine by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "tools/build_engine.py", "desktop")
    inputs.files(rootProject.fileTree("native/core/crates") { include("**/*.rs", "**/Cargo.toml") }, rootProject.file("native/core/Cargo.lock"))
    outputs.dir(layout.buildDirectory.dir("nativeResources"))
}
tasks.matching { it.name == "desktopProcessResources" }.configureEach { dependsOn(buildDesktopEngine) }
compose.resources { publicResClass = true; packageOfResClass = "org.viptv.app.resources" }
compose.desktop {
    application {
        mainClass = "org.viptv.app.MainKt"
        nativeDistributions { targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb); packageName = "VIPTV-Experiment"; packageVersion = "0.1.0" }
    }
}
