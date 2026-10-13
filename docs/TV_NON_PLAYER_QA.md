# TV non-player evidence — 2026-10-13

The current source is based on the ZIP's Tv3 boards and SkTvCalendar. T3 boards
represent tablets. Complete connected data and board parity remain unqualified.

Verified on the disposable TV emulator:

| Surface | Observed behavior |
| --- | --- |
| Live | FutureGuide, NoGuide and Empty remain focusable without unattached requesters |
| Calendar | Release 2 → action → same release; action Up returns to the date |
| Subtitle settings | Left traverses exact size, background and color choices before exiting from the first option |

Source fixes also cover Browse scroll-before-focus and cancellation of stale
focus requests, retained Calendar fallback dates, provider-specific Live selection,
Home progress/metadata geometry, sign-in spacing and visible Search suggestion
focus. Dynamic refresh cases, every shelf and all supplied boards still need
rendered acceptance evidence. No additional provider/authentication success is
implied by UI fixtures.

Native video blur evidence and current APK qualification are recorded separately
in `TV_PLAYER.md`. The test device was emulator-5582; no physical device was used.
