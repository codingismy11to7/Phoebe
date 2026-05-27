import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    private let isPlaybackSmoke: Bool

    init() {
        isPlaybackSmoke = IosPlaybackSmokeKt.runIosPlaybackSmokeIfRequested()
        if !isPlaybackSmoke {
            PlatformPlayback_iosKt.ensureIosPlaybackRuntime()
            IosCastCoordinator.shared.initialize()
        }
    }

    var body: some Scene {
        WindowGroup {
            if isPlaybackSmoke {
                Color.clear
            } else {
                ContentView()
            }
        }
    }
}
