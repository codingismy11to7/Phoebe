import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        PlatformPlayback_iosKt.ensureIosPlaybackRuntime()
        IosCastCoordinator.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
