import SwiftUI

struct MainTabView: View {
    @EnvironmentObject var store: MesaiStore
    @State private var selectedTab: Int = 0
    @State private var showNotificationCenter: Bool = false

    var unreadCount: Int {
        store.notifications.filter { !$0.isRead }.count
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                IosHomeView(onOpenNotifications: { showNotificationCenter = true }, unreadCount: unreadCount)
            }
            .tabItem {
                Label("Ana Sayfa", systemImage: "clock.badge.checkmark")
            }
            .tag(0)

            NavigationStack {
                IosBudgetAndArchiveView()
            }
            .tabItem {
                Label("Bütçe & Rapor", systemImage: "chart.pie.fill")
            }
            .tag(1)

            NavigationStack {
                IosTariffAndAuditView()
            }
            .tabItem {
                Label("Tarife & Denetim", systemImage: "checkmark.shield.fill")
            }
            .tag(2)

            NavigationStack {
                IosAchievementsView()
            }
            .tabItem {
                Label("Başarılar", systemImage: "trophy.fill")
            }
            .tag(3)

            NavigationStack {
                IosSettingsView()
            }
            .tabItem {
                Label("Ayarlar", systemImage: "gearshape.fill")
            }
            .tag(4)
        }
        .tint(Color(red: 0.08, green: 0.36, blue: 0.75))
        .sheet(isPresented: $showNotificationCenter) {
            NavigationStack {
                IosNotificationCenterView(onSelectTargetTab: { tab in
                    showNotificationCenter = false
                    selectedTab = tab
                })
            }
        }
    }
}

// MARK: - 1. Ana Sayfa & Mesai Sayacı (Özellik 6)
struct IosHomeView: View {
    @EnvironmentObject var store: MesaiStore
    let onOpenNotifications: () -> Void
    let unreadCount: Int

    @State private var selectedType: OvertimeType = .weekday
    @State private var showManualSheet: Bool = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Aktif Mesai Sayacı Kartı
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Image(systemName: store.activeOvertime.isActive ? "timer" : "play.circle.fill")
                            .font(.title2)
                            .foregroundColor(.blue)
                        Text(store.activeOvertime.isActive ? "Devam Eden Mesai Sayacı" : "Tek Dokunuşla Mesai Sayacı")
                            .font(.headline)
                        Spacer()
                    }

                    if store.activeOvertime.isActive {
                        Text("Başlangıç: \(store.activeOvertime.startTimeStr) • Saatlik: ₺\(String(format: "%.0f", store.activeOvertime.hourlyRate))")
                            .font(.subheadline)
                            .foregroundColor(.secondary)

                        Stepper(
                            "Mola Süresi: \(store.activeOvertime.breakMinutes) dk",
                            value: Binding(
                                get: { store.activeOvertime.breakMinutes },
                                set: { store.updateActiveBreakMinutes($0) }
                            ),
                            in: 0...480,
                            step: 15
                        )
                        .font(.subheadline)

                        Button(role: .destructive) {
                            store.stopOvertime()
                        } label: {
                            Label("Mesaiyi Bitir ve Kaydet", systemImage: "stop.fill")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                    } else {
                        Picker("Mesai Türü", selection: $selectedType) {
                            ForEach(OvertimeType.allCases) { type in
                                Text(type.labelTr).tag(type)
                            }
                        }
                        .pickerStyle(.menu)

                        HStack(spacing: 12) {
                            Button {
                                store.startOvertime(type: selectedType)
                            } label: {
                                Label("Mesai Başlat", systemImage: "play.fill")
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.borderedProminent)

                            Button {
                                showManualSheet = true
                            } label: {
                                Label("Manuel Ekle", systemImage: "plus")
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }
                .padding()
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 16))

                // Aylık Özet Kartı
                let summary = store.calculateBudgetSummary(for: store.selectedYearMonth)
                VStack(alignment: .leading, spacing: 8) {
                    Text("\(store.selectedYearMonth) Dönemi Maaş & Mesai Özeti")
                        .font(.headline)
                    HStack {
                        Text("Sabit Maaş:")
                        Spacer()
                        Text("₺\(String(format: "%.2f", summary.baseSalaryEntitlement))").bold()
                    }
                    HStack {
                        Text("Mesai Kazancı:")
                        Spacer()
                        Text("+₺\(String(format: "%.2f", summary.overtimeEarningsEntitlement))")
                            .foregroundColor(.green).bold()
                    }
                    Divider()
                    HStack {
                        Text("Net Ödenecek (Avans Düşülmüş):")
                        Spacer()
                        Text("₺\(String(format: "%.2f", summary.netRemainingSalaryAfterAdvance))")
                            .font(.title3).bold().foregroundColor(.blue)
                    }
                }
                .padding()
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 16))

                // Son Mesai Kayıtları
                VStack(alignment: .leading, spacing: 10) {
                    Text("Son Mesai Kayıtları")
                        .font(.headline)
                    if store.records.isEmpty {
                        Text("Henüz mesai kaydı bulunmuyor.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(store.records.prefix(15)) { rec in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("\(rec.date) • \(rec.startTimeStr) - \(rec.endTimeStr)")
                                        .font(.subheadline).bold()
                                    Text("Net: \(rec.durationMinutes) dk (Mola: \(rec.breakMinutes) dk)")
                                        .font(.caption).foregroundColor(.secondary)
                                }
                                Spacer()
                                Text("₺\(String(format: "%.2f", rec.totalAmount))")
                                    .font(.subheadline).bold().foregroundColor(.blue)
                            }
                            .padding(12)
                            .background(Color(.systemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                    }
                }
            }
            .padding()
        }
        .navigationTitle("Mesai Takip")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: onOpenNotifications) {
                    HStack(spacing: 4) {
                        Image(systemName: "bell.badge.fill")
                        if unreadCount > 0 {
                            Text("\(unreadCount)")
                                .font(.caption2).bold()
                        }
                    }
                }
            }
        }
        .sheet(isPresented: $showManualSheet) {
            IosManualRecordSheet()
        }
    }
}

