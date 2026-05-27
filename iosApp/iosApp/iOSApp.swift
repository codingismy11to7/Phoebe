import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        if IosPlaybackSmokeKt.runIosPlaybackSmokeIfRequested() {
            RunLoop.main.run()
        }
        PlatformPlayback_iosKt.ensureIosPlaybackRuntime()
        IosCastCoordinator.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
