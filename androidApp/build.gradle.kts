plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "org.viptv.experiment"
    compileSdk = 37
    defaultConfig { applicationId = "org.viptv.experiment"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0-experiment" }
    buildFeatures { compose = true }
    buildTypes {
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
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/rustJni").get().asFile)
}
dependencies {
    implementation(project(":composeApp"))
    implementation("androidx.activity:activity-compose:1.10.1")
    "qaImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    "debugImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

val buildAndroidEngine by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "tools/build_engine.py", "android")
    inputs.files(rootProject.fileTree("native/core/crates") { include("**/*.rs", "**/Cargo.toml") }, rootProject.file("native/core/Cargo.lock"), rootProject.file("tools/build_engine.py"))
    outputs.dir(layout.buildDirectory.dir("generated/rustJni"))
}
tasks.named("preBuild") { dependsOn(buildAndroidEngine) }
