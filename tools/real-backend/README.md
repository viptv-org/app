# Real backend checks

This directory is opt-in and excluded from the normal test source set. It uses
the production KMP `RemoteConnection` and HTTPS transport against a running real
backend. No mock engine, synthetic response, or generated media is used.

Provide `VIPTV_REAL_CREDENTIALS_FILE` pointing to a private JSON file containing
`username` and `password`, `VIPTV_REAL_ORIGIN` with the trusted HTTPS API origin,
and `VIPTV_REAL_PROFILE` with an existing authorized profile ID (default `1`).
Optionally set `VIPTV_REAL_TRUST_STORE` to a JVM trust store containing the local
CA; certificate and hostname verification remain enabled.

From the app root:

```sh
./gradlew :composeApp:desktopTest \
  --tests org.viptv.app.RealBackendSessionTest \
  --init-script tools/real-backend/live-test.init.gradle --max-workers=1
```

The check creates its own device session, selects the profile, retires and
restores the real client five times plus two concurrent restore requests,
verifies each refresh token changes, makes
40 parallel authenticated bootstrap reads, and loads real Ted Lasso search and
detail. Finally it signs out its own session. It never intentionally reuses a
consumed token or sends playback/progress events. Credentials live in an isolated
in-memory session store, so this checks shared client rotation and restore, not
Android keystore persistence, Activity lifecycle, or TV focus.

The separate `tools/real_source_batch.py` captures concurrent real source jobs.
Its sanitized output is reusable without retaining stream URLs or grants.
To replay real captured labels through current shared core, flatten its
`results[].events[].streams[]` into `{"sources":[...]}` and run, from the canonical
core checkout:

```sh
cargo run -p viptv-engine --example parse_source_capture --locked -- CAPTURE.json
```

Parser replay does not contact providers; report its time separately from the
live discovery batch and actual playback admission/decoding.
