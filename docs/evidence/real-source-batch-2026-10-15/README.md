# Fresh real source batch — 2026-10-15

Twelve source-discovery requests ran concurrently through the authenticated canonical local backend using a separate device session and profile 1. Seeds came from freshly fetched real Simkl search/Home cards: eight movies, three exact TV episodes and one exact anime episode. No historical seed, mock provider, synthetic source response, playback request or progress/scrobble write was used by this runner.

The shared batch budget was 55 seconds; it finished in **30.028 seconds**. Ten discovery jobs completed and two starts were rejected because verified source mapping was unavailable. The completed jobs returned **703 real source records in 40 incremental events**: Torrentio 294, Torrentio TB 307 and PenguPlay 102. The IPTV producer returned zero records. Cinemeta was present in the configured inventory but did not produce stream events. Provider/server caching was left at its normal behavior; this proves fresh backend discovery requests, not a separate upstream network fetch for every record.

The [summary](summary.json) retains every reported error. PenguPlay returned partial accepted records alongside unsupported-format errors for three episode jobs, DNS failures for four jobs, timeouts for two and an unavailable error for one. Returned sources are discovery evidence; they are not proof that delivery, decoding or exact title matching will succeed.

The [sanitized replay cache](sanitized.json) preserves all 703 real names, titles and descriptions, 684 filenames, public provider presentation on every record and 99 byte sizes. Source IDs, playback grants, stream URLs, credentials, private headers, fingerprints and binge groups are omitted. Full public release labels remain intact. [Field coverage](safe-field-coverage.json) records the observed public shape. Raw responses and usable ephemeral source handles remain only in mode-600 files under `/tmp/viptv-real-validation-20261015/batch-12-fresh/raw`, inside a private directory.

The production parser was independently reviewed against the fresh labels. All 67 distinct reported name classes were considered; no quality mismatch was found across 703 rows. The 62 unknown heights lack reliable resolution declarations. Eighteen conflicts between delivered rendition names and original filenames correctly preserve the reported name, including PenguPlay 720p/480p/360p renditions with 1080p original filenames. These are reported hints, not measured playback properties.

The [24 real golden cases](real-label-golden.json) contain unmodified public source objects and explicit human expectations. They cover Cyrillic, multiline releases, HDR/Dolby Vision, short filenames, Auto HLS, SD fallback, uncommon heights and reported-name priority. Two concrete language issues were reported before these expectations were written: a Ukrainian flag omitted from language hints (cleanest example index 595), and `Eng SubbeD` incorrectly treated as English audio (index 512). [The independent audit](independent-label-audit.json) preserves those pre-fix observations; the root agent subsequently replayed the production parser after both fixes and all 24 real golden expectations passed.

All ten created discovery jobs received successful cancellation responses, the owned worker service finished with status 0 and no client worker remains. Backend cancellation preserves published source grants; private handles were shared with the playback-validation agent for separately authorized admission checks. No playback success is claimed here.

The reusable runner is [tools/real_source_batch.py](../../../tools/real_source_batch.py). It requires a private session file and fresh `movie.json`/`home.json` metadata directory, writes private raw files and a sanitized cache, uses a shared sub-minute deadline and cancels only the exact jobs it created. Its sanitizer preserves public presentation fields and filenames while removing delivery material. This capture contains no invented cases.

The follow-up [provider-error audit](provider-error-audit.json) confirms that both 422 mappings are expected: two read-only real detail responses identify the 1980 movie `Inception` and the 2010 music title with only Simkl IDs and no verified IMDb identity. The canonical mapping code requires provider-asserted IMDb identity. The three partial PenguPlay format events preserved 36/35/31 accepted streams; discarded upstream objects were not included in the capture, so their exact rejected shapes cannot be established. No further parser change was justified by that evidence.

## Current KMP client against the real backend

[Real read results](real-backend-reads.json) record authenticated Simkl movie,
TV and anime search, Home, Calendar and genre filters. The initial TV search
returned 502 before a subsequent request succeeded; that failure is retained.
Valid lowercase genre values from the backend controls returned 24 cards each
for movies, series and anime. The older manual runtime returns 404 for the new
grouped-search endpoint and cannot qualify that implementation.

The production KMP `RemoteConnection` used its real HTTPS transport and a
separate disposable device session. Five sequential and two concurrent restores
issued eight distinct refresh tokens; 40 concurrent authenticated bootstrap
reads and real Ted Lasso search/detail passed. The session was signed out. See
[test result](kmp-real-session-concurrent.xml) and the
[opt-in runner](../../../tools/real-backend/README.md). Its isolated in-memory
credential store does not qualify Android keystore persistence or Activity
lifecycle.

## Parser and Android candidate

[Before-fix parser output](current-parser.json) and
[after-fix output](current-parser-fixed.json) preserve all 703 parsed records.
The latter batch took 261 ms, with no network requests; live discovery timing
above is measured separately. Eleven previously missing Ukrainian hints are
now present, and the subtitle-only English hint is removed. No quality
expectations changed. The canonical core test `real_source_labels` checks the
24 reviewed, actual provider objects without generating substitute streams.

Normal Android candidate
[`4bcd7ac0af69`](../../../artifacts/android-release-2026-10-15/real-source-4bcd7ac0af69/receipt.json)
contains both shared-parser fixes across three rebuilt ABIs. Signature v2 and
16 KiB ZIP alignment passed; the nine other native libraries match the prior
candidate. It uses the existing local Android Debug signing certificate and
contains no QA Activity or custom TLS trust overlay. The desktop native library
was also rebuilt; prior packaged Linux artifacts retain their original hashes
and predate these two fixes. [Build log](android-desktop-parser-build.log).

## Real playback failures in the preserved manual runtime

Actual Torrentio TB movie and PenguPlay episode playback admissions returned
`gateway_key_rejected` and were closed. No delivery or decode success is claimed
for those attempts. Read-only comparison established that the registered key
matches the gateway and its namespace, is not revoked, but **has expired**.
[Boolean-only diagnosis](expired-gateway-key.json) contains no keys or hashes.
Manual backend/gateway configuration was not changed. Isolated current-runtime
playback qualification is recorded separately when complete.
