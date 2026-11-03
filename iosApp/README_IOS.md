# Mesai Takip — iOS Uygulaması (`iosApp/`) Kurulum ve App Store Yayınlama Rehberi

Android projenize hiçbir zarar vermeden, aynı paket kimliği (`com.aistudio.mesaitakip.kvnrpd`) ve tüm 8 yeni özellikle uyumlu yerel **SwiftUI iOS projesi** `iosApp/` dizinine eklenmiştir.

## Neler Eklendi?
1. **`iosApp/MesaiTakip/Models/MesaiModels.swift`**
   - Mesai türleri (Normal, Hafta İçi, Hafta Sonu, Gece Vardiyası, Resmî Tatil), dönemlik ücret tarifeleri (`RateTariffRule`), bütçe gelir/gider kayıtları (`BudgetTransaction`), kişisel maaş programlama raporu arşivi (`PayrollArchiveReport`), bildirim merkezi (`AppNotification`), akıllı kayıt denetimi (`AuditWarning`) ve başarı rozetleri (`AchievementBadge`).
2. **`iosApp/MesaiTakip/Store/MesaiStore.swift`**
   - Tek dokunuşla mesai başlatma/bitirme ve mola düşümü, çift sayım önlemeli maaş-bütçe mutabakatı, dondurulmuş ücret tarifeli aylık rapor arşivleme, mükerrer bildirim engelleyici yerel bildirim (`UNUserNotificationCenter`) desteği ve farklı çalışma günü bazlı başarı hesaplayıcı.
3. **`iosApp/MesaiTakip/Views/MainTabView.swift`**
   - Açık renkli kurumsal tasarıma sahip 5 sekmeli iOS arayüzü (Ana Sayfa & Mesai Sayacı, Bütçe & Rapor Arşivi, Tarife & Eksik Kayıt Denetimi, Başarılar & Kilometre Taşları, Ayarlar) ve Bildirim Merkezi ekranı.
4. **`iosApp/project.yml` & Otomatik Bulut Derleme Yapılandırmaları**
   - **GitHub Actions (`.github/workflows/ios-release.yml`):** Mac bilgisayarınız olmasa bile projeyi GitHub'a aktardığınızda macOS sunucusunda Xcode projesini otomatik üretir ve derler.
   - **Codemagic (`iosApp/codemagic.yaml`):** Apple Developer hesabınızı bağlayarak doğrudan `.ipa` üretip TestFlight / App Store Connect'e göndermenizi sağlar.

## Nasıl Derlenir ve App Store'a Gönderilir?

### Yöntem 1: Mac Bilgisayarınız Varsa (Xcode ile)
1. AI Studio sağ üst menüsünden projeyi **ZIP olarak indirin** veya **GitHub'a aktarın**.
2. Terminal'de `iosApp` klasörüne gidin ve XcodeGen ile `.xcodeproj` dosyasını oluşturun:
   ```bash
   cd iosApp
   brew install xcodegen
   xcodegen generate
   open MesaiTakip.xcodeproj
   ```
3. Xcode içinde **Signing & Capabilities** sekmesinden Apple Developer takımınızı seçin ve **Product -> Archive** ile App Store Connect'e yükleyin.

### Yöntem 2: Mac Bilgisayarınız Yoksa (GitHub Actions veya Codemagic ile)
1. AI Studio'dan projeyi **GitHub'a aktarın**.
2. **Codemagic.io**'da ücretsiz hesap açıp GitHub deponuzu bağlayın; `iosApp/codemagic.yaml` dosyası otomatik algılanır ve Apple Developer sertifikanızla imzalı `.ipa` dosyasını doğrudan App Store Connect / TestFlight'a gönderir.
