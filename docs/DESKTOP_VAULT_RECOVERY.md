# Desktop vault recovery

Linux secure storage now fails promptly when its Secret Service collection is locked or unavailable. It preserves saved credentials and permits another connection choice. Startup reports a secure-storage read failure on the existing connection screen. Explicitly choosing Local still works; its persistence notice is retained in state and rendered in Settings, rather than immediately on Profiles/Home.

The previous synchronous `keyring` adapter could wait for an unanswered unlock prompt for up to one year. Cancel returned to Connect immediately, but the next Local operation joined the unfinished native call and remained busy. A real private GNOME Keyring/Xvfb fixture reproduced this: Cancel took 5 ms, while the canceled restore remained unfinished and Local had not opened its database after another 1.2 seconds.

The [Linux adapter](../native/core/crates/viptv-core/src/local_engine/secrets/linux.rs) uses the already resolved `dbus-secret-service` dependency with DH encryption and a zero prompt timeout. It does not unlock collections or items. Exact `service` / `username` / `target` matching retains precedence over the old default-collection `service` / `username` fallback. Ambiguous matches remain errors. Windows/macOS retain their existing `keyring` implementation; Android is unchanged. No native API or generated-binding change was required.

Actual checks used an owned D-Bus session, a new GNOME keyring, temporary HOME/XDG directories and an owned Xvfb display. They never accessed the user's wallet, display or default audio. Each child had an external timeout and all fixture processes were stopped. The [evidence](evidence/desktop-vault-2026-10-15/summary.json) retains the original failure and subsequent results.

| Check | Observed result |
| --- | --- |
| Local connection marker | Save/read and a second JVM read passed through real `DesktopSecrets` and the OS vault. |
| Remote compatibility | Existing JSON decoded; save/rotation, replacement with Local and repeated clear passed. |
| Old credential identity | No-target credential read/update/delete passed; exact credentials took precedence over legacy records; ambiguous get/write/delete were rejected. |
| Locked collection | Final packaged get/write/delete failed in 5/3/3 ms; no unlock prompter activated. |
| Cancel and next Local choice | Cancel took 3 ms; canceled restore completed, no profiles appeared after Cancel, and explicit Local reached Profiles with its persistence notice retained for Settings. |
| Missing session bus | Final packaged fixture returned a secure-storage error in 0.435 seconds including JVM startup and native-resource hash verification. |
| Missing default collection | Read/write/delete failed in 6/3/3 ms. The default alias stayed absent and no keyring data files were created. |
| Native regression/ABI | Five focused Rust checks passed; all 121 existing exported UniFFI checksum values remained identical. |

The initial adapter fixture also exposed that the existing startup controller silently discarded a vault-read failure. The final shared correction retains a bounded message on Connect, respects cancellation/generation ownership, and renders that message in desktop/mobile connection choices. The final common gate passed 287 desktop checks plus isolated Wasm, normal Android and the current Linux image. The exact extracted package was then checked without a native-library override: locked get/write/delete returned in 5/3/3 ms, Cancel took 3 ms, and the startup error was retained.

Actual original packaged Main was launched twice in the private environment. With the unlocked saved Local marker, it restored [Who’s watching / Profiles](evidence/linux-final-287-2026-10-15/actual-main-unlocked-local-restore.png). With the collection locked, it rendered the [visible secure-storage message on the connection choices](evidence/linux-final-287-2026-10-15/actual-main-locked-storage-warning.png). Both captures were visually inspected. These are fresh final-package results, separate from the earlier adapter-only evidence.

The rebuilt Linux library is SHA256 `0212a058da9465e46c6eb885963f13e22bb8e3dc7ddd17198e9d1e790d52cd06`. Canonical and native overlay receipts contain 69 matching files and explicitly identify this as a Linux-only rebuild. Android binaries remain from the preceding qualified snapshot and are not claimed rebuilt from the updated source.

Final [Linux Deb candidate for the frozen 287-test snapshot](../artifacts/linux-desktop-2026-10-15/final-287-ebc3fabb2c09/viptv-experiment_0.1.0-1_amd64.deb): SHA256 `ebc3fabb2c094388a3a24510f6ee025d25c92ea7ec28e102b22bc51c2981ef4d`, 134,626,580 bytes. Its application JAR is SHA256 `eb88abd8712118f0a2460b1442d6b36a5d82f4b4bc4e1372e805fc41b8528225`. [Fresh package receipts](evidence/linux-final-287-2026-10-15/summary.json) verify the bundled JVM/native SQLite, all 32 ELF inventories, desktop Exec/icon paths, `xdg-utils` dependency and actual OS-vault/Main behavior.

Current-package media playback was not rerun. The [separate media continuity receipt](evidence/linux-final-287-2026-10-15/media-component-continuity.json) verifies 210 player/QA classes, 106 dependency files and 96 JVM-runtime files are byte-identical to the earlier actual VTT-played `97e065` candidate. The changed Linux core library is explicitly excluded from that unchanged-component claim. The final gate also includes the nine Home render/key regressions, including [Up returning from a hero-less shelf to visible navigation](evidence/linux-final-287-2026-10-15/tv-home-no-hero-navigation.png); scene ownership is consistently EDT and the full suite has no background snapshot-observer errors.

Earlier immutable packages, including `2de7c1517d82` and `portable-local-entry-78d32a8c3404`, remain preserved; all 109 protected served-Wasm file hashes are unchanged. Actual Debian installation, physical desktop/window controls, hardware/audio, Windows/macOS execution and distribution signing remain unqualified.
