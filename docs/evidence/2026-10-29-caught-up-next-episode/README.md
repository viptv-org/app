# Caught-up Continue Watching cards name the next episode (app#132)

Rendered by `CaughtUpNextEpisodeTest` (Compose Desktop, a fake host answering
`/api/bootstrap` and `/api/profiles/1/continue` with the server's continuation
entries). Set `VIPTV_CAUGHT_UP_CAPTURE_DIR` and run the class to regenerate.

| Render | Shows |
|---|---|
| `phone-caught-up-next-episode.png` | Home's row (390×844 @2x): "Dated Finale" — `S1 E1 · Watched` then `S1 E2 · Nov 4`; "Undated Finale" — `S2 E1 · Watched` then `S2 E2 coming soon`. |
| `page-caught-up-next-episode.png` | The Continue Watching page: the same two cards, and "Finished Show" with only `S3 E1 · Watched` because its entry announced no next episode. |
| `tv-caught-up-next-episode.png` | TV Home's shelf (1920×1080): the board's coordinate caption, then the announced episode — `S1 E1  S1 E2 · Nov 4`, `S2 E1  S2 E2 coming soon`, `S3 E1`. |

The strings are the app's until the design copy lands (a separate design issue).
