# Android TV startup checkpoint

`Tv3Splash` supplies the centered brand at y=440, 600 ms logo fade and a
240×4 loading track at y=600. `TvSplash` uses those dimensions; its 96 px
indicator traverses the clipped track over 1.2 seconds. The animation reads
state in its graphics layer rather than relaying out the page per frame.

The controller initially exposes `starting=true`. The TV app displays the
splash during session restoration and clears startup in operation completion;
accepted bootstrap data also creates non-starting state. No minimum splash
duration or fake authentication success is added. Non-TV startup rendering is
unchanged.

The isolated Splash preview performs no restoration or account request.
Real restoration timing, reduced-motion visuals and full typography parity
remain unqualified. The supplied signed-in greeting still needs actual account
presentation data and a connected transition; this work does not invent it.

The 1920×1080 isolated emulator sampled multiple indicator frames and confirmed
movement inside the 240 px track with unchanged pixels beyond both edges. A
subsequent render measured the brand mark at y=448–536, centered in the supplied
104 px brand row. Explicit row constraints prevent natural text height from
moving the icon down. Desktop tests, Wasm compilation and Android assembly passed.
