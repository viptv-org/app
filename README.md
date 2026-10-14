# VIPTV

Experimental Kotlin Multiplatform application backed by the Rust/Crux engine.
Android (API 26+), Android TV and desktop support local and remote operation. Browser viewing and account management are remote-only.
Android playback uses Media3 with its official FFmpeg audio extension and hardware video. Desktop uses the pinned KitePlayer fork; browsers use an HTML video adapter with playback-gateway delivery.

The supplied design is held in the companion design repository. This repository is new; no legacy Android or Simkl branch is its base.
Implementation status and missing designs are recorded in `docs/STATUS.md` and `docs/DESIGN_GAPS.md`.
