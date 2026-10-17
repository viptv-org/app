# Android TV Calendar checkpoint

Current connected functionality is recorded in [Calendar completion](CALENDAR_COMPLETION.md): category/week/date controls, progressive real schedules, exact metadata-resolved release actions and current render/focus receipts. The preview-era limitations below are historical and are superseded where that document records qualification.

The supplied `SkTvCalendar` Simkl ZIP board is the TV Calendar source. It has
64 px date pills at y=140, 1100×132 release rows starting at y=250, 18 px row gaps,
and a 584 px details panel at x=1240. Dedicated TV components replace the generic
calendar layout; phone and desktop rendering remain on their existing paths.

Date entry targets the selected day; Down targets its first release. Row focus
updates the preview. All supplied dates remain selectable, including dates with
no releases. Empty dates show a separate state from an unavailable schedule.
Rows use explicit text line heights and the provided 72×108 poster dimensions.

Optional `TvCalendarAction` data supplies a watch label, target card and provider
attribution. Without it, the panel offers View details and omits attribution.
Normal connections do not invent a next episode or claim that data came from
Simkl. The normal app still lacks a release-feed/action contract and supplies an
unavailable schedule. Local/server feed and tracking integration remain work.

The isolated preview uses the supplied artwork and board's Watch E481/Data from
Simkl labels. Its detail/source routes are generic fixtures and do not prove that
episode 481 resolves or plays. No account/provider/history operations run.
Typography/motion, connected data and complete design parity remain unqualified.

Empty-day/unavailable-feed panels and the fallback View details action are
additional UI absent from the supplied populated board, tracked in `DESIGN_GAPS.md`.

At 1920×1080 the isolated TV emulator exercised header/date/release traversal,
the first row's supplied position, focus changing the preview/action, Tomorrow
filtering, and a supplied empty date. The selected date is preserved across
incoming data changes when it remains available. The supplied artwork reuses
existing resources; no duplicate image files were added. Desktop tests, Wasm
compilation and Android assembly passed. Real feeds and episode resolution remain
unqualified by these preview checks.

The current focus update gives every release a stable row requester. Right from
any release enters the preview action; Left returns to that release, and Up from
the action returns to the selected date. Date changes reset the release scroll
position. Preview titles, descriptions, action labels and row statuses now have
explicit bounds so long provider text cannot push the action off the screen.
These changes still require a fresh build and rendered remote-navigation check;
the earlier captures do not qualify this update.

When an incoming schedule removes the selected date, its fallback now becomes the
stored selection and the release selection resets with it. Reintroducing a removed
date therefore does not silently switch the user away from the fallback date.
This refresh path still needs rendered validation with changing fixture data.
