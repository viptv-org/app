# Android TV Home and catalog checkpoint

The supplied `Tv3Home`, `Tv3HomeLoading`, `Tv3HomeToRows`, and `Tv3Movies`
HTML and artwork are the source for the dedicated TV components. This is an
implementation checkpoint, not certification of complete design parity.

Home has the billboard artwork and actions, Continue Watching and catalog
shelves, a focused-title summary, a 450 ms shelf movement and a 400 ms focused
title delay. Loading placeholders use the supplied 1.4 second gradient shimmer,
clipped to their individual shapes. The selected header underline is constrained
to its tab rather than expanding the navigation row across the screen.

The catalog uses seven poster columns and supplied genre chips. Entry into the
filter row targets the selected genre. Header and catalog no longer both request
initial focus. Genre filtering only operates on provided membership data; the
normal engine contract currently provides no genre membership.

The isolated 1920×1080 TV emulator exercised Home → Play → Continue Watching,
horizontal summary updates, Up back to Home, and Action genre selection. Preview
images and membership are fixtures, not evidence of provider capabilities.
Desktop tests, Wasm compilation, and Android APK assembly passed.

Remaining work includes exact coordinated header/hero entrance and exit motion,
loading-to-content fade, reduced-motion qualification, actual carousel selection
and cycling, richer provider presentation data, and full typography comparison.
The fixture indicator count does not mean that carousel cycling is implemented.
