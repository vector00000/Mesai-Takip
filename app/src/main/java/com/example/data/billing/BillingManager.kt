package com.example.data.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Abonelik durumunu ayrıntılı olarak temsil eden durum sınıfı.
 * Gerçek ödeme, iptal (mevcut dönem devam eden), süre dolumu ve hata durumları birbirinden ayrıdır.
 */
enum class SubscriptionStatus {
    LOADING,
    ACTIVE_AUTO_RENEWING,
    CANCELED_ACTIVE_UNTIL_EXPIRY,
    IN_GRACE_PERIOD,
    PAYMENT_FAILED_ON_HOLD,
    PENDING,
    EXPIRED,
    INACTIVE,
    ERROR
}

/**
 * Satın alma doğrulama katmanı durumu.
 */
enum class VerificationState {
    UNVERIFIED,
    VERIFYING,
    PLAY_STORE_VERIFIED,
    SERVER_VERIFIED,
    REJECTED_INVALID_OR_REPLAY
}

/**
 * Aktif abonelik bilgilerini içeren veri modeli.
 */
data class ActiveSubscriptionInfo(
    val productId: String,
    val basePlanId: String,
    val purchaseToken: String,
    val orderId: String?,
    val purchaseTimeMillis: Long,
    val expiryTimeMillis: Long,
    val isAutoRenewing: Boolean,
    val isAcknowledged: Boolean,
    val verificationState: VerificationState
)

/**
 * Sunucu tarafı doğrulama yanıt modeli.
 */
data class ServerVerificationResult(
    val httpStatus: Int,
    val success: Boolean,
    val serverVerificationConfigured: Boolean,
    val verifiedByGooglePlayApi: Boolean,
    val isEntitled: Boolean,
    val subscriptionState: String?,
    val statusReason: String?,
    val expiryTimeMillis: Long = 0L,
    val autoRenewEnabled: Boolean = false,
    val errorCode: String? = null,
    val message: String? = null
)

/**
 * Mesai Takip Premium — Google Play Billing (v8.0.0) Abonelik Yöneticisi.
 *
 * Play Console Yapılandırması:
 * - Paket Adı: com.aistudio.mesaitakip.kvnrpd
 * - Subscription Product ID: mesai_takip_premium
 * - Base Plan ID: monthly (Aylık, otomatik yenilenen)
 */
class BillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"

        const val EXPECTED_PACKAGE_NAME = "com.aistudio.mesaitakip.kvnrpd"
        const val PREMIUM_SUBSCRIPTION_ID = "mesai_takip_premium"
        const val PREMIUM_BASE_PLAN_ID = "monthly"

        private const val PREFS_NAME = "mesai_takip_verified_receipt_store"
        private const val KEY_RECEIPT_ORIGINAL_JSON = "receipt_original_json"
        private const val KEY_RECEIPT_SIGNATURE = "receipt_signature"
        private const val KEY_RECEIPT_EXPIRY_MS = "receipt_expiry_ms"
        private const val KEY_RECEIPT_VERIFIED_AT_MS = "receipt_verified_at_ms"
        private const val KEY_RECEIPT_SERVER_VERIFIED = "receipt_server_verified"

        // Çevrimdışı durumda imzalı makbuzun geçerlilik süresi (48 saat)
        const val OFFLINE_RECEIPT_MAX_AGE_MS = 48L * 60L * 60L * 1000L
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Aynı satın alma işleminin tekrar işlenmesini ve mükerrer acknowledge çağrılarını önleyen kümeler
    private val inFlightTokens = ConcurrentHashMap.newKeySet<String>()
    private val processedPurchaseKeys = ConcurrentHashMap.newKeySet<String>()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _subscriptionStatus = MutableStateFlow(SubscriptionStatus.LOADING)
    val subscriptionStatus: StateFlow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()

    private val _verificationState = MutableStateFlow(VerificationState.UNVERIFIED)
    val verificationState: StateFlow<VerificationState> = _verificationState.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    // Fiyat uygulama içinde sabit kodlanmaz; Google Play'in döndürdüğü bölgesel fiyat tutulur.
    private val _formattedPrice = MutableStateFlow<String?>(null)
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    private val _billingPeriodText = MutableStateFlow("Aylık (Otomatik Yenilenir)")
    val billingPeriodText: StateFlow<String> = _billingPeriodText.asStateFlow()

    private val _activeSubscriptionInfo = MutableStateFlow<ActiveSubscriptionInfo?>(null)
    val activeSubscriptionInfo: StateFlow<ActiveSubscriptionInfo?> = _activeSubscriptionInfo.asStateFlow()

    private val _billingMessage = MutableStateFlow<String?>(null)
    val billingMessage: StateFlow<String?> = _billingMessage.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private var reconnectDelayMs = 1000L
    private val maxReconnectDelayMs = 30000L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    init {
        // Başlangıçta yalnızca imzalı ve süresi geçmemiş makbuz varsa geçici çevrimdışı durumu yükle,
        // ardından hemen Google Play Billing ile gerçek durumu sorgula.
        restoreFromVerifiedOfflineReceiptIfValid()
        startConnection()
    }

    /**
     * Google Play Billing servisine bağlantıyı güvenli biçimde başlatır.
     */
    fun startConnection(onConnected: (() -> Unit)? = null) {
        if (billingClient.isReady) {
            _isConnecting.value = false
            refreshBillingData()
            onConnected?.invoke()
            return
        }

        if (_isConnecting.value) return
        _isConnecting.value = true

        try {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    _isConnecting.value = false
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        reconnectDelayMs = 1000L
                        Log.d(TAG, "Google Play Billing bağlantısı kuruldu.")
                        refreshBillingData()
                        onConnected?.invoke()
                    } else {
                        handleConnectionFailure(billingResult)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _isConnecting.value = false
                    Log.w(TAG, "Google Play Billing bağlantısı kesildi, yeniden bağlanılacak...")
                    scheduleReconnect()
                }
            })
        } catch (e: Exception) {
            _isConnecting.value = false
            Log.e(TAG, "BillingClient bağlantı hatası", e)
            if (!_isPremium.value) {
                _subscriptionStatus.value = SubscriptionStatus.ERROR
            }
        }
    }

    fun handleConnectionFailure(billingResult: BillingResult) {
        Log.w(TAG, "Billing kurulum hatası (${billingResult.responseCode}): ${billingResult.debugMessage}")
        // Çevrimdışı geçerli makbuz yoksa durumu güncelle
        if (!restoreFromVerifiedOfflineReceiptIfValid()) {
            _isPremium.value = false
            _subscriptionStatus.value = when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                BillingClient.BillingResponseCode.NETWORK_ERROR -> SubscriptionStatus.ERROR
                else -> SubscriptionStatus.INACTIVE
            }
        }
    }

    private fun scheduleReconnect() {
        coroutineScope.launch {
            delay(reconnectDelayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(maxReconnectDelayMs)
            startConnection()
        }
    }

    /**
     * Hem ürün/fiyat bilgilerini (`mesai_takip_premium` / `monthly`) hem de mevcut satın almaları sorgular.
     */
    fun refreshBillingData() {
        if (!billingClient.isReady) {
            startConnection()
            return
        }
        querySubscriptionProductDetails()
        queryActivePurchases()
    }

    /**
     * Google Play'den `mesai_takip_premium` ürününü ve `monthly` temel planını sorgular.
     * Fiyatı sabit kodlamaz; Google Play'in döndürdüğü gerçek bölgesel fiyatı (`formattedPrice`) alır.
     */
    fun querySubscriptionProductDetails(onLoaded: ((ProductDetails?) -> Unit)? = null) {
        if (!billingClient.isReady) {
            onLoaded?.invoke(null)
            return
        }

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_SUBSCRIPTION_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            val productDetailsList = queryProductDetailsResult.productDetailsList
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val details = productDetailsList.firstOrNull { it.productId == PREMIUM_SUBSCRIPTION_ID }
                    ?: productDetailsList.firstOrNull()
                applyProductDetails(details)
                onLoaded?.invoke(details)
            } else {
                Log.w(TAG, "Ürün sorgulama başarısız (${billingResult.responseCode}): ${billingResult.debugMessage}")
                onLoaded?.invoke(null)
            }
        }
    }

    /**
     * `ProductDetails` içinden `monthly` temel planını ve bölgesel fiyat bilgisini ayrıştırır.
     */
    fun applyProductDetails(details: ProductDetails?) {
        _productDetails.value = details
        if (details == null) return

        val selectedOffer = details.subscriptionOfferDetails?.firstOrNull {
            it.basePlanId == PREMIUM_BASE_PLAN_ID
        } ?: details.subscriptionOfferDetails?.firstOrNull()

        val pricingPhase = selectedOffer
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull { it.priceAmountMicros > 0 }
            ?: selectedOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()

        if (pricingPhase != null && pricingPhase.formattedPrice.isNotBlank()) {
            _formattedPrice.value = pricingPhase.formattedPrice
            _billingPeriodText.value = when (pricingPhase.billingPeriod) {
                "P1M" -> "Aylık (Otomatik Yenilenir)"
                "P1Y" -> "Yıllık (Otomatik Yenilenir)"
                "P1W" -> "Haftalık (Otomatik Yenilenir)"
                else -> "Aylık (Otomatik Yenilenir)"
            }
        }
    }

    /**
     * Kullanıcının mevcut aboneliklerini Google Play'den sorgular ve geri yükler.
     */
    fun queryActivePurchases(showFeedbackMessage: Boolean = false) {
        if (!billingClient.isReady) {
            if (showFeedbackMessage) {
                _billingMessage.value = "Google Play bağlantısı kuruluyor, lütfen internet bağlantınızı kontrol edin."
            }
            startConnection()
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchasesList(purchasesList, showFeedbackMessage = showFeedbackMessage)
            } else {
                Log.w(TAG, "Abonelik geçmişi sorgulanamadı (${billingResult.responseCode}): ${billingResult.debugMessage}")
                if (showFeedbackMessage) {
                    _billingMessage.value = mapBillingResponseToTurkishMessage(billingResult.responseCode)
                }
            }
        }
    }

    /**
     * Satın alma işlemini yalnızca resmi Google Play Billing akışı üzerinden başlatır.
     */
    fun launchSubscriptionFlow(activity: Activity, selectedOfferToken: String? = null) {
        if (!billingClient.isReady) {
            _billingMessage.value = "Google Play Store bağlantısı kuruluyor, lütfen birkaç saniye sonra tekrar deneyin."
            startConnection {
                querySubscriptionProductDetails { details ->
                    mainHandler.post {
                        if (details != null) {
                            launchBillingFlowInternal(activity, details, selectedOfferToken)
                        } else {
                            _billingMessage.value =
                                "Abonelik bilgisi şu anda Google Play'den alınamadı. Lütfen internet bağlantınızı ve Google Play hesabınızı kontrol edin."
                        }
                    }
                }
            }
            return
        }

        val currentDetails = _productDetails.value
        if (currentDetails != null) {
            launchBillingFlowInternal(activity, currentDetails, selectedOfferToken)
        } else {
            querySubscriptionProductDetails { fetchedDetails ->
                mainHandler.post {
                    if (fetchedDetails != null) {
                        launchBillingFlowInternal(activity, fetchedDetails, selectedOfferToken)
                    } else {
                        _billingMessage.value =
                            "Mesai Takip Premium ($PREMIUM_SUBSCRIPTION_ID) ürün bilgisi Google Play'den alınamadı. Lütfen Google Play hesabınızın açık olduğundan emin olun."
                    }
                }
            }
        }
    }

    private fun launchBillingFlowInternal(
        activity: Activity,
        details: ProductDetails,
        selectedOfferToken: String?
    ): Boolean {
        val offerDetails = details.subscriptionOfferDetails
        val matchingOffer = offerDetails?.firstOrNull { it.basePlanId == PREMIUM_BASE_PLAN_ID }
            ?: offerDetails?.firstOrNull()
        val offerToken = selectedOfferToken ?: matchingOffer?.offerToken

        if (offerToken.isNullOrBlank()) {
            _billingMessage.value = "Bu abonelik için aktif bir temel plan ($PREMIUM_BASE_PLAN_ID) bulunamadı."
            return false
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingMessage.value = mapBillingResponseToTurkishMessage(result.responseCode)
            return false
        }
        return true
    }

    /**
     * Google Play satın alma sonucu callback'i.
     */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        handleBillingCallback(billingResult.responseCode, billingResult.debugMessage, purchases)
    }

    /**
     * Billing yanıt kodlarını ve satın alma listesini işler (Test edilebilir giriş noktası).
     */
    fun handleBillingCallback(
        responseCode: Int,
        debugMessage: String = "",
        purchases: List<Purchase>?
    ) {
        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    processPurchasesList(purchases, showFeedbackMessage = true)
                } else {
                    queryActivePurchases(showFeedbackMessage = false)
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // Kullanıcı satın alma ekranını kapattığında uygulama normal çalışmaya devam eder
                _billingMessage.value = "Satın alma işlemi iptal edildi."
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _billingMessage.value = "Zaten aktif bir Mesai Takip Premium aboneliğiniz bulunuyor. Erişiminiz geri yükleniyor..."
                queryActivePurchases(showFeedbackMessage = false)
            }

            else -> {
                Log.w(TAG, "Satın alma hatası ($responseCode): $debugMessage")
                _billingMessage.value = mapBillingResponseToTurkishMessage(responseCode)
            }
        }
    }

    /**
     * Google Play Billing hata kodlarını anlaşılır Türkçe mesajlara dönüştürür.
     */
    fun mapBillingResponseToTurkishMessage(responseCode: Int): String {
        return when (responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED ->
                "Satın alma işlemi iptal edildi."
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                "Ağ veya Google Play bağlantı hatası oluştu. Lütfen internet bağlantınızı kontrol edip tekrar deneyin."
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "Google Play faturalandırma servisi bu cihazda kullanılamıyor veya ödeme yöntemi tanımlı değil."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "Mesai Takip Premium aboneliği şu anda bölgenizde satın alınamıyor."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                "Bu abonelik zaten hesabınızda aktif görünüyor."
            BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                "Uygulama imzası veya abonelik yapılandırması doğrulanamadı."
            else ->
                "Ödeme işlemi tamamlanamadı (Hata Kodu: $responseCode). Lütfen ödeme yönteminizi kontrol edin."
        }
    }

    /**
     * Gelen satın alma listesini doğrular, mükerrer işlemleri engeller,
     * onaylanmamış satın almaları onaylar (acknowledge) ve sunucu doğrulamasını çalıştırır.
     */
    fun processPurchasesList(
        purchases: List<Purchase>,
        showFeedbackMessage: Boolean = false
    ) {
        coroutineScope.launch {
            var matchedPurchase: Purchase? = null
            var hasPendingPurchase = false
            var rejectedInvalidPurchase = false

            for (purchase in purchases) {
                val isTargetProduct = purchase.products.contains(PREMIUM_SUBSCRIPTION_ID)
                if (!isTargetProduct) continue

                if (!isPurchaseStructurallyValid(purchase)) {
                    Log.e(TAG, "Geçersiz paket adı veya eksik token tespit edildi!")
                    rejectedInvalidPurchase = true
                    continue
                }

                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> {
                        matchedPurchase = purchase
                    }
                    Purchase.PurchaseState.PENDING -> {
                        hasPendingPurchase = true
                    }
                }
            }

            if (matchedPurchase != null) {
                val purchase = matchedPurchase
                val dedupeKey = "${purchase.purchaseToken}_${purchase.purchaseState}_${purchase.isAcknowledged}"

                // Aynı satın alma işleminin mükerrer acknowledge edilmesini engelle
                if (!purchase.isAcknowledged && inFlightTokens.add(purchase.purchaseToken)) {
                    acknowledgePurchaseIfNeeded(purchase)
                }

                val isFirstTimeProcessing = processedPurchaseKeys.add(dedupeKey)

                // Sunucu tarafı doğrulama isteği gönder
                _verificationState.value = VerificationState.VERIFYING
                val serverResult = verifySubscriptionWithBackend(purchase)

                withContext(Dispatchers.Main) {
                    applyVerifiedPurchaseState(
                        purchase = purchase,
                        serverResult = serverResult,
                        showFeedbackMessage = showFeedbackMessage && isFirstTimeProcessing
                    )
                }
            } else {
                withContext(Dispatchers.Main) {
                    when {
                        rejectedInvalidPurchase -> {
                            _isPremium.value = false
                            _subscriptionStatus.value = SubscriptionStatus.INACTIVE
                            _verificationState.value = VerificationState.REJECTED_INVALID_OR_REPLAY
                            _activeSubscriptionInfo.value = null
                            clearVerifiedOfflineReceipt()
                            if (showFeedbackMessage) {
                                _billingMessage.value = "Geçersiz satın alma bilgisi reddedildi."
                            }
                        }

                        hasPendingPurchase -> {
                            // Gerçek ödeme tamamlanmadan Premium açılmaz!
                            _isPremium.value = false
                            _subscriptionStatus.value = SubscriptionStatus.PENDING
                            _verificationState.value = VerificationState.UNVERIFIED
                            _activeSubscriptionInfo.value = null
                            if (showFeedbackMessage) {
                                _billingMessage.value =
                                    "Ödemeniz onay bekliyor. Ödeme tamamlandığında Mesai Takip Premium otomatik olarak açılacaktır."
                            }
                        }

                        else -> {
                            // Aktif bir abonelik dönemi kalmamış (süresi dolmuş veya hiç alınmamış)
                            val hadPreviousReceipt = prefs.contains(KEY_RECEIPT_ORIGINAL_JSON)
                            _isPremium.value = false
                            _subscriptionStatus.value = if (hadPreviousReceipt) {
                                SubscriptionStatus.EXPIRED
                            } else {
                                SubscriptionStatus.INACTIVE
                            }
                            _verificationState.value = VerificationState.UNVERIFIED
                            _activeSubscriptionInfo.value = null
                            clearVerifiedOfflineReceipt()
                            if (showFeedbackMessage) {
                                _billingMessage.value = if (hadPreviousReceipt) {
                                    "Premium abonelik süreniz sona ermiş."
                                } else {
                                    "Bu Google Play hesabına tanımlı aktif bir abonelik bulunamadı."
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Hem Google Play satın alma nesnesini hem de sunucu doğrulama sonucunu birlikte değerlendirir.
     *
     * ÖNEMLİ KURAL:
     * - Kullanıcı aboneliği iptal etmiş (`isAutoRenewing == false` veya `CANCELED_ACTIVE_UNTIL_EXPIRY`)
     *   olsa bile, ödenmiş mevcut dönem sona ermeden Premium erişimi KALDIRILMAZ!
     * - Token başka bir hesaba kayıtlıysa (`TOKEN_BOUND_TO_DIFFERENT_ACCOUNT`) veya sunucu aboneliğin
     *   süresinin dolduğunu / askıya alındığını (`EXPIRED`, `PAYMENT_FAILED_ON_HOLD`) doğruladıysa
     *   Premium erişimi reddedilir.
     */
    fun applyVerifiedPurchaseState(
        purchase: Purchase,
        serverResult: ServerVerificationResult?,
        showFeedbackMessage: Boolean = false
    ) {
        // 1. Sunucu açıkça reddettiyse (farklı hesapta kullanılan token, geçersiz imza veya süresi dolmuş/askıda)
        if (serverResult != null && (
                    serverResult.errorCode == "TOKEN_BOUND_TO_DIFFERENT_ACCOUNT" ||
                    serverResult.errorCode == "INVALID_SIGNATURE" ||
                    serverResult.errorCode == "INVALID_PACKAGE_NAME" ||
                    serverResult.errorCode == "INVALID_PRODUCT_ID"
                    )
        ) {
            _isPremium.value = false
            _subscriptionStatus.value = SubscriptionStatus.INACTIVE
            _verificationState.value = VerificationState.REJECTED_INVALID_OR_REPLAY
            _activeSubscriptionInfo.value = null
            clearVerifiedOfflineReceipt()
            _billingMessage.value = serverResult.message
                ?: "Satın alma doğrulaması güvenlik kontrolünden geçemedi."
            return
        }

        // 2. Sunucu Google Play Developer API ile doğrulama yaptıysa ve abonelik askıda/süresi dolmuşsa:
        if (serverResult != null && serverResult.verifiedByGooglePlayApi && !serverResult.isEntitled) {
            _isPremium.value = false
            _subscriptionStatus.value = when (serverResult.statusReason) {
                "PAYMENT_FAILED_ON_HOLD" -> SubscriptionStatus.PAYMENT_FAILED_ON_HOLD
                else -> SubscriptionStatus.EXPIRED
            }
            _verificationState.value = VerificationState.SERVER_VERIFIED
            _activeSubscriptionInfo.value = null
            clearVerifiedOfflineReceipt()
            if (showFeedbackMessage || serverResult.statusReason == "PAYMENT_FAILED_ON_HOLD") {
                _billingMessage.value = when (serverResult.statusReason) {
                    "PAYMENT_FAILED_ON_HOLD" ->
                        "Abonelik yenileme ödemesi başarısız oldu. Lütfen Google Play'de ödeme yönteminizi güncelleyin."
                    else ->
                        "Premium abonelik süreniz sona ermiştir."
                }
            }
            return
        }

        // 3. Geçerli ve ödenmiş abonelik (Google Play PURCHASED + varsa sunucu onayı)
        val isAutoRenewing = if (serverResult != null && serverResult.verifiedByGooglePlayApi) {
            serverResult.autoRenewEnabled
        } else {
            purchase.isAutoRenewing
        }

        val resolvedVerificationState = if (serverResult != null && serverResult.verifiedByGooglePlayApi && serverResult.isEntitled) {
            VerificationState.SERVER_VERIFIED
        } else {
            VerificationState.PLAY_STORE_VERIFIED
        }

        val resolvedStatus = when {
            serverResult?.statusReason == "IN_GRACE_PERIOD" ->
                SubscriptionStatus.IN_GRACE_PERIOD
            !isAutoRenewing ->
                // Abonelik iptal edilmiş ancak mevcut ödenmiş dönem devam ediyor!
                SubscriptionStatus.CANCELED_ACTIVE_UNTIL_EXPIRY
            else ->
                SubscriptionStatus.ACTIVE_AUTO_RENEWING
        }

        val expiryMs = if (serverResult != null && serverResult.expiryTimeMillis > 0L) {
            serverResult.expiryTimeMillis
        } else {
            0L
        }

        val info = ActiveSubscriptionInfo(
            productId = PREMIUM_SUBSCRIPTION_ID,
            basePlanId = PREMIUM_BASE_PLAN_ID,
            purchaseToken = purchase.purchaseToken,
            orderId = purchase.orderId,
            purchaseTimeMillis = purchase.purchaseTime,
            expiryTimeMillis = expiryMs,
            isAutoRenewing = isAutoRenewing,
            isAcknowledged = purchase.isAcknowledged,
            verificationState = resolvedVerificationState
        )

        _isPremium.value = true
        _subscriptionStatus.value = resolvedStatus
        _verificationState.value = resolvedVerificationState
        _activeSubscriptionInfo.value = info

        saveVerifiedOfflineReceipt(
            originalJson = purchase.originalJson,
            signature = purchase.signature,
            expiryTimeMillis = expiryMs,
            serverVerified = resolvedVerificationState == VerificationState.SERVER_VERIFIED
        )

        if (showFeedbackMessage) {
            _billingMessage.value = if (isAutoRenewing) {
                "Mesai Takip Premium aboneliğiniz aktif!"
            } else {
                "Aboneliğiniz iptal edilmiş; mevcut ödenmiş dönem sonuna kadar Premium erişiminiz devam ediyor."
            }
        }
    }

    /**
     * Başarılı satın almayı Google Play kurallarına uygun şekilde onaylar (acknowledge).
     */
    private fun acknowledgePurchaseIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            inFlightTokens.remove(purchase.purchaseToken)
            return
        }

        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
            inFlightTokens.remove(purchase.purchaseToken)
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Mesai Takip Premium satın alımı onaylandı (acknowledged).")
                _activeSubscriptionInfo.value = _activeSubscriptionInfo.value?.copy(isAcknowledged = true)
            } else {
                Log.w(TAG, "Satın alma onaylama hatası (${billingResult.responseCode}): ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Backend `/api/billing/verify-subscription` uç noktası üzerinden satın alma token'ını doğrular.
     */
    private suspend fun verifySubscriptionWithBackend(purchase: Purchase): ServerVerificationResult? =
        withContext(Dispatchers.IO) {
            val baseUrl = try {
                com.example.BuildConfig.BACKEND_BASE_URL.trim().removeSuffix("/")
            } catch (_: Exception) {
                ""
            }
            if (baseUrl.isBlank()) return@withContext null

            val deviceAccountId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
            } catch (_: Exception) {
                ""
            }

            val payloadJson = JSONObject().apply {
                put("packageName", purchase.packageName)
                put("productId", purchase.products.firstOrNull() ?: PREMIUM_SUBSCRIPTION_ID)
                put("purchaseToken", purchase.purchaseToken)
                put("accountId", deviceAccountId)
                put("originalJson", purchase.originalJson)
                put("signature", purchase.signature)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/billing/verify-subscription")
                .post(payloadJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (raw.isBlank()) return@withContext null
                    val json = JSONObject(raw)
                    ServerVerificationResult(
                        httpStatus = response.code,
                        success = json.optBoolean("success", false),
                        serverVerificationConfigured = json.optBoolean("serverVerificationConfigured", false),
                        verifiedByGooglePlayApi = json.optBoolean("verifiedByGooglePlayApi", false),
                        isEntitled = json.optBoolean("isEntitled", false),
                        subscriptionState = json.optString("subscriptionState", null),
                        statusReason = json.optString("statusReason", null),
                        expiryTimeMillis = json.optLong("expiryTimeMillis", 0L),
                        autoRenewEnabled = json.optBoolean("autoRenewEnabled", purchase.isAutoRenewing),
                        errorCode = json.optString("errorCode", null).takeIf { !it.isNullOrBlank() },
                        message = json.optString("message", null).takeIf { !it.isNullOrBlank() }
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sunucu doğrulama isteği ulaşamadı (çevrimdışı veya ağ hatası): ${e.message}")
                null
            }
        }

    /**
     * Satın almanın paket adını (`com.aistudio.mesaitakip.kvnrpd`), ürün kimliğini (`mesai_takip_premium`),
     * satın alma durumunu ve token bütünlüğünü doğrular.
     */
    fun isPurchaseStructurallyValid(
        purchase: Purchase,
        base64PublicKey: String? = null
    ): Boolean {
        if (purchase.packageName != EXPECTED_PACKAGE_NAME) {
            return false
        }
        if (!purchase.products.contains(PREMIUM_SUBSCRIPTION_ID)) {
            return false
        }
        if (purchase.purchaseToken.isBlank() || purchase.originalJson.isBlank()) {
            return false
        }
        if (!base64PublicKey.isNullOrBlank()) {
            return verifyRsaSignature(base64PublicKey, purchase.originalJson, purchase.signature)
        }
        return true
    }

    /**
     * Google Play RSA açık anahtarı ile imza doğrulaması yapar.
     */
    fun verifyRsaSignature(
        base64PublicKey: String,
        signedData: String,
        signature: String
    ): Boolean {
        if (signedData.isBlank() || signature.isBlank() || base64PublicKey.isBlank()) {
            return false
        }
        return try {
            val decodedKey = Base64.decode(base64PublicKey, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))

            val sig = Signature.getInstance("SHA1withRSA")
            sig.initVerify(publicKey)
            sig.update(signedData.toByteArray())
            sig.verify(Base64.decode(signature, Base64.DEFAULT))
        } catch (e: Exception) {
            Log.e(TAG, "RSA imza doğrulama hatası", e)
            false
        }
    }

    /**
     * Kullanıcının Google Play üzerindeki aboneliğini yönetebileceği / iptal edebileceği
     * resmi Google Play abonelik yönetim sayfasını açar.
     */
    fun openPlayStoreSubscriptionManagement(context: Context) {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                    "?sku=$PREMIUM_SUBSCRIPTION_ID&package=$EXPECTED_PACKAGE_NAME"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Play Store abonelik sayfası açılamadı", e)
        }
    }

    fun clearBillingMessage() {
        _billingMessage.value = null
    }

    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    /**
     * Yalnızca basit bir boolean değere güvenmez!
     * Çevrimdışı durumda saklanan Google Play makbuz JSON'unun (`originalJson`) paket adını,
     * ürün kimliğini (`mesai_takip_premium`), `purchaseState` değerini ve süre sınırını doğrular.
     */
    fun restoreFromVerifiedOfflineReceiptIfValid(nowMs: Long = System.currentTimeMillis()): Boolean {
        val originalJson = prefs.getString(KEY_RECEIPT_ORIGINAL_JSON, null) ?: return false
        val verifiedAtMs = prefs.getLong(KEY_RECEIPT_VERIFIED_AT_MS, 0L)
        val expiryMs = prefs.getLong(KEY_RECEIPT_EXPIRY_MS, 0L)
        val serverVerified = prefs.getBoolean(KEY_RECEIPT_SERVER_VERIFIED, false)

        if (nowMs - verifiedAtMs !in 0..OFFLINE_RECEIPT_MAX_AGE_MS) {
            return false
        }
        if (expiryMs in 1..nowMs) {
            // Süresi dolmuş abonelik
            clearVerifiedOfflineReceipt()
            return false
        }

        return try {
            val json = JSONObject(originalJson)
            val pkg = json.optString("packageName")
            val prod = json.optString("productId")
            val token = json.optString("purchaseToken")
            val state = json.optInt("purchaseState", -1) // Google Play JSON'da 0 = Purchased
            val autoRenewing = json.optBoolean("autoRenewing", true)

            if (pkg == EXPECTED_PACKAGE_NAME &&
                prod == PREMIUM_SUBSCRIPTION_ID &&
                token.length >= 10 &&
                state == 0
            ) {
                _isPremium.value = true
                _subscriptionStatus.value = if (autoRenewing) {
                    SubscriptionStatus.ACTIVE_AUTO_RENEWING
                } else {
                    SubscriptionStatus.CANCELED_ACTIVE_UNTIL_EXPIRY
                }
                _verificationState.value = if (serverVerified) {
                    VerificationState.SERVER_VERIFIED
                } else {
                    VerificationState.PLAY_STORE_VERIFIED
                }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun saveVerifiedOfflineReceipt(
        originalJson: String,
        signature: String,
        expiryTimeMillis: Long,
        serverVerified: Boolean
    ) {
        prefs.edit()
            .putString(KEY_RECEIPT_ORIGINAL_JSON, originalJson)
            .putString(KEY_RECEIPT_SIGNATURE, signature)
            .putLong(KEY_RECEIPT_EXPIRY_MS, expiryTimeMillis)
            .putLong(KEY_RECEIPT_VERIFIED_AT_MS, System.currentTimeMillis())
            .putBoolean(KEY_RECEIPT_SERVER_VERIFIED, serverVerified)
            .apply()
    }

    fun clearVerifiedOfflineReceipt() {
        prefs.edit()
            .remove(KEY_RECEIPT_ORIGINAL_JSON)
            .remove(KEY_RECEIPT_SIGNATURE)
            .remove(KEY_RECEIPT_EXPIRY_MS)
            .remove(KEY_RECEIPT_VERIFIED_AT_MS)
            .remove(KEY_RECEIPT_SERVER_VERIFIED)
            .apply()
    }

    /**
     * Mevcut cihaz/kullanıcı için kalıcı ve izole kimlik (ownerAccountId) döndürür.
     * Belge arşivinde her kullanıcının yalnızca kendi belgelerine erişebilmesi için kullanılır.
     */
    fun getCurrentUserAccountId(): String {
        val existing = prefs.getString("owner_account_id", null)
        if (!existing.isNullOrBlank()) return existing

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        val resolvedId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            "acct_$androidId"
        } else {
            "acct_${java.util.UUID.randomUUID().toString().replace("-", "")}"
        }
        prefs.edit().putString("owner_account_id", resolvedId).apply()
        return resolvedId
    }

    /**
     * Premium erişiminin yalnızca yerel bir boolean değişkene değil,
     * Google Play Billing / sunucu tarafından doğrulanmış aktif abonelik durumuna
     * dayandığını kontrol eder.
     */
    fun hasVerifiedPremiumEntitlement(): Boolean {
        val status = _subscriptionStatus.value
        val verification = _verificationState.value
        val validStatus = status == SubscriptionStatus.ACTIVE_AUTO_RENEWING ||
            status == SubscriptionStatus.CANCELED_ACTIVE_UNTIL_EXPIRY ||
            status == SubscriptionStatus.IN_GRACE_PERIOD
        val validVerification = verification == VerificationState.PLAY_STORE_VERIFIED ||
            verification == VerificationState.SERVER_VERIFIED
        return _isPremium.value && validStatus && validVerification
    }
}
