# Android TV password-entry checkpoint

`Tv3SignInPassword` supplies the heading, 1130×84 fields, ten-column keyboard
and three primary actions. Heading spacing/line height, 34 px field text,
masked-character spacing, 10 px key corners and bottom-row widths follow the
provided HTML. Fields live in a separate component and constrain long text so
the caret does not escape into the gutter.

Keyboard input reads the current case/symbol mode when activated, rather than
using the previous frame's rendered character label. Email/password buffers
already use producer-owned mutable state. Normal Sign in still calls the existing
connection callback; no authentication or server behavior changed here.

The 1920×1080 isolated emulator retained five rapid characters, then an immediate
Shift/character sequence produced uppercase. An 80-character email stayed within
the field bounds with its caret inside, and Back returned to the method chooser.
Desktop tests, Wasm compilation and Android assembly passed. Preview does not
connect, so these checks do not qualify a successful real sign-in.

Delete character, custom-server input, loading/error and long-input states are
additional surfaces recorded in `DESIGN_GAPS.md`. Alternative supplied sign-in
flows and complete typography/focus/motion parity remain unfinished.
