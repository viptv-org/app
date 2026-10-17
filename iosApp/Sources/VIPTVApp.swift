import SwiftUI
import VIPTVShared

@main
struct VIPTVApp: App {
    var body: some Scene {
        WindowGroup {
            ViewControllerContainer(MainViewController())
        }
    }
}

/// Wraps a UIKit view controller inside a SwiftUI scene.
struct ViewControllerContainer: UIViewControllerRepresentable {
    let controller: UIViewController

    func makeUIViewController(context: Context) -> UIViewController { controller }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
