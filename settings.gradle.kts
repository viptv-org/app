pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri(rootDir.resolve("build/kiteplayer/maven"))
            content { includeModuleByRegex("io.github.yuroyami", "kiteplayer-compose-(ui|video)(-.*)?") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "VIPTV"
include(":composeApp", ":androidApp")
