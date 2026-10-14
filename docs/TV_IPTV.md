# Android TV IPTV entry

`Tv3SignInIptv` supplies the heading, Xtream/M3U tabs, 1130×96 fields and right
actions. Normal entry starts empty; isolated previews use reserved-domain values.
Each tab retains its own draft. Password input is masked; M3U submits only its
playlist URL. Field dialogs and the expanded M3U state remain provisional.

Native entry configures the local Rust/SQLite engine. A sole unlocked profile is
selected automatically; multiple or protected profiles require selection/PIN
before assignment. Credentials are encrypted through the engine's platform-backed
vault. No metadata provider is replaced. The first successful submission opens
Live and requests channels; network errors remain visible, without fabricated data.
The phone alternative opens normal server pairing, separate from local provider
entry; it does not remotely type credentials into the TV's local form.

Three controller checks cover real native configuration/persistence, profile/PIN
isolation, inactive-field omission and cancellation while opening the engine.
Only directory IO is replaced in those checks. A separate real public M3U probe
opened the native engine through this controller and read 100 channels/28 groups
with further pages in 1,063 ms, without a backend. Real Xtream account playback
and the updated physical-TV entry remain unqualified.

The installed TV app was left untouched during manual testing. Older isolated
emulator evidence covered field navigation, dialog cancellation and focus return,
not the new provider submission. New/profile/loading/error variants are tracked
in GAP-034/GAP-056; full supplied-design parity is not certified.
