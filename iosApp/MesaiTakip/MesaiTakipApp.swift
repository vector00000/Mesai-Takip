import SwiftUI

@main
struct MesaiTakipApp: App {
    @StateObject private var store = MesaiStore()

    var body: some Scene {
        WindowGroup {
            MainTabView()
                .environmentObject(store)
                .preferredColorScheme(.light)
        }
    }
}
