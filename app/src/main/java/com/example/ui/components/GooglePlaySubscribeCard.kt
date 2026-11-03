package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MesaiTakipApp
import com.example.data.billing.BillingManager
import com.example.data.billing.SubscriptionStatus
import com.example.ui.theme.MoneyEmerald

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Ana ekranda üst bölümde sabit (sticky) olarak yer alan 3D derinlikli, akan LED ışıklı (neon sweep border),
 * açık temalı kurumsal Premium banner.
 * Fiyatı sabit kodlamaz; yalnızca Google Play Billing'den alınan gerçek `formattedPrice` değerini gösterir.
 * Dokunulduğunda Premium ayrıntı ve satın alma ekranını (`GooglePlaySubscriptionDialog`) açar.
 */
@Composable
fun CorporatePremiumBanner(
    modifier: Modifier = Modifier,
    overrideIsPremium: Boolean? = null,
    overrideFormattedPrice: String? = null,
    overrideBillingPeriodText: String? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MesaiTakipApp
    val billingManager: BillingManager? = app?.billingManager

    val billingIsPremium by billingManager?.isPremium?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val subscriptionStatus by billingManager?.subscriptionStatus?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(SubscriptionStatus.INACTIVE) }
    val billingFormattedPrice by billingManager?.formattedPrice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<String?>(null) }
    val billingPeriodState by billingManager?.billingPeriodText?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf("Aylık (Otomatik Yenilenir)") }
    val billingMessage by billingManager?.billingMessage?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<String?>(null) }

    val isPremium = overrideIsPremium ?: billingIsPremium
    val formattedPrice = overrideFormattedPrice ?: billingFormattedPrice
    val billingPeriodText = overrideBillingPeriodText ?: billingPeriodState

    var showDetailDialog by remember { mutableStateOf(false) }

    LaunchedEffect(billingMessage) {
        val msg = billingMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            billingManager?.clearBillingMessage()
        }
    }

    if (showDetailDialog) {
        GooglePlaySubscriptionDialog(
            isPremium = isPremium,
            subscriptionStatus = subscriptionStatus,
            formattedPrice = formattedPrice,
            billingPeriodText = billingPeriodText,
            featureContextTitle = null,
            onDismiss = { showDetailDialog = false },
            onSubscribeClick = {
                val activity = context.findActivity()
                if (activity != null && billingManager != null) {
                    billingManager.launchSubscriptionFlow(activity)
                } else {
                    Toast.makeText(
                        context,
                        "Satın alma akışı yalnızca uygulama ekranı üzerinden başlatılabilir.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onManageSubscription = {
                billingManager?.openPlayStoreSubscriptionManagement(context)
            },
            onRestorePurchases = {
                billingManager?.queryActivePurchases(showFeedbackMessage = true)
            }
        )
    }

    val bulletItems = remember {
        listOf(
            "Gelişmiş fazla mesai raporları",
            "Son 6 ay ve haftalık trend analizi",
            "PDF ve Excel/CSV dışa aktarma",
            "Özel tema ve görünüm seçenekleri"
        )
    }

    // 360° Akan LED Işık Animasyonu
    val infiniteTransition = rememberInfiniteTransition(label = "led_border_transition")
    val ledAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "led_rotation_angle"
    )
    val ledGlowPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "led_glow_pulse"
    )

    val ledColors = if (isPremium) {
        listOf(
            Color(0xFF10B981),
            Color(0xFF34D399),
            Color(0xFFF59E0B),
            Color(0xFF06B6D4),
            Color(0xFF10B981)
        )
    } else {
        listOf(
            Color(0xFF2563EB), // Kurumsal Elektrik Mavisi
            Color(0xFF06B6D4), // Parlak Turkuaz LED
            Color(0xFFF59E0B), // Altın/Kehribar LED Kıvılcımı
            Color(0xFF6366F1), // İndigo Neon
            Color(0xFF38BDF8), // Buz Mavisi LED
            Color(0xFF2563EB)
        )
    }

    val outerShape = RoundedCornerShape(18.dp)
    val innerShape = RoundedCornerShape(15.5.dp)

    // Dış 3D Taban + LED Çerçeve Katmanı
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 3.dp)
            .shadow(
                elevation = 10.dp,
                shape = outerShape,
                ambientColor = if (isPremium) Color(0xFF10B981) else Color(0xFF2563EB),
                spotColor = if (isPremium) Color(0xFF059669) else Color(0xFF0284C7)
            )
            .clip(outerShape)
            .clickable { showDetailDialog = true }
            .testTag("corporate_premium_banner")
    ) {
        // 3D Alt Derinlik Dudak Katmanı (3D Extruded Bottom Lip)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = if (isPremium) {
                            listOf(Color(0xFF047857), Color(0xFF0D9488), Color(0xFF047857))
                        } else {
                            listOf(Color(0xFF1E3A8A), Color(0xFF0369A1), Color(0xFF312E81))
                        }
                    )
                )
        )

        // Dönen LED Işık Çerçevesi (Sweep Gradient Neon Tube)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.5.dp)
                .clip(outerShape)
                .drawWithContent {
                    val sweepBrush = Brush.sweepGradient(colors = ledColors)
                    rotate(degrees = ledAngle) {
                        val radius = size.maxDimension * 1.35f
                        drawCircle(
                            brush = sweepBrush,
                            radius = radius,
                            alpha = ledGlowPulse
                        )
                    }
                    drawContent()
                }
                .padding(2.6.dp) // LED şerit kalınlığı
        ) {
            // İç 3D Kabartmalı Kurumsal Açık Tema Kart Gövdesi
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(innerShape)
                    .background(
                        Brush.verticalGradient(
                            colors = if (isPremium) {
                                listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFFF0FDF4),
                                    Color(0xFFDCFCE7)
                                )
                            } else {
                                listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFFF5F9FF),
                                    Color(0xFFE6F0FF)
                                )
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFFBFDBFE).copy(alpha = 0.6f)
                            )
                        ),
                        shape = innerShape
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Üst Satır: 3D İkon Rozeti, Başlık & Alt Başlık, 3D Aksiyon Butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // 3D Küp Rozet İkonu
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    spotColor = if (isPremium) Color(0xFF10B981) else Color(0xFF2563EB)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isPremium) {
                                            listOf(Color(0xFF10B981), Color(0xFF047857))
                                        } else {
                                            listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                                        },
                                        start = Offset(0f, 0f),
                                        end = Offset(100f, 100f)
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.7f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = "Mesai Takip Premium",
                                tint = Color(0xFFFDE047), // Parlak Altın İkon
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Mesai Takip Premium",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.testTag("banner_title_premium")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "Mesai raporlarında daha fazlası",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPremium) Color(0xFF047857) else Color(0xFF1D4ED8),
                                modifier = Modifier.testTag("banner_subtitle_premium")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (isPremium) {
                        // 3D Premium Aktif Rozeti
                        Box(
                            modifier = Modifier
                                .shadow(4.dp, RoundedCornerShape(10.dp), spotColor = MoneyEmerald)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .testTag("badge_premium_active")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Premium Aktif",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        // 3D Kabartmalı Parlak CTA Butonu
                        Box(
                            modifier = Modifier
                                .shadow(
                                    elevation = 5.dp,
                                    shape = RoundedCornerShape(10.dp),
                                    spotColor = Color(0xFF1D4ED8)
                                )
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E3A8A)) // 3D alt kenar gölgesi
                                .padding(bottom = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF2563EB),
                                            Color(0xFF0284C7)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.65f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .testTag("badge_premium_cta")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val badgeText = if (!formattedPrice.isNullOrBlank()) {
                                    "$formattedPrice / Ay"
                                } else {
                                    "İncele"
                                }
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color(0xFFFDE047),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4 Özellik Maddesi (3D Kabartmalı Mini-Kartlar, Metin Kesilmesi Olmadan)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWideScreen = maxWidth >= 560.dp
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            bulletItems.forEach { itemText ->
                                BannerBulletItem(
                                    text = itemText,
                                    isPremium = isPremium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BannerBulletItem(
                                    text = bulletItems[0],
                                    isPremium = isPremium,
                                    modifier = Modifier.weight(1f)
                                )
                                BannerBulletItem(
                                    text = bulletItems[1],
                                    isPremium = isPremium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BannerBulletItem(
                                    text = bulletItems[2],
                                    isPremium = isPremium,
                                    modifier = Modifier.weight(1f)
                                )
                                BannerBulletItem(
                                    text = bulletItems[3],
                                    isPremium = isPremium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerBulletItem(
    text: String,
    isPremium: Boolean,
    modifier: Modifier = Modifier
) {
    val chipShape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .shadow(elevation = 1.5.dp, shape = chipShape, spotColor = Color(0xFF94A3B8))
            .clip(chipShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White,
                        if (isPremium) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (isPremium) Color(0xFF86EFAC) else Color(0xFFBFDBFE),
                shape = chipShape
            )
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Parlak LED Noktası
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(
                    if (isPremium) Color(0xFF10B981).copy(alpha = 0.18f)
                    else Color(0xFF2563EB).copy(alpha = 0.15f)
                )
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isPremium) Color(0xFF10B981) else Color(0xFF2563EB))
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1E293B),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Raporlar ve diğer ekranlarda kullanılan "Mesai Takip Premium" kartı.
 * Fiyatı sabit kodlamaz; Google Play'den alınan bölgesel fiyatı (`formattedPrice`) gösterir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GooglePlaySubscribeCard(
    modifier: Modifier = Modifier,
    featureContextTitle: String? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MesaiTakipApp
    val billingManager: BillingManager? = app?.billingManager

    val isPremium by billingManager?.isPremium?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val subscriptionStatus by billingManager?.subscriptionStatus?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(SubscriptionStatus.INACTIVE) }
    val formattedPrice by billingManager?.formattedPrice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<String?>(null) }
    val billingPeriodText by billingManager?.billingPeriodText?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf("Aylık (Otomatik Yenilenir)") }
    val billingMessage by billingManager?.billingMessage?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<String?>(null) }

    var showDetailDialog by remember { mutableStateOf(false) }

    LaunchedEffect(billingMessage) {
        val msg = billingMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            billingManager?.clearBillingMessage()
        }
    }

    if (showDetailDialog) {
        GooglePlaySubscriptionDialog(
            isPremium = isPremium,
            subscriptionStatus = subscriptionStatus,
            formattedPrice = formattedPrice,
            billingPeriodText = billingPeriodText,
            featureContextTitle = featureContextTitle,
            onDismiss = { showDetailDialog = false },
            onSubscribeClick = {
                val activity = context.findActivity()
                if (activity != null && billingManager != null) {
                    billingManager.launchSubscriptionFlow(activity)
                } else {
                    Toast.makeText(
                        context,
                        "Satın alma akışı yalnızca uygulama ekranı üzerinden başlatılabilir.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onManageSubscription = {
                billingManager?.openPlayStoreSubscriptionManagement(context)
            },
            onRestorePurchases = {
                billingManager?.queryActivePurchases(showFeedbackMessage = true)
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDetailDialog = true }
            .testTag("card_google_play_subscribe"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isPremium) MoneyEmerald.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Üst Başlık ve Google Play'den Alınan Dinamik Fiyat / Premium Aktif Rozeti
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (isPremium) MoneyEmerald else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = "Mesai Takip Premium",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Mesai Takip Premium",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Mesai raporlarında daha fazlası • $billingPeriodText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (isPremium) {
                    Surface(
                        color = MoneyEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MoneyEmerald.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "Premium Aktif",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MoneyEmerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                } else if (!formattedPrice.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "$formattedPrice / Ay",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // İptal edilmiş ancak mevcut ödenmiş dönemi devam eden abonelik bilgilendirmesi
            if (subscriptionStatus == SubscriptionStatus.CANCELED_ACTIVE_UNTIL_EXPIRY) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Otomatik yenileme kapatıldı. Ödenmiş mevcut döneminiz sona erene kadar tüm Premium özellikleriniz aktiftir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Ödeme başarısızlığı / askıda durumu uyarısı
            if (subscriptionStatus == SubscriptionStatus.PAYMENT_FAILED_ON_HOLD) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Abonelik yenileme ödemesi başarısız oldu. Erişiminizi sürdürmek için Google Play'de ödeme yönteminizi güncelleyin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ödeme Yöntemi Etiketleri
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Telefon Faturası (Mobil Ödeme)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreditCard,
                            contentDescription = null,
                            tint = MoneyEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tüm Banka & Kredi Kartları",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isPremium) {
                OutlinedButton(
                    onClick = { billingManager?.openPlayStoreSubscriptionManagement(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_manage_google_play_sub"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MoneyEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Premium Aktif • Google Play'de Yönet",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = { showDetailDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_google_play_subscribe"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shop,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val priceLabel = if (!formattedPrice.isNullOrBlank()) " ($formattedPrice/Ay)" else ""
                    Text(
                        text = "Mesai Takip Premium'a Abone Ol$priceLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Mesai Takip Premium Ayrıntı ve Satın Alma Ekranı (Dialog).
 * Ücretsiz kullanıcılar Premium banner'ına veya Premium gerektiren bir özelliğe dokunduğunda açılır.
 */
@Composable
fun GooglePlaySubscriptionDialog(
    isPremium: Boolean,
    subscriptionStatus: SubscriptionStatus = SubscriptionStatus.INACTIVE,
    formattedPrice: String?,
    billingPeriodText: String = "Aylık (Otomatik Yenilenir)",
    featureContextTitle: String? = null,
    onDismiss: () -> Unit,
    onSubscribeClick: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestorePurchases: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                color = if (isPremium) MoneyEmerald.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = "Mesai Takip Premium",
                        tint = if (isPremium) MoneyEmerald else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Mesai Takip Premium",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("premium_dialog_title")
                )
                Text(
                    text = "Mesai raporlarında daha fazlası",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (isPremium) {
                    Surface(
                        color = MoneyEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MoneyEmerald.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("dialog_badge_premium_active")
                    ) {
                        Text(
                            text = "✓ Premium Aktif",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MoneyEmerald,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    val priceSummary = if (!formattedPrice.isNullOrBlank()) {
                        "$formattedPrice / Ay • $billingPeriodText"
                    } else {
                        "$billingPeriodText • Fiyat Google Play'den alınıyor"
                    }
                    Text(
                        text = priceSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("premium_dialog_price_summary")
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!featureContextTitle.isNullOrBlank() && !isPremium) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🔒 \"$featureContextTitle\" özelliği Mesai Takip Premium abonelerine özeldir.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Kurumsal Premium Avantaj Listesi
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Premium Avantajları:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "✓ Gelişmiş fazla mesai raporları",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "✓ Son 6 ay ve haftalık trend analizi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "✓ PDF ve Excel/CSV dışa aktarma",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "✓ Özel tema ve görünüm seçenekleri",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "✓ Maaş Bordrosu ve Belge Arşivi (PDF ve Fotoğraf Belgelerini Yıl/Ay Bazında Güvenli Saklama)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "✓ Kişisel Başarılar ve İstatistikler (Aylık Hedefler, Yıllık Grafikler, Karşılaştırma ve Rozetler)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Geçerli Ödeme Yöntemleri (Yalnızca Google Play Billing)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Google Play Güvenli Ödeme Seçenekleri:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "• Telefon Faturası (Turkcell, Vodafone, Türk Telekom Mobil Ödeme)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Tüm Banka ve Kredi Kartları (Visa, Mastercard, Troy)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Google Play Bakiyesi ve Hediye Kodları",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Satın Alma, Aylık Otomatik Yenileme ve İptal Koşulları Bilgilendirmesi
                Text(
                    text = "Abonelik ve İptal Koşulları: Bu abonelik aylık olarak faturalandırılır ve iptal edilmediği sürece her fatura döneminde Google Play hesabınız üzerinden otomatik olarak yenilenir. Aboneliğinizi dilediğiniz zaman Google Play > Abonelikler sayfasından iptal edebilirsiniz. İptal ettiğinizde mevcut ödenmiş dönem sona erene kadar Premium erişiminiz kesintisiz devam eder.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    modifier = Modifier.testTag("premium_dialog_cancellation_terms")
                )

                HorizontalDivider()

                // Satın Alımları Geri Yükle & Abonelik Yönetimi Bağlantıları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onRestorePurchases,
                        modifier = Modifier.testTag("btn_restore_purchases")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Satın Alımları Geri Yükle", fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = onManageSubscription,
                        modifier = Modifier.testTag("btn_manage_subscription")
                    ) {
                        Text("Abonelik Yönetimi", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (!isPremium) {
                Button(
                    onClick = onSubscribeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_dialog_subscribe_confirm"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val btnLabel = if (!formattedPrice.isNullOrBlank()) {
                        "Google Play ile Abone Ol ($formattedPrice / Ay)"
                    } else {
                        "Google Play ile Abone Ol"
                    }
                    Text(
                        text = btnLabel,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onManageSubscription,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_dialog_manage_confirm"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MoneyEmerald
                    )
                ) {
                    Text("Premium Aktif • Google Play'de Yönet", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_dialog_dismiss")
            ) {
                Text("Kapat")
            }
        }
    )
}
