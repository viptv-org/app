# Home row filtering — 2026-10-26

Captures from `HomeRecoveryUiTest` in the desktop JVM (`ImageComposeScene`), produced with:

```sh
VIPTV_HOME_CAPTURE_DIR=docs/evidence/2026-10-26-home-row-filtering \
  ./gradlew --max-workers=2 :composeApp:desktopTest --tests 'org.viptv.app.HomeRecoveryUiTest'
```

- `phone-home-hidden-empty-failed.png` — 390×844 phone layout with one READY row, one PENDING row, and EMPTY/FAILED rows between them. Only the rows with content (READY/PENDING) render.
- `tv-home-hidden-rows.png` — 1920×1080 TV Home where every catalog is empty or failed. The existing all-catalogs error surface shows instead of row headings.
- `tv-home-focused-row-removed.png` — TV Home after a quiet refresh failed the focused row. Focus moved to the surviving row; the removed row's heading and retry are gone.

These are desktop-rendered common layouts, not physical-device captures. The TV captures use an art-less fixture, so the billboard summary line is plain text.