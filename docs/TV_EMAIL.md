# Android TV email-entry checkpoint

`Tv3SignInEmail` supplies the step heading, 1130×92 field at y=250, keyboard at
y=382 and Next/phone actions. `TvEmailSignIn` follows those dimensions and reuses
the corrected entry keyboard. The normal field starts empty; the isolated Email
preview uses the board's vynxc draft and initial Gmail-key focus.

Next has an optional request-code callback. The current normal connection does
not supply it, so Next explicitly reports unavailable email codes. It does not
invent a sent-code session or call an assumed endpoint. Email-code delivery,
verification, expiry, resend and successful sign-in remain backend work. The
provided code-entry board remains unfinished.

Hardware Backspace and an additional remote Delete action edit the current
buffer. Long text stays in the field. Unavailable-dialog dismissal returns focus
to Next; the phone action enters the existing phone state and Back returns to
the method chooser. Phone pairing remains unfinished.

At 1920×1080 the isolated emulator checked Gmail append, Backspace, initial
suffix focus, unavailable Next and restored focus, phone navigation and Back.
Desktop tests, Wasm compilation and Android assembly passed. No email/provider/
account request ran. Caret pulse, complete motion/typography parity and connected
authentication remain unqualified; no board is certified as complete.
