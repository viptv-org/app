# Android TV details checkpoint — 2026-10-13

The detail backdrop remains sharp and follows absolute scroll distance across
lazy-item boundaries. A cached grouped surface preserves its image and gradient
while translation updates during drawing. Scrolling no longer switches to a
second blurred backdrop or resets its position when the hero leaves the list.

The selected season fills automatically in bounded background pages. Initial
detail contains 24 episodes; continuation requests use 96, within the host maximum
of 128. The canonical season index exposes every season before its cards arrive.
Artwork remains lazy. Paging preserves current presentation and navigation,
coalesces duplicate in-flight offsets, and fences stale profile/title responses.
A network failure stops that attempt; selecting the season again retries it.

Every composed episode owns a stable focus requester. Related-row Up explicitly
returns to the remembered episode as its row is placed. The hero's initial-focus
handler is disabled while another row is active, preventing a remounted hero from
stealing focus to its off-screen Play button.

The physical TV loaded Re:ZERO episode 25 without a manual More action. A single
Up from the last related card returned to episode 25 and retained its focus after
the transition. Recorded hero/episode transitions were inspected for sharp,
continuous artwork. An unrecorded 439-frame sample measured 20 ms median, 32 ms
p95 and 7.06% missed frame deadlines. Full 60 fps is not certified.

Real Simkl One Piece paging covered 1,184 records/23 seasons with 13 continuation
requests after the initial 24, stable indexes and no duplicates in 2.825 seconds.
Native Cinemeta independently projected 1,242 records from one cached metadata
document. The two counts describe different authoritative provider documents.
