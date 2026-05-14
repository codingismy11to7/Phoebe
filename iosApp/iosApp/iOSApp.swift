import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        PlatformPlayback_iosKt.ensureIosPlaybackRuntime()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
