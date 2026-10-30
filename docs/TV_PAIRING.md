# Android TV phone/browser pairing checkpoint

`Tv3SignIn` supplies the phone/browser columns, QR frame, separator and code
panel. Shared `TvPairingSignIn` serves both methods. QRose 1.3.1 generates a QR
from supplied session data; no QR pattern is drawn by hand.

`TvRemotePairing` connects the layout to the server-owned device challenge: QR,
code, expiry and polling, New code and Use another server. The server selector,
creating/expired/error states and the pairing poll are recorded in
`DESIGN_GAPS.md` GAP-038. TV offers no email alternative.

The isolated Pairing preview supplies an `.invalid` URL, matching visible host,
example code and static expiry label. It performs no account or polling request,
and without a challenge there is no New code action. The 1920×1080 emulator
rendered a QR that zbar decoded to that exact supplied URL. PairingUnavailable
contained no fabricated QR, code or expiry.

The safe preview host differs from the board's production host. Complete
typography/motion and physical-TV rendering remain unqualified; no board is
certified as complete.
