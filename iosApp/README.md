# VIPTV iOS App (xtool/Swift shell)

Xcode-project-free iOS shell hosting the Compose Multiplatform
`MainViewController()` from `app/composeApp` (framework `VIPTVShared`).

## Build (from a Mac or an xtool-equipped host)

1. Produce the Kotlin framework:
   `cd ../ && ./gradlew :composeApp:linkReleaseFrameworkIosSimulatorArm64`
   (or `IosArm64` for device) and copy
   `composeApp/build/bin/<target>/releaseFramework/VIPTVShared.framework`
   into a local checkout path referenced by the `VIPTVShared` dependency
   below (see `Package.swift` comment for wiring once built).
2. Build/sign/sideload with xtool:
   `xtool dev platform ios --swift-package .`

## Notes

- v1 is remote-only (browser-parity): no local engine on iOS from Kotlin;
  the Rust `LocalEngine` Swift bindings (`app/native/adapters/ios/`) are
  available for the shell to wire as a follow-up.
- `Info.plist` carries the QA ATS exception (`viptv.local.test`) for
  simulator/QA builds only.
- Framework linking cannot run on the Linux host — see root `AGENTS.md`.
