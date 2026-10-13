# Android TV Live guide checkpoint

The `Tv3Live` ZIP board supplies the program summary, 640×360 preview frame,
categories, and two-hour guide. Dedicated TV components replace the generic
Watch/Favourite/Full guide buttons. The shared navigation occupies 132 design
pixels; the Live components place the summary at y=130, preview at y=120,
categories at y=512, time labels at y=592 and program rows at y=636.

The guide has 300 px channel labels, a 28 px gutter, 72 px rows and 8 px row gaps.
Program slots use the supplied 351 px half-hour interval. The red current-time
marker and dot are clipped to the guide viewport and stop at the channel rows,
including when filtering leaves only one channel. Ongoing programs shade their
elapsed portion. A carry-over program has a library chevron indicator. Stable
program IDs own focus state as schedule data changes.

Entering from the header targets the first current program. Up enters the selected
category; Down returns to the first program. Focus updates the summary, while OK
watches the selected channel. The player first handles Back by hiding controls;
the next Back returns to Live. Provider refreshes preserve the selected channel
and program when their identities remain present.

The preview uses the supplied news still, fixture channels/programs, and explicit
HD/CC badges. It enters a fixture live-player scene, not a real decoded live stream.
The normal app still supplies no channel/EPG feed; its empty state directs to
add-on settings. Local/server live-source, EPG, favorites and playback integration
remain outstanding. No supplied board is certified as complete.

The no-guide list, empty state and unavailable favorites notice are provisional
surfaces recorded in `DESIGN_GAPS.md`. Complete typography, motion, guide-scroll
qualification and focus restoration across player re-entry also remain pending.

The isolated 1920×1080 emulator confirmed entry into the first program, Right
updating the summary, OK entering the fixture player, return to Live, and Sports
filtering. Sampled program/marker pixels matched the supplied geometry/colour;
the marker did not continue below the last visible row or through blank filtered
space. A final typography render retained those positions after explicitly
setting summary, badge and time-label line heights. Desktop tests, Wasm
compilation and Android assembly passed. These checks do not qualify a real feed
or decoded live playback.

The current guide update limits its initial focus target to programs rendered
inside the visible window. A feed can retain the currently airing program while
the guide displays a later window; that off-window program no longer receives an
unattached focus request. Removed categories fall back to All. The missing-guide
fallback now uses 72 px remote-focusable TV rows, updates the preview on focus,
and supports category-to-row entry instead of reusing the phone row controls.
The fallback remains provisional under GAP-028. It does not expose favorites.

Isolated `Live --es ui_variant FutureGuide|NoGuide|Empty` fixtures cover these
boundaries without provider requests. A fresh build and emulator checks are
pending; previous captures do not qualify this update.

Refresh restoration now compares the channel's provider as well as its media
identity. Providers may reuse channel IDs; a refresh must not switch the preview
to another provider's matching ID. A multi-provider refresh check remains pending.
