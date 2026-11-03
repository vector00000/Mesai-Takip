import Foundation

struct FirebasePlistConfig {
    let apiKey: String
    let gcmSenderId: String
    let bundleId: String
    let projectId: String
    let storageBucket: String
    let googleAppId: String
    let isSignInEnabled: Bool
    let isGcmEnabled: Bool

    static func loadFromBundle() -> FirebasePlistConfig {
        if let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let dict = NSDictionary(contentsOfFile: path) as? [String: Any] {
            return FirebasePlistConfig(
                apiKey: dict["API_KEY"] as? String ?? "",
                gcmSenderId: dict["GCM_SENDER_ID"] as? String ?? "903542625585",
                bundleId: dict["BUNDLE_ID"] as? String ?? "com.aistudio.mesaitakip.kvnrpd",
                projectId: dict["PROJECT_ID"] as? String ?? "mesai-takip-fc13a",
                storageBucket: dict["STORAGE_BUCKET"] as? String ?? "mesai-takip-fc13a.firebasestorage.app",
                googleAppId: dict["GOOGLE_APP_ID"] as? String ?? "1:903542625585:ios:9c82af2ec534147ced4c10",
                isSignInEnabled: dict["IS_SIGNIN_ENABLED"] as? Bool ?? true,
                isGcmEnabled: dict["IS_GCM_ENABLED"] as? Bool ?? true
            )
        }
        return FirebasePlistConfig(
            apiKey: "",
            gcmSenderId: "903542625585",
            bundleId: "com.aistudio.mesaitakip.kvnrpd",
            projectId: "mesai-takip-fc13a",
            storageBucket: "mesai-takip-fc13a.firebasestorage.app",
            googleAppId: "1:903542625585:ios:9c82af2ec534147ced4c10",
            isSignInEnabled: true,
            isGcmEnabled: true
        )
    }
}

final class FirebaseIosClient {
    static let shared = FirebaseIosClient()
    let config: FirebasePlistConfig

    private init() {
        self.config = FirebasePlistConfig.loadFromBundle()
    }

    var isConfigured: Bool {
        !config.projectId.isEmpty && !config.googleAppId.isEmpty && !config.apiKey.isEmpty
    }

    /// Syncs a user backup payload to Cloud Firestore (`mesai-takip-fc13a`) via REST API
    func syncBackupToFirestore(
        deviceUserId: String,
        recordsCount: Int,
        budgetCount: Int,
        reportsCount: Int,
        payloadJson: String,
        completion: @escaping (Result<String, Error>) -> Void
    ) {
        guard isConfigured else {
            completion(.failure(NSError(domain: "FirebaseIosClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "GoogleService-Info.plist yapılandırması eksik."])))
            return
        }

        let urlStr = "https://firestore.googleapis.com/v1/projects/\(config.projectId)/databases/(default)/documents/ios_backups/\(deviceUserId)?key=\(config.apiKey)"
        guard let url = URL(string: urlStr) else {
            completion(.failure(NSError(domain: "FirebaseIosClient", code: -2, userInfo: [NSLocalizedDescriptionKey: "Geçersiz Firestore URL."])))
            return
        }

        let nowIso = ISO8601DateFormatter().string(from: Date())
        let body: [String: Any] = [
            "fields": [
                "bundleId": ["stringValue": config.bundleId],
                "googleAppId": ["stringValue": config.googleAppId],
                "updatedAt": ["stringValue": nowIso],
                "recordsCount": ["integerValue": "\(recordsCount)"],
                "budgetCount": ["integerValue": "\(budgetCount)"],
                "reportsCount": ["integerValue": "\(reportsCount)"],
                "payloadJson": ["stringValue": payloadJson]
            ]
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: body, options: [])

        URLSession.shared.dataTask(with: request) { _, response, error in
            if let error = error {
                DispatchQueue.main.async {
                    completion(.failure(error))
                }
                return
            }
            if let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) {
                DispatchQueue.main.async {
                    completion(.success(nowIso))
                }
            } else {
                let status = (response as? HTTPURLResponse)?.statusCode ?? 500
                DispatchQueue.main.async {
                    completion(.failure(NSError(
                        domain: "FirebaseIosClient",
                        code: status,
                        userInfo: [NSLocalizedDescriptionKey: "Firestore yanıt kodu: HTTP \(status)"]
                    )))
                }
            }
        }.resume()
    }
}
