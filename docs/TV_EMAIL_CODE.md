# Android TV email-code checkpoint

`Tv3SignInCode` supplies six 104×124 slots at y=260, the numeric keypad at
x=1180/y=250, status text and Send a new code. `TvEmailCode` reuses the numeric
key component with the board's 140 px width and 36 px digits. Input is capped at
six digits. Draft state is keyed by challenge session ID, not changing status
text. Busy state disables keypad/resend actions.

The normal email-entry screen still has no delivery/session API and does not
advance to this screen. A challenge must be supplied; the isolated EmailCode
preview supplies the board's 481 draft and static example status. It did not
send an email. Verify/resend callbacks are absent and produce explicit
unavailable notices. Dismissal restores the appropriate initiating control.
Back returns to email entry.

Real challenge creation, verification, expiry/resend, server error mapping and
successful login remain work. The code-entry layout is an implementation
checkpoint, not authentication proof or full design certification. Complete
typography/motion parity also remains unfinished.

At 1920×1080 the isolated emulator checked the supplied 481 draft and focused
5 key, six-digit cap, erase, unavailable verification/resend, initiating-control
focus restoration and Back into email entry. Desktop tests, Wasm compilation
and Android assembly passed. The fixture did not create or consume a real code.
