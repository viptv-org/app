# Final parent title approval qualification

Real Chromium rendered the KMP UI against an isolated test-only HTTP host over trusted local HTTPS. The selected restricted child had no provider assigned. A different owned target child alone had the synthetic private provider. The actual PIN dialog, target-specific search, private-origin approval, authoritative list reload and exact saved-selector revoke all passed. The selected profile stayed unchanged; 12 UI API responses and zero page errors.

`runtime-proof.json` omits account credentials, cookies, session IDs, PIN values and private provider configuration. `target-child-approved.png` was visually inspected and contains only synthetic display labels and code-free controls. `backend-candidate.json` distinguishes the normal release from the qualification-only test host; it records final normal503/SDK505 passing test counts and immutable binary hashes. Ignored tests remain explicitly counted. No binary is checked into this evidence folder and nothing was deployed.

The original retained synthetic fixture database was unchanged; qualification used its private backup with the same keyring and origin. The owned API, proxy and metadata mock were stopped after qualification.