struct IosManualRecordSheet: View {
    @EnvironmentObject var store: MesaiStore
    @Environment(\.dismiss) var dismiss
    @State private var date: String = MesaiStore.todayDateString()
    @State private var startTime: String = "18:00"
    @State private var endTime: String = "21:00"
    @State private var breakMinutes: Int = 0
    @State private var type: OvertimeType = .weekday
    @State private var note: String = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Tarih (YYYY-AA-GG)", text: $date)
                TextField("Başlangıç Saati (SS:DD)", text: $startTime)
                TextField("Bitiş Saati (SS:DD)", text: $endTime)
                Stepper("Mola: \(breakMinutes) dk", value: $breakMinutes, in: 0...240, step: 15)
                Picker("Mesai Türü", selection: $type) {
                    ForEach(OvertimeType.allCases) { item in
                        Text(item.labelTr).tag(item)
                    }
                }
                TextField("Not", text: $note)
            }
            .navigationTitle("Manuel Mesai Ekle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("İptal") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        store.addManualRecord(
                            date: date,
                            startTimeStr: startTime,
                            endTimeStr: endTime,
                            breakMinutes: breakMinutes,
                            type: type,
                            customRate: nil,
                            note: note
                        )
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - 2. Gelir & Gider Bütçe Takibi ve Bordro Arşivi (Özellik 3 & 4)
struct IosBudgetAndArchiveView: View {
    @EnvironmentObject var store: MesaiStore
    @State private var title: String = ""
    @State private var amountText: String = ""
    @State private var category: BudgetCategory = .rent

    var body: some View {
        let summary = store.calculateBudgetSummary(for: store.selectedYearMonth)
        List {
            Section("Aylık Bütçe & Maaş Mutabakatı (\(store.selectedYearMonth))") {
                LabeledContent("Toplam Uzlaştırılmış Gelir", value: "₺\(String(format: "%.2f", summary.totalReconciledIncome))")
                LabeledContent("Alınan Maaş Avansı", value: "₺\(String(format: "%.2f", summary.advanceReceivedTotal))")
                LabeledContent("Toplam Giderler", value: "₺\(String(format: "%.2f", summary.totalExpense))")
                LabeledContent("Net Kalan Bakiye", value: "₺\(String(format: "%.2f", summary.netRemainingBalance))")
                    .bold()
            }

            Section("Yeni Gelir / Gider Kaydı Ekle") {
                TextField("Açıklama (Örn: Kira, Fatura, Avans)", text: $title)
                TextField("Tutar (TL)", text: $amountText)
                    .keyboardType(.decimalPad)
                Picker("Kategori", selection: $category) {
                    ForEach(BudgetCategory.allCases) { cat in
                        Text("\(cat.labelTr) (\(cat.transactionType.labelTr))").tag(cat)
                    }
                }
                Button("Bütçe Kaydını Ekle") {
                    if let amt = Double(amountText.replacingOccurrences(of: ",", with: ".")), amt > 0, !title.isEmpty {
                        store.budgetTransactions.insert(
                            BudgetTransaction(
                                title: title,
                                amount: amt,
                                type: category.transactionType,
                                category: category,
                                dateStr: MesaiStore.todayDateString(),
                                yearMonth: store.selectedYearMonth
                            ),
                            at: 0
                        )
                        title = ""
                        amountText = ""
                    }
                }
            }

            Section("Kişisel Maaş Programlama Raporu Arşivi") {
                Button {
                    store.createAndSaveMonthlyArchive(for: store.selectedYearMonth)
                } label: {
                    Label("\(store.selectedYearMonth) Raporunu Oluştur ve Arşivle", systemImage: "doc.badge.plus")
                }

                ForEach(store.archivedReports) { report in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(report.reportTitle).font(.subheadline).bold()
                        Text("Oluşturulma: \(report.createdDateStr) • Net Ödenecek: ₺\(String(format: "%.2f", report.netPayableTotal))")
                            .font(.caption).foregroundColor(.secondary)
                        Text("Dondurulmuş Saatlik Ücretler — H.İçi: ₺\(Int(report.snapshotWeekdayRate)), H.Sonu: ₺\(Int(report.snapshotWeekendRate))")
                            .font(.caption2).foregroundColor(.secondary)
                    }
                }
            }
        }
        .navigationTitle("Bütçe & Rapor Arşivi")
    }
}

