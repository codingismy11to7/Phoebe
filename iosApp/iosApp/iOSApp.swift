import SwiftUI

@main
struct iOSApp: App {
    init() {
        EnsureIosPlaybackRuntimeKt.ensureIosPlaybackRuntime()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
