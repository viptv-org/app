# Android TV phone/browser pairing checkpoint

`Tv3SignIn` supplies the phone/browser columns, QR frame, separator, code panel
and email alternative. Shared `TvPairingSignIn` serves both methods. QRose 1.3.1
generates a QR from supplied session data; no QR pattern is drawn by hand.

Normal connections currently supply no pairing session. They expose an
unavailable state with no QR, code, expiry or waiting claim. New code reports the
missing service and restores focus. Backend challenge creation, expiry/polling,
refresh and session completion remain work. The signed-in greeting/transition
also remains unfinished.

The isolated Pairing preview supplies an `.invalid` URL, matching visible host,
example code and static expiry label. It performs no account or polling request.
The 1920×1080 emulator rendered a QR that zbar decoded to that exact supplied URL,
then exercised missing refresh, focus restoration, email navigation and Back.
PairingUnavailable contained no fabricated QR/code/expiry. Desktop tests, Wasm
compilation and Android assembly passed.

The safe preview host differs from the board's production host. Complete
typography/motion, actual expiry handling and connected authentication remain
unqualified. Missing-session/refresh notices are additional UI tracked in
`DESIGN_GAPS.md`; no board is certified as complete.
