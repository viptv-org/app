plugins {
    kotlin("multiplatform") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.13.0-alpha02"
}

kotlin {
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "repro.js" } }
        binaries.executable()
    }
    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(compose.ui)
                implementation(compose.foundation)
            }
        }
    }
}
