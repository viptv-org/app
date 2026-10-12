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
