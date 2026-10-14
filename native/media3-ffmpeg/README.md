# Android audio decoding

This module contains AndroidX Media3's FFmpeg audio extension at 1.11.1, pinned to
8c6678b657ede1e7883fc164ef73ed483c7796c3. Java/JNI source hashes are in upstream.json.
The experimental video renderer is retained as upstream source but excluded from compilation.

The native libraries contain FFmpeg 6.0.1 with fourteen audio decoders, no software
video decoders, and no GPL/nonfree configuration. native.json records the archive hash,
NDK/API versions, decoder set, 16 KiB page alignment and the three binary hashes.
The source and native libraries retain their Apache 2.0/LGPL 2.1 notices in packaged assets.

Android's player factory prefers this audio extension. Video remains MediaCodec/SurfaceView.
Physical Onn 4K Pro QA confirmed FFmpeg AAC audio, Amlogic hardware AVC video and
59.99–60.00 compositor FPS at 1920×1080p60 with zero reported decoder drops.

The rebuild source is tools/build_media3_audio.py; integrity and APK verification are in
tools/check_media3_audio.py. Native static archives and JNI source remain available for relinking.
