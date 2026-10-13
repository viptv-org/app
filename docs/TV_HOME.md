# Android TV Home checkpoint — 2026-10-13

The current VIPTV ZIP boards and uploaded `VIPTV-tv-focus-and-rows.html` are the
visual references. Full design parity is not certified.

Home receives five enriched trending titles from the host. Each title displays
for eight seconds after its artwork is ready; the five indicators include an
active timer. Browsing shelves pauses the timer. Play keeps its focus target
when the carousel changes. Actions use the title currently shown during a fade.

Transparent logos are cropped to their visible alpha bounds and bottom-aligned
above the metadata. Year, duration, genres, rating, certification and description
come from the shared metadata model. Other shelf titles retain their available
logo or text fallback without per-focus metadata requests.

Continue Watching uses parent-title presentation and an `S1 E1 · time left`
caption. Its progress track is inside the small card, clear of the focus outline;
the summary has no resume progress bar. Browsing artwork and gradient masks fade
as one plane. Text fades through one title at a time. Returning to the hero keeps
the outgoing summary stable and restores Play without waiting for scrolling.

Shelf headings/cards anchor at y=512/564 on a 1920×1080 viewport. Vertical motion
uses 350 ms, horizontal motion 300 ms, hero departure 450 ms with an 80 px lift,
and focus outlines 120 ms. The first card returns to the 96 px gutter. Above-row
visibility fades over 200 ms. Buttons change from glass to white over 150 ms.
Soft backgrounds blur at quarter width and height before scaling to the viewport.

Real-provider emulator screenshots and a recorded five-title cycle were inspected.
A 643-frame navigation sample measured 16 ms median, 16 ms p95 and 1.56% missed
frame deadlines. These measurements qualify the emulator sample. The physical
TV's earlier sample measured 25 ms median/73 ms p95; it predates this final UI
batch. The latest release-engine APK is now installed and connected over LAN HTTPS.
After cached grouped compositing, an unrecorded 371-frame navigation sample
measured 21 ms median, 32 ms p95 and 1.08% missed deadlines. Recording adds significant overhead, so recorded
measurements are not compared to this sample. Hardware performance remains under
active improvement.
