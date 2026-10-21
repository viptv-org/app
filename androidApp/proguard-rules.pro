# UniFFI calls use JNA interface/method names as exported native symbol names.
-keep class uniffi.** { *; }
-keep class com.sun.jna.** { *; }
# JNA's optional desktop AWT helpers have no Android call sites.
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
# The torrent runtime's JNI bridge binds this exact class and native method name.
-keep class org.playbackgateway.runtime.NativeRuntime { *; }