// MARK: - 3. Çoklu Ücret Tarifesi & Eksik Kayıt Kontrolü (Özellik 5 & 7)
struct IosTariffAndAuditView: View {
    @EnvironmentObject var store: MesaiStore

    var body: some View {
        let warnings = store.auditRecords()
        List {
            Section("Akıllı Eksik Kayıt Kontrolü (\(warnings.count) Uyarı)") {
                if warnings.isEmpty {
                    Text("Tüm mesai kayıtlarınız eksiksiz ve tutarlı görünüyor.")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(warnings) { w in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(w.title).font(.subheadline).bold().foregroundColor(.orange)
                            Text(w.description).font(.caption)
                            Button("Uyarıyı Kapat") {
                                store.dismissWarning(w.warningKey)
                            }
                            .font(.caption)
                        }
                    }
                }
            }

            Section("Aktif Saatlik Ücret Tarifeleri") {
                LabeledContent("Normal Mesai", value: "₺\(String(format: "%.2f", store.settings.normalHourlyRate))")
                LabeledContent("Hafta İçi Fazla Mesai", value: "₺\(String(format: "%.2f", store.settings.weekdayRate))")
                LabeledContent("Hafta Sonu Mesaisi", value: "₺\(String(format: "%.2f", store.settings.weekendRate))")
                LabeledContent("Gece Vardiyası", value: "₺\(String(format: "%.2f", store.settings.nightShiftRate))")
                LabeledContent("Resmî Tatil Mesaisi", value: "₺\(String(format: "%.2f", store.settings.officialHolidayRate))")
            }
        }
        .navigationTitle("Tarife & Denetim")
    }
}

// MARK: - 4. Başarılar ve Kilometre Taşları (Özellik 9 / 68)
struct IosAchievementsView: View {
    @EnvironmentObject var store: MesaiStore

