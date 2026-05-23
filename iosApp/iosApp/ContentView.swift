import SwiftUI
import UIKit
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        IosCastCoordinator.shared.attach(to: controller)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
