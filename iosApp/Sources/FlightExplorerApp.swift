import SwiftUI

@main
struct FlightExplorerApp: App {
    @State private var model = ExplorerModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .tint(Palette.accent)
        }
    }
}