    var body: some View {
        let badges = store.calculateAchievements()
        List {
            Section("Kazanılan ve Devam Eden Başarılar") {
                ForEach(badges) { badge in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Image(systemName: badge.isUnlocked ? "checkmark.seal.fill" : "lock.circle")
                                .foregroundColor(badge.isUnlocked ? .green : .secondary)
                            Text(badge.title).font(.subheadline).bold()
                            Spacer()
                            Text("%\(badge.progressPercent)").font(.caption).bold()
                        }
                        Text(badge.conditionDescription)
                            .font(.caption)
                            .foregroundColor(.secondary)
                        ProgressView(value: Double(badge.currentProgress), total: Double(max(1, badge.targetProgress)))
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("Başarılar")
    }
}

// MARK: - 5. Bildirim Merkezi (Özellik 8 / 65)
struct IosNotificationCenterView: View {
    @EnvironmentObject var store: MesaiStore
    let onSelectTargetTab: (Int) -> Void

    var body: some View {
        List {
            if store.notifications.isEmpty {
                Text("Henüz bildirim kaydı bulunmuyor.")
                    .foregroundColor(.secondary)
            } else {
                ForEach(store.notifications) { item in
                    Button {
                        if let idx = store.notifications.firstIndex(where: { $0.id == item.id }) {
                            store.notifications[idx].isRead = true
                        }
                        onSelectTargetTab(item.targetTabIndex)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(item.type.badgeText)
                                    .font(.caption2).bold()
                                    .padding(.horizontal, 6).padding(.vertical, 2)
                                    .background(Color.blue.opacity(0.15))
                                    .clipShape(Capsule())
                                Spacer()
                                Text(item.dateTimeStr).font(.caption2).foregroundColor(.secondary)
                            }
                            Text(item.title)
                                .font(.subheadline)
                                .fontWeight(item.isRead ? .regular : .bold)
                            Text(item.message)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("Bildirim Merkezi")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Tümünü Okundu İşaretle") {
                    for idx in store.notifications.indices {
                        store.notifications[idx].isRead = true
                    }
                }
            }
        }
    }
}

// MARK: - 6. Ayarlar
struct IosSettingsView: View {
    @EnvironmentObject var store: MesaiStore
    @StateObject private var storeKit = StoreKitManager.shared

    var body: some View {
        Form {
            Section("Mesai Takip Premium (\(storeKit.displayPrice))") {
                LabeledContent("Durum", value: storeKit.isPremium ? "Premium Aktif" : "Standart Plan")
                Text(storeKit.statusMessage)
                    .font(.caption)
                    .foregroundColor(.secondary)
                if !storeKit.isPremium {
                    Button {
                        Task { await storeKit.purchasePremium() }
                    } label: {
                        Label("Mesai Takip Premium'a Geç (\(storeKit.displayPrice))", systemImage: "crown.fill")
                    }
                    .disabled(storeKit.isLoading)
                }
                Button {
                    Task { await storeKit.restorePurchases() }
                } label: {
                    Label("Satın Almaları Geri Yükle (Restore Purchases)", systemImage: "arrow.clockwise.circle")
                }
                .disabled(storeKit.isLoading)
            }

            Section("Maaş & Saatlik Ücret Tarifeleri (TL)") {
                HStack {
                    Text("Aylık Net Maaş")
                    Spacer()
                    TextField("Maaş", value: $store.settings.baseSalary, format: .number)
                        .multilineTextAlignment(.trailing)
                        .keyboardType(.decimalPad)
                }
                HStack {
                    Text("Normal Saatlik Ücret")
                    Spacer()
                    TextField("Normal", value: $store.settings.normalHourlyRate, format: .number)
                        .multilineTextAlignment(.trailing)
                        .keyboardType(.decimalPad)
                }
                HStack {
                    Text("Hafta İçi Mesai Ücreti")
                    Spacer()
                    TextField("Hafta İçi", value: $store.settings.weekdayRate, format: .number)
                        .multilineTextAlignment(.trailing)
                        .keyboardType(.decimalPad)
                }
                HStack {
                    Text("Hafta Sonu Mesai Ücreti")
                    Spacer()
                    TextField("Hafta Sonu", value: $store.settings.weekendRate, format: .number)
                        .multilineTextAlignment(.trailing)
                        .keyboardType(.decimalPad)
                }
                HStack {
                    Text("Gece Vardiyası Ücreti")
                    Spacer()
                    TextField("Gece", value: $store.settings.nightShiftRate, format: .number)
                        .multilineTextAlignment(.trailing)
                        .keyboardType(.decimalPad)
                }
                HStack {
                    Text("Resmî Tatil Ücreti")
                    Spacer()
                    TextField("Resmî Tatil", value: $store.settings.officialHolidayRate, format: .number)
                        .multilineTextAlignment(.trailing)
                        .keyboardType(.decimalPad)
                }
            }

            Section("Otomatik Hatırlatmalar") {
                Toggle("Bildirimleri Etkinleştir", isOn: $store.settings.notificationsEnabled)
                Toggle("Mesai Kaydı Hatırlatması", isOn: $store.settings.reminderOvertimeLogEnabled)
                Toggle("Maaş Günü Hatırlatması", isOn: $store.settings.reminderSalaryDayEnabled)
                Toggle("Vardiya Başlangıç Hatırlatması", isOn: $store.settings.reminderShiftStartEnabled)
                Toggle("Eksik Kayıt Uyarısı", isOn: $store.settings.reminderMissingRecordEnabled)
                Toggle("Başarı Bildirimleri", isOn: $store.settings.reminderAchievementEnabled)
            }

            Section("Firebase Bulut Bağlantısı (GoogleService-Info.plist)") {
                LabeledContent("Firebase Proje ID", value: store.firebaseConfig.projectId)
                LabeledContent("Google App ID", value: store.firebaseConfig.googleAppId)
                LabeledContent("Depolama Kovası", value: store.firebaseConfig.storageBucket)
                LabeledContent("Durum", value: store.firebaseSyncMessage)
                Button {
                    store.syncToFirebaseCloud()
                } label: {
                    Label(
                        store.isSyncingFirebase ? "Yedekleniyor..." : "Firebase Buluta Şimdi Yedekle",
                        systemImage: "icloud.and.arrow.up.fill"
                    )
                }
                .disabled(store.isSyncingFirebase)
            }

            Section("Uygulama Bilgisi") {
                LabeledContent("Sürüm", value: "Mesai Takip iOS v31.0 (31)")
                LabeledContent("Paket / Bundle ID", value: store.firebaseConfig.bundleId)
                LabeledContent("Premium Ürün ID", value: StoreKitManager.premiumProductId)
            }
        }
        .navigationTitle("Ayarlar")
    }
}
