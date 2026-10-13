# Android TV sign-in chooser comparison

`Tv3SignInChoose` supplies the chooser. The current source comparison corrects
its option corners to 16 px, icon-to-label gaps to 26 px, right-column gap to
22 px, and the source's weight-500 secondary text and focus-dependent colour.
Titles and helper text remain bounded within the 900 px rows.

The actions still lead into existing TV sign-in panels. This is a presentation
update, not an authentication implementation. A fresh rendered comparison and
remote navigation check are pending; the older chooser capture does not certify
this change or full-board parity.
