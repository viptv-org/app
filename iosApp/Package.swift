// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "VIPTVApp",
    platforms: [.iOS(.v16)],
    targets: [
        .executableTarget(
            name: "VIPTVApp",
            path: "Sources",
            resources: [
                // QA CA bundle for the self-signed viptv.local.test origin
                // (simulator/QA builds only; production keeps default ATS).
                .copy("qa-ca.pem")
            ]
        )
    ]
)
