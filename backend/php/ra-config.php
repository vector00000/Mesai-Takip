<?php
/**
 * RA App Studio - Güvenli Yapılandırma Dosyası
 * Dosya Konumu: /home/raappbpf/ra-config.php (public_html DIŞINDA olmalıdır)
 *
 * Bu dosya web üzerinden doğrudan erişilemeyen güvenli sunucu kök dizininde saklanır.
 * API anahtarları asla istemciye veya HTML/JS içine iletilmez.
 */

// Google Gemini API Anahtarı
if (!defined('GEMINI_API_KEY')) {
    define('GEMINI_API_KEY', 'BURAYA_GEMINI_API_KEY_YAZILACAK');
}

// İsteğe bağlı değişken formatı
$GEMINI_API_KEY = GEMINI_API_KEY;
