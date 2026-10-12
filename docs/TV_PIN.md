# Android TV PIN checkpoint

The supplied `Tv3Pin` board places the avatar at y=150, heading at y=300,
four PIN slots at y=380, keypad at y=540, and recovery link at y=920. The
120 px avatar uses 22 px corners; slots use 14 px corners and keys use 10 px.
The keypad reuses `TvAction` and the Lucide erase icon rather than a separate
button implementation. Slot accessibility labels disclose filled/empty state,
not the digit value.

Normal entry starts empty. The isolated preview starts with the board's two
masked digits; `Empty` starts empty. Hardware digits and Backspace operate on
the same buffer as the keypad. Enter delegates to the producer's current PIN
state, avoiding a stale rendered-length check during rapid input. Normal
submission still uses the existing profile controller and its 4–8 digit policy.

To retain existing longer PINs, typing a fifth digit switches to a stable eight
slot layout. OK appears once at least four digits exist; it is absent from the
initial board state. These additional states, error text and the unavailable
recovery dialog are recorded in `DESIGN_GAPS.md`. Recovery dismissal waits for
the parent window and restores focus to its link.

Preview submission navigates to Home without checking a stored credential.
Preview Back returns to Profiles. These fixtures do not qualify real unlock,
recovery or persistence. Complete typography/motion parity remains unfinished.

At 1920×1080 the isolated emulator confirmed two masked digits in four slots,
the focused 5 key, the eight-digit limit, Backspace, recovery dismissal/focus,
Back into Profiles and rapid digit/Enter submission through the synthetic callback.
Desktop tests, Wasm compilation and Android assembly passed. The input test
exposed and verified the stale-render Enter fix; it did not check a real credential.
