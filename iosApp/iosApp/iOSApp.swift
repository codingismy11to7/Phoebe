import Foundation
import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    private let isPlaybackSmoke: Bool

    init() {
        isPlaybackSmoke = ProcessInfo.processInfo.arguments.contains { argument in
            argument.hasPrefix("--phoebe-playback-smoke=")
        }
        if !isPlaybackSmoke {
            PlatformPlayback_iosKt.ensureIosPlaybackRuntime()
            IosCastCoordinator.shared.initialize()
        }
    }

    var body: some Scene {
        WindowGroup {
            if isPlaybackSmoke {
                IosPlaybackSmokeView()
            } else {
                ContentView()
            }
        }
    }
}

private struct IosPlaybackSmokeView: View {
    @State private var started = false

    var body: some View {
        Color.clear
            .task {
                guard !started else { return }
                started = true
                _ = IosPlaybackSmokeKt.runIosPlaybackSmokeIfRequested()
            }
    }
}
