pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri(rootDir.resolve("build/kiteplayer/maven"))
            content { includeModuleByRegex("io.github.yuroyami", "kiteplayer-(compose-(ui|video)|core|output)(-.*)?") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "VIPTV"
include(":composeApp", ":androidApp")

include(":media3-ffmpeg")
project(":media3-ffmpeg").projectDir = file("native/media3-ffmpeg")
