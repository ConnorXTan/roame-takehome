import SwiftUI
import Shared

@main
struct RoameStarterApp: App {
    var body: some Scene {
        WindowGroup {
            Text(Greeting().message())
        }
    }
}
