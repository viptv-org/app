pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "VIPTV"
include(":composeApp", ":androidApp", ":baselineprofile")

include(":media3-ffmpeg")
project(":media3-ffmpeg").projectDir = file("native/media3-ffmpeg")
