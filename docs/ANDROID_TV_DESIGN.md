# Android TV design status

Android TV is the current priority within the full KMP design-parity objective.
The source is the supplied TV ZIP's 37 `Tv3` boards and their HTML/assets. The
older `T3` tablet boards belong to another platform. Main branches are unchanged.

The current TV implementation includes a poster welcome screen, sign-in method
chooser, password entry with a remote keyboard, full-screen movie/series details,
season/episode shelves, a remote search keyboard, separate title/channel search
rows, and a modal source drawer. The shared TV header now uses the ZIP's fixed
positions and spacing. Logo artwork uses the supplied blend treatment.

An isolated 1920×1080 Android TV emulator exercised Down into the resume episode,
Right into the next episode, search typing/deletion, password-field input, and
Back through password → methods → welcome. These are preview interactions, not
proof of authentication or provider playback. Desktop tests, Wasm compilation
and Android assembly passed with these TV components.

The preview dataset is separate from normal connections. Cast/resume details,
recommendations, search suggestions, channel schedule labels, source quality,
format and ranking require richer engine/server presentation contracts. Normal
connections show available fields and do not invent those values. Browser/email/
device/IPTV authentication still has unavailable states, rather than the complete
supplied flows. No TV board is certified as fully matching.

Remaining TV work includes splash and successful sign-in, all alternative auth
boards, profile editing, Home loading and Home-to-rows motion, Movies, Live guide,
My List, final playback-state and settings typography/motion/data qualification.
The implemented screens still have typography, focus/motion and data-contract
checks outstanding. `design-coverage.json` records each supplied board separately.

The dedicated TV player and scoped real-video checks are described in `TV_PLAYER.md`.

Dedicated TV settings and their unavailable contract limits are described in `TV_SETTINGS.md`.

Home, loading and Movies implementation progress and remaining qualification
are described in `TV_HOME.md`.

Dedicated My List layout and its missing persistent data contract are described
in `TV_LIBRARY.md`.

Profile picker geometry, focus behavior and remaining editor work are described
in `TV_PROFILES.md`.

The dedicated editor and its provisional dialog/data boundaries are described
in `TV_PROFILE_EDIT.md`.

Dedicated Live layout, bounded guide marker and missing connected feed/playback
contracts are described in `TV_LIVE.md`.

The Calendar board belongs to the Simkl ZIP (`SkTvCalendar`); its dedicated TV
layout and missing connected feed/actions are described in `TV_CALENDAR.md`.

PIN layout, masking, longer-PIN compatibility and preview limits are described
in `TV_PIN.md`.

Rapid search-input handling, bounded caret and shared loading placeholders are
described in `TV_SEARCH.md`.

Password-entry keyboard/layout qualification and remaining connection limits
are described in `TV_PASSWORD.md`.

Dedicated email-entry layout and its unavailable-code/phone boundaries are
described in `TV_EMAIL.md`.

IPTV-entry layout and missing provider/storage integration are described in
`TV_IPTV.md`.

TV metadata now shares `TvMediaBadge`: the supplied 30 px height, 8 px horizontal
padding, 5 px corners, 1.5 px border, bold 18 px label and letter spacing are used
by Home, focused summaries, details, Live and next-episode metadata. At 1920×1080,
Home/Details/Live/Next preview captures confirmed 22 px badge text lines inside
those containers. Desktop tests, Wasm compilation and Android assembly passed.
These scoped checks do not certify complete board parity or connected metadata.

Email-code layout and missing verification/session integration are described in
`TV_EMAIL_CODE.md`.

The supplied startup splash and controller flag are described in `TV_SPLASH.md`.

Phone/browser pairing layout, QR payload checks and missing backend contracts
are described in `TV_PAIRING.md`.

The signed-in greeting renderer and missing account/bootstrap transition data
are described in `TV_SIGNED_IN.md`.

The source drawer now slides from the right in 300 ms with decelerating easing
and exits in 200 ms with accelerating easing. Its background dim tracks the
same animation. Repeated dismissal is guarded; source-opening requests are not
delayed. The isolated emulator retained source focus and rapid Back returned to
the movie details scene. A recording showed the moving drawer edge revealing
content during exit. Desktop tests, Wasm compilation and Android assembly passed.
Precise frame timing/interruption coverage and complete motion parity remain work.

Source discovery's three 104 px placeholders now use the shared 1.4 second TV
shimmer, clipped to their supplied 14 px corners. Empty/loading-empty drawers
focus All instead of requesting an absent or disabled source row. The isolated
emulator checked partial discovery animation, empty/loading-empty focus and
Back dismissal. Desktop tests, Wasm compilation and Android assembly passed;
real discovery timing/rich source metadata remain unqualified.
