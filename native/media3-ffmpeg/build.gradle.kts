plugins { id("com.android.library") }
android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 37
    defaultConfig { minSdk = 26; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.annotation:annotation-jvm:1.7.1")
    listOf("media3-common", "media3-exoplayer", "media3-decoder").forEach { implementation("androidx.media3:$it:1.11.1") }
    compileOnly("org.checkerframework:checker-qual:3.33.0")
}
val verifyAudioDecoder by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3", "tools/check_media3_audio.py")
}
tasks.named("preBuild") { dependsOn(verifyAudioDecoder) }

tasks.withType<JavaCompile>().configureEach { exclude("**/ExperimentalFfmpegVideoRenderer.java") }
