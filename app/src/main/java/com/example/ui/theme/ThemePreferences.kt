package com.example.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Uygulamanın mevcut açık ve ferah tasarımını koruyan Özel Görünüm / Tema Renk Seçenekleri.
 * Klasik Mavi ücretsiz varsayılandır; diğer özel kurumsal renk paletleri Mesai Takip Premium abonelerine özeldir.
 */
enum class AppThemePalette(
    val id: Int,
    val title: String,
    val requiresPremium: Boolean,
    val primaryColor: Color,
    val primaryContainerColor: Color,
    val onPrimaryContainerColor: Color,
    val secondaryColor: Color
) {
    CLASSIC_BLUE(
        id = 0,
        title = "Klasik Mavi (Varsayılan)",
        requiresPremium = false,
        primaryColor = Color(0xFF2563EB),
        primaryContainerColor = Color(0xFFEFF6FF),
        onPrimaryContainerColor = Color(0xFF1E40AF),
        secondaryColor = Color(0xFF0284C7)
    ),
    EMERALD_PRO(
        id = 1,
        title = "Zümrüt Yeşili",
        requiresPremium = true,
        primaryColor = Color(0xFF059669),
        primaryContainerColor = Color(0xFFECFDF5),
        onPrimaryContainerColor = Color(0xFF065F46),
        secondaryColor = Color(0xFF0D9488)
    ),
    ROYAL_INDIGO(
        id = 2,
        title = "Kurumsal İndigo",
        requiresPremium = true,
        primaryColor = Color(0xFF4338CA),
        primaryContainerColor = Color(0xFFEEF2FF),
        onPrimaryContainerColor = Color(0xFF312E81),
        secondaryColor = Color(0xFF6366F1)
    ),
    OCEAN_TEAL(
        id = 3,
        title = "Okyanus Turkuaz",
        requiresPremium = true,
        primaryColor = Color(0xFF0D9488),
        primaryContainerColor = Color(0xFFF0FDFA),
        onPrimaryContainerColor = Color(0xFF115E59),
        secondaryColor = Color(0xFF0284C7)
    ),
    AMBER_GOLD(
        id = 4,
        title = "Kehribar Altın",
        requiresPremium = true,
        primaryColor = Color(0xFFD97706),
        primaryContainerColor = Color(0xFFFFFBEB),
        onPrimaryContainerColor = Color(0xFF92400E),
        secondaryColor = Color(0xFFEA580C)
    );

    companion object {
        fun fromId(id: Int): AppThemePalette =
            entries.firstOrNull { it.id == id } ?: CLASSIC_BLUE
    }
}

object ThemePreferences {
    private const val PREFS_NAME = "mesai_takip_theme_prefs"
    private const val KEY_PALETTE_ID = "selected_palette_id"

    private val _selectedPalette = MutableStateFlow(AppThemePalette.CLASSIC_BLUE)
    val selectedPalette: StateFlow<AppThemePalette> = _selectedPalette.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getInt(KEY_PALETTE_ID, AppThemePalette.CLASSIC_BLUE.id)
        _selectedPalette.value = AppThemePalette.fromId(savedId)
    }

    fun selectPalette(context: Context, palette: AppThemePalette, isPremium: Boolean): Boolean {
        if (palette.requiresPremium && !isPremium) {
            return false
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_PALETTE_ID, palette.id).apply()
        _selectedPalette.value = palette
        return true
    }

    fun getEffectivePalette(isPremium: Boolean): AppThemePalette {
        val current = _selectedPalette.value
        return if (current.requiresPremium && !isPremium) {
            AppThemePalette.CLASSIC_BLUE
        } else {
            current
        }
    }
}
