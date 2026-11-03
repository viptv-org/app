# VIPTV

Kotlin Compose Multiplatform client backed by the Rust/Crux engine.
Android (API 26+), Android TV and desktop support local and remote operation. Browser viewing and account management are remote-only.
Android playback uses Media3 with its official FFmpeg audio extension and hardware video. Desktop uses libmpv with a checksummed native runtime and bounded media buffers; browsers use an HTML video adapter with playback-gateway delivery.

The supplied design is held in the companion design repository. Missing designs are recorded in `docs/DESIGN_GAPS.md`.

Android build, shipped ABIs and the single-ABI emulator loop: [Android builds](docs/ANDROID_BUILD.md).
