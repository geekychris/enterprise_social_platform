import SwiftUI

@main
struct WorkSphereApp: App {
    @State private var auth = AuthService.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(auth)
                .environment(APIClient.shared)
        }
    }
}
