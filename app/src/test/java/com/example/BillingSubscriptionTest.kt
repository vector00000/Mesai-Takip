package com.example

import android.content.Context
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.example.data.billing.BillingManager
import com.example.data.billing.ServerVerificationResult
import com.example.data.billing.SubscriptionStatus
import com.example.data.billing.VerificationState
import com.example.ui.components.CorporatePremiumBanner
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillingSubscriptionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var billingManager: BillingManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        billingManager = BillingManager(context)
        billingManager.clearVerifiedOfflineReceipt()
        ThemePreferences.selectPalette(context, AppThemePalette.CLASSIC_BLUE, isPremium = false)
    }

    private fun createPurchase(
        packageName: String = BillingManager.EXPECTED_PACKAGE_NAME,
        productId: String = BillingManager.PREMIUM_SUBSCRIPTION_ID,
        purchaseToken: String = "valid_purchase_token_1234567890",
        purchaseState: Int = 0, // Google Play JSON: 0 = PURCHASED, 4 = PENDING
        autoRenewing: Boolean = true,
        acknowledged: Boolean = false,
        signature: String = "dummy_sig"
    ): Purchase {
        val json = """
            {
                "orderId": "GPA.3312-4455-6677-88990",
                "packageName": "$packageName",
                "productId": "$productId",
                "purchaseTime": ${System.currentTimeMillis()},
                "purchaseState": $purchaseState,
                "purchaseToken": "$purchaseToken",
                "autoRenewing": $autoRenewing,
                "acknowledged": $acknowledged
            }
        """.trimIndent()
        return Purchase(json, signature)
    }

    // 1. Ürün kimliği, temel plan ve sabit kodlanmamış fiyat kontrolü
    @Test
    fun `1_product query constants and unhardcoded initial price state`() {
        assertEquals("com.aistudio.mesaitakip.kvnrpd", BillingManager.EXPECTED_PACKAGE_NAME)
        assertEquals("mesai_takip_premium", BillingManager.PREMIUM_SUBSCRIPTION_ID)
        assertEquals("monthly", BillingManager.PREMIUM_BASE_PLAN_ID)
        // Google Play'den sorgulanmadan önce sabit kodlanmış fiyat bulunmamalıdır
        assertNull(billingManager.formattedPrice.value)
        assertEquals("Aylık (Otomatik Yenilenir)", billingManager.billingPeriodText.value)
    }

    // 2. Billing bağlantı hatası
    @Test
    fun `2_billing connection error sets ERROR status and keeps isPremium false`() {
        val unavailableResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
            .setDebugMessage("Play Store service unavailable")
            .build()

        billingManager.handleConnectionFailure(unavailableResult)

        assertFalse(billingManager.isPremium.value)
        assertEquals(SubscriptionStatus.ERROR, billingManager.subscriptionStatus.value)
    }

    // 3. Kullanıcının satın alma işlemini iptal etmesi
    @Test
    fun `3_user canceled purchase keeps app working and does not grant premium`() {
        billingManager.handleBillingCallback(
            responseCode = BillingClient.BillingResponseCode.USER_CANCELED,
            debugMessage = "User pressed back",
            purchases = null
        )

        assertFalse(billingManager.isPremium.value)
        assertEquals("Satın alma işlemi iptal edildi.", billingManager.billingMessage.value)
    }

    // 4. Başarılı satın alma
    @Test
    fun `4_successful purchase activates premium and sets ACTIVE_AUTO_RENEWING`() {
        val purchase = createPurchase(autoRenewing = true, acknowledged = true)
        val serverResult = ServerVerificationResult(
            httpStatus = 200,
            success = true,
            serverVerificationConfigured = true,
            verifiedByGooglePlayApi = true,
            isEntitled = true,
            subscriptionState = "SUBSCRIPTION_STATE_ACTIVE",
            statusReason = "ACTIVE",
            expiryTimeMillis = System.currentTimeMillis() + 30L * 24 * 3600 * 1000,
            autoRenewEnabled = true
        )

        billingManager.applyVerifiedPurchaseState(
            purchase = purchase,
            serverResult = serverResult,
            showFeedbackMessage = true
        )

        assertTrue(billingManager.isPremium.value)
        assertEquals(SubscriptionStatus.ACTIVE_AUTO_RENEWING, billingManager.subscriptionStatus.value)
        assertEquals(VerificationState.SERVER_VERIFIED, billingManager.verificationState.value)
        assertNotNull(billingManager.activeSubscriptionInfo.value)
    }

    // 5. Satın alımların geri yüklenmesi
    @Test
    fun `5_restore purchases restores active subscription from valid Play receipt`() {
        val purchase = createPurchase(autoRenewing = true, acknowledged = true)
        billingManager.applyVerifiedPurchaseState(
            purchase = purchase,
            serverResult = null,
            showFeedbackMessage = false
        )
        assertTrue(billingManager.isPremium.value)

        // Yeni bir BillingManager örneği oluşturulduğunda imzalı makbuzdan geri yüklemeli
        val newManager = BillingManager(context)
        val restored = newManager.restoreFromVerifiedOfflineReceiptIfValid()
        assertTrue(restored)
        assertTrue(newManager.isPremium.value)
        assertEquals(SubscriptionStatus.ACTIVE_AUTO_RENEWING, newManager.subscriptionStatus.value)
    }

    // 6. Süresi dolmuş abonelik
    @Test
    fun `6_expired subscription revokes premium and sets EXPIRED status`() {
        val purchase = createPurchase(autoRenewing = false, acknowledged = true)
        val expiredServerResult = ServerVerificationResult(
            httpStatus = 200,
            success = true,
            serverVerificationConfigured = true,
            verifiedByGooglePlayApi = true,
            isEntitled = false,
            subscriptionState = "SUBSCRIPTION_STATE_EXPIRED",
            statusReason = "EXPIRED",
            expiryTimeMillis = System.currentTimeMillis() - 60_000L,
            autoRenewEnabled = false
        )

        billingManager.applyVerifiedPurchaseState(
            purchase = purchase,
            serverResult = expiredServerResult,
            showFeedbackMessage = true
        )

        assertFalse(billingManager.isPremium.value)
        assertEquals(SubscriptionStatus.EXPIRED, billingManager.subscriptionStatus.value)
        assertNull(billingManager.activeSubscriptionInfo.value)
    }

    // 7. İptal edilmiş ancak mevcut dönemi devam eden abonelik
    @Test
    fun `7_canceled subscription with remaining paid period keeps premium active until expiry`() {
        val canceledButActivePurchase = createPurchase(autoRenewing = false, acknowledged = true)
        val futureExpiry = System.currentTimeMillis() + 14L * 24 * 3600 * 1000 // 14 gün daha var
        val serverResult = ServerVerificationResult(
            httpStatus = 200,
            success = true,
            serverVerificationConfigured = true,
            verifiedByGooglePlayApi = true,
            isEntitled = true,
            subscriptionState = "SUBSCRIPTION_STATE_CANCELED",
            statusReason = "CANCELED_ACTIVE_UNTIL_EXPIRY",
            expiryTimeMillis = futureExpiry,
            autoRenewEnabled = false
        )

        billingManager.applyVerifiedPurchaseState(
            purchase = canceledButActivePurchase,
            serverResult = serverResult,
            showFeedbackMessage = true
        )

        // ÖNEMLİ KURAL: Abonelik iptal edildi diye, ödenmiş mevcut dönem sona ermeden Premium hemen kaldırılmaz!
        assertTrue(billingManager.isPremium.value)
        assertEquals(SubscriptionStatus.CANCELED_ACTIVE_UNTIL_EXPIRY, billingManager.subscriptionStatus.value)
        assertEquals(futureExpiry, billingManager.activeSubscriptionInfo.value?.expiryTimeMillis)
    }

    // 8. Ödeme başarısızlığı (Account Hold / On Hold)
    @Test
    fun `8_payment failure on renewal puts subscription on hold and revokes premium`() {
        val purchase = createPurchase(autoRenewing = true, acknowledged = true)
        val onHoldResult = ServerVerificationResult(
            httpStatus = 200,
            success = true,
            serverVerificationConfigured = true,
            verifiedByGooglePlayApi = true,
            isEntitled = false,
            subscriptionState = "SUBSCRIPTION_STATE_ON_HOLD",
            statusReason = "PAYMENT_FAILED_ON_HOLD",
            expiryTimeMillis = System.currentTimeMillis() - 1000L,
            autoRenewEnabled = true
        )

        billingManager.applyVerifiedPurchaseState(
            purchase = purchase,
            serverResult = onHoldResult,
            showFeedbackMessage = true
        )

        assertFalse(billingManager.isPremium.value)
        assertEquals(SubscriptionStatus.PAYMENT_FAILED_ON_HOLD, billingManager.subscriptionStatus.value)
        assertTrue(billingManager.billingMessage.value.orEmpty().contains("ödeme yönteminizi güncelleyin"))
    }

    // 9. Geçersiz veya başka hesapta kullanılan token & RSA imza doğrulaması
    @Test
    fun `9_invalid package product RSA signature or replay token is rejected`() {
        val wrongPackagePurchase = createPurchase(packageName = "com.fake.app")
        assertFalse(billingManager.isPurchaseStructurallyValid(wrongPackagePurchase))

        val wrongProductPurchase = createPurchase(productId = "wrong_sku")
        assertFalse(billingManager.isPurchaseStructurallyValid(wrongProductPurchase))

        // Başka hesapta kullanılan token (Replay saldırısı) sunucu tarafından reddedildiğinde:
        val validPurchase = createPurchase()
        val replayResult = ServerVerificationResult(
            httpStatus = 409,
            success = false,
            serverVerificationConfigured = true,
            verifiedByGooglePlayApi = false,
            isEntitled = false,
            subscriptionState = null,
            statusReason = null,
            errorCode = "TOKEN_BOUND_TO_DIFFERENT_ACCOUNT",
            message = "Bu satın alma anahtarı başka bir kullanıcı hesabıyla ilişkilendirilmiş."
        )

        billingManager.applyVerifiedPurchaseState(
            purchase = validPurchase,
            serverResult = replayResult,
            showFeedbackMessage = true
        )

        assertFalse(billingManager.isPremium.value)
        assertEquals(VerificationState.REJECTED_INVALID_OR_REPLAY, billingManager.verificationState.value)

        // Gerçek RSA İmza Doğrulaması testi
        val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val keyPair = kpg.generateKeyPair()
        val publicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT)

        val payload = """{"packageName":"com.aistudio.mesaitakip.kvnrpd","productId":"mesai_takip_premium"}"""
        val signer = Signature.getInstance("SHA1withRSA").apply {
            initSign(keyPair.private)
            update(payload.toByteArray())
        }
        val validSignatureBase64 = Base64.encodeToString(signer.sign(), Base64.DEFAULT)

        assertTrue(billingManager.verifyRsaSignature(publicKeyBase64, payload, validSignatureBase64))
        assertFalse(billingManager.verifyRsaSignature(publicKeyBase64, "$payload-tampered", validSignatureBase64))
    }

    // 10. Premium özellik erişim kontrolü (Özel tema kilit kontrolü)
    @Test
    fun `10_premium feature access control blocks premium themes for free users and allows for premium`() {
        // Ücretsiz kullanıcı Zümrüt Yeşili (Premium) temayı seçmeye çalıştığında reddedilir
        val freeAttempt = ThemePreferences.selectPalette(
            context = context,
            palette = AppThemePalette.EMERALD_PRO,
            isPremium = false
        )
        assertFalse(freeAttempt)
        assertEquals(AppThemePalette.CLASSIC_BLUE, ThemePreferences.getEffectivePalette(isPremium = false))

        // Premium kullanıcı Zümrüt Yeşili temayı seçtiğinde kabul edilir
        val premiumAttempt = ThemePreferences.selectPalette(
            context = context,
            palette = AppThemePalette.EMERALD_PRO,
            isPremium = true
        )
        assertTrue(premiumAttempt)
        assertEquals(AppThemePalette.EMERALD_PRO, ThemePreferences.getEffectivePalette(isPremium = true))

        // Abonelik sona ererse (isPremium = false), efektif tema otomatik olarak ücretsiz Klasik Mavi'ye döner
        assertEquals(AppThemePalette.CLASSIC_BLUE, ThemePreferences.getEffectivePalette(isPremium = false))
    }

    // 11. İnternet bağlantısı kesilmesi (Çevrimdışı makbuz geçerliliği ve süre aşımı)
    @Test
    fun `11_offline network disconnection preserves verified receipt within 48h and expires after`() {
        val now = System.currentTimeMillis()
        val purchase = createPurchase(autoRenewing = true, acknowledged = true)
        billingManager.applyVerifiedPurchaseState(
            purchase = purchase,
            serverResult = null,
            showFeedbackMessage = false
        )

        // İnternet kesildiğinde (SERVICE_UNAVAILABLE) 48 saat içinde imzalı makbuz korunur
        val networkError = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR)
            .setDebugMessage("No internet connection")
            .build()
        billingManager.handleConnectionFailure(networkError)
        assertTrue(billingManager.isPremium.value)

        // 48 saatten (OFFLINE_RECEIPT_MAX_AGE_MS) uzun süre çevrimdışı kalındığında makbuz geçersiz sayılır
        val after72Hours = now + BillingManager.OFFLINE_RECEIPT_MAX_AGE_MS + 3600_000L
        assertFalse(billingManager.restoreFromVerifiedOfflineReceiptIfValid(nowMs = after72Hours))
    }

    // 12. Kurumsal Premium Banner metinleri, dinamik fiyat gösterimi ve satın alma ekranı açılışı
    @Test
    fun `12_corporate premium banner displays exact features dynamic price and opens purchase dialog`() {
        composeTestRule.setContent {
            MyApplicationTheme(palette = AppThemePalette.CLASSIC_BLUE) {
                CorporatePremiumBanner(
                    overrideIsPremium = false,
                    overrideFormattedPrice = "₺250,00",
                    overrideBillingPeriodText = "Aylık (Otomatik Yenilenir)"
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("corporate_premium_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mesai Takip Premium").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mesai raporlarında daha fazlası").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gelişmiş fazla mesai raporları").assertIsDisplayed()
        composeTestRule.onNodeWithText("Son 6 ay ve haftalık trend analizi").assertIsDisplayed()
        composeTestRule.onNodeWithText("PDF ve Excel/CSV dışa aktarma").assertIsDisplayed()
        composeTestRule.onNodeWithText("Özel tema ve görünüm seçenekleri").assertIsDisplayed()
        composeTestRule.onNodeWithText("₺250,00 / Ay").assertIsDisplayed()

        // Banner'a dokunulduğunda Premium ayrıntı ve satın alma ekranı açılmalı
        composeTestRule.onNodeWithTag("corporate_premium_banner").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("premium_dialog_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("premium_dialog_price_summary").assertIsDisplayed()
        composeTestRule.onNodeWithTag("premium_dialog_cancellation_terms")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_dialog_subscribe_confirm").assertIsDisplayed()
    }

    // 13. Kullanıcı zaten Premium abonesiyse satın alma yerine "Premium Aktif" durumu gösterilir
    @Test
    fun `13_corporate premium banner shows Premium Aktif when user is already subscribed`() {
        composeTestRule.setContent {
            MyApplicationTheme(palette = AppThemePalette.CLASSIC_BLUE) {
                CorporatePremiumBanner(
                    overrideIsPremium = true,
                    overrideFormattedPrice = "₺250,00",
                    overrideBillingPeriodText = "Aylık (Otomatik Yenilenir)"
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("badge_premium_active", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Premium Aktif").assertIsDisplayed()

        composeTestRule.onNodeWithTag("corporate_premium_banner").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dialog_badge_premium_active").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_dialog_manage_confirm").assertIsDisplayed()
    }
}
