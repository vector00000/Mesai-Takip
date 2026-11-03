import Foundation
import StoreKit
import Combine

@MainActor
final class StoreKitManager: ObservableObject {
    static let shared = StoreKitManager()
    static let premiumProductId = "mesai_takip_premium"

    @Published var isPremium: Bool = false
    @Published var displayPrice: String = "₺299,99"
    @Published var statusMessage: String = "Standart Plan (Ücretsiz Özellikler Aktif)"
    @Published var isLoading: Bool = false
    private var fetchedProduct: Product?

    private init() {
        Task {
            await loadProductsAndEntitlements()
        }
    }

    func loadProductsAndEntitlements() async {
        do {
            let products = try await Product.products(for: [Self.premiumProductId])
            if let product = products.first {
                self.fetchedProduct = product
                self.displayPrice = product.displayPrice
            }
        } catch {
            // Keep default regional display price fallback
        }
        await refreshPurchasedStatus()
    }

    func purchasePremium() async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }

        do {
            if fetchedProduct == nil {
                let products = try await Product.products(for: [Self.premiumProductId])
                fetchedProduct = products.first
            }
            guard let product = fetchedProduct else {
                statusMessage = "App Store ürün bilgisi (mesai_takip_premium) yükleniyor, lütfen tekrar deneyin."
                return
            }
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                if case .verified(let transaction) = verification {
                    await transaction.finish()
                    isPremium = true
                    statusMessage = "Mesai Takip Premium Aktif (\(displayPrice))"
                } else {
                    statusMessage = "Satın alma doğrulaması tamamlanamadı."
                }
            case .userCancelled:
                statusMessage = "Satın alma işlemi iptal edildi."
            case .pending:
                statusMessage = "Ödeme onayı bekleniyor."
            @unknown default:
                break
            }
        } catch {
            statusMessage = "App Store bağlantı uyarısı: \(error.localizedDescription)"
        }
    }

    func restorePurchases() async {
        isLoading = true
        defer { isLoading = false }
        do {
            try await AppStore.sync()
            await refreshPurchasedStatus()
            if !isPremium {
                statusMessage = "Bu Apple hesabına tanımlı aktif bir abonelik bulunamadı."
            }
        } catch {
            statusMessage = "Geri yükleme tamamlanamadı: \(error.localizedDescription)"
        }
    }

    private func refreshPurchasedStatus() async {
        var active = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result,
               transaction.productID == Self.premiumProductId,
               transaction.revocationDate == nil {
                active = true
            }
        }
        self.isPremium = active
        if active {
            self.statusMessage = "Mesai Takip Premium Aktif"
        }
    }
}
