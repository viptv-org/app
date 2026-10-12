# Android TV profile picker checkpoint

`Tv3Profiles` is the source for the profile picker: heading at 220 px, 200 px
avatars at 380 px, 56 px gaps, 20 px name spacing, 4 px focus borders, and a
300×72 Manage profiles button at 780 px. The centered lazy row includes its
edge padding in its measured width so the first avatar starts at the supplied
348 px when four profiles plus Add profile are present.

Focus frames fade over 120 ms without scaling the artwork. Down from each
profile or Add reaches Manage profiles; Up returns to the first profile. With
an empty local list, Add is the initial focus target. Native local profile
selection and management callbacks remain connected through `ProfilesScreen`.

The normal profile engine already supports local creation, rename, avatars,
PINs and deletion. The dedicated TV editor is described in `TV_PROFILE_EDIT.md`.
Maturity/language contracts, per-profile entry motion and full visual
qualification remain work.

The isolated Profiles preview uses the supplied unlocked board state by default.
`Locked` retains the locked-profile fixture. Manage/Done change preview state;
preview editing and creating do not mutate a database or prove persistence.

The 1920×1080 emulator confirmed the first avatar's 348 px x-position, Right to
the next profile, Down into Manage profiles, Manage/Done state changes, and a
supplied locked-profile marker. This is preview interaction evidence, not a new
claim about backend authentication or profile persistence.

A subsequent render check confirmed the focus frame is white, and the empty
local picker initially focuses Add profile and opens the existing name dialog.
