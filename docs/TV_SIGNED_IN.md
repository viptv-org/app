# Android TV signed-in greeting checkpoint

`Tv3SignedIn` supplies the 168 px round avatar at y=300, name at y=510, email
at y=590 and 200×4 progress track at y=700. `TvSignedIn` accepts explicit identity
and session data. Avatar/name/email enter over 500 ms with the supplied stagger;
the track fills over 2.5 seconds, then invokes an optional completion callback.
Session ID owns animation state so unrelated recomposition does not restart it.

Normal authentication does not yet supply account presentation data and does
not render this component. Profile names are not assumed to be account identity.
The isolated preview supplies the board's example identity; no sign-in request or
authenticated session is created. SignedIn routes to Profiles on completion;
SignedInHold retains the completed scene for visual inspection.

Connected account/bootstrap presentation, transition timing, reduced-motion
visuals and full typography/design parity remain work. This renderer and its
fixture callback do not prove authentication or complete board certification.

The isolated 1920×1080 emulator checked the supplied name/email labels and filled
track, then confirmed completion to Profiles. Desktop tests, Wasm compilation
and Android assembly passed. Real authenticated navigation remains unqualified.
