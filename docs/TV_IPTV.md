# Android TV IPTV-entry checkpoint

`Tv3SignInIptv` supplies the heading, Xtream/M3U tabs, 1130×96 fields and right
actions. `TvIptvSignIn` follows those positions; draft values remain in memory.
The isolated preview uses the board's example address and username, not real
provider credentials. Normal entry starts empty.

Xtream shows server, username and masked password. M3U shows a playlist address
only. A future M3U connection callback receives no hidden username/password.
The current app supplies no provider-connect callback, so Connect explicitly
reports unsupported provider connections. No request or add-on installation is
fabricated. The board's TV-only storage claim is replaced with an accurate
in-memory explanation until a local/server storage contract exists.

Field input uses a provisional shared Material dialog and Android TV's keyboard.
Back first hides the system keyboard, then cancels the dialog and restores the
initiating field. The phone alternative enters the existing phone state; pairing
is unfinished. M3U/input/unsupported-service surfaces are tracked in
`DESIGN_GAPS.md`.

Provider registration, validation, protected credential persistence, catalog/EPG
ingestion and real streams remain work. Complete typography/motion and connected
behavior remain unqualified. No supplied board is certified as fully matching.

The isolated 1920×1080 emulator checked the supplied username-row position and
focus, field-dialog cancellation, M3U field switching, unavailable Connect and
focus restoration, and Back into method selection. Explicit tab/address-field
Up/Down targets avoid spatial-navigation jumps. Desktop tests, Wasm compilation
and Android assembly passed; no actual provider authentication was exercised.
