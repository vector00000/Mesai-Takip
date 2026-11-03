<?php
/**
 * RA App Studio - AI Proje Asistanı Backend Endpoint
 * Dosya Yolu: /home/raappbpf/public_html/ai-proje-asistani.php
 *
 * Özellikler:
 * 1. Google Gemini API (gemini-3.1-flash-lite-preview, gemini-3.8-flash, gemini-3.5-flash) ile doğrudan iletişim.
 * 2. API Key'i güvenli /home/raappbpf/ra-config.php dosyasından veya ortam değişkeninden okur.
 * 3. Asla PHP warning/notice basmaz, her zaman temiz ve geçerli JSON döndürür.
 * 4. cURL ve file_get_contents (stream context) desteği ile tüm barındırma ortamlarında çalışır.
 * 5. Fatal error / exception yakalama ile HTTP 500 çökmesi engellenmiştir.
 */

// PHP hata çıktılarını JSON'u bozmaması için gizle
ini_set('display_errors', '0');
error_reporting(0);

// Global Kapanış ve Hata Yakalayıcı (Her zaman geçerli JSON üretir)
register_shutdown_function(function () {
    $error = error_get_last();
    if ($error && in_array($error['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR])) {
        if (!headers_sent()) {
            header('Content-Type: application/json; charset=utf-8');
            http_response_code(200);
        }
        echo json_encode([
            'ok' => false,
            'success' => false,
            'error' => 'Kritik sistem hatası',
            'message' => 'AI servisi geçici olarak kullanılamıyor.'
        ], JSON_UNESCAPED_UNICODE);
    }
});

// HTTP Başlıkları ve CORS
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD');
header('Access-Control-Allow-Headers: Content-Type, Authorization, Accept, X-Requested-With');
header('Access-Control-Max-Age: 86400');

// OPTIONS preflight isteğini karşıla
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// GET isteği durumunda durum bilgisi dön
if ($_SERVER['REQUEST_METHOD'] === 'GET' || $_SERVER['REQUEST_METHOD'] === 'HEAD') {
    http_response_code(200);
    if ($_SERVER['REQUEST_METHOD'] !== 'HEAD') {
        echo json_encode([
            'ok' => true,
            'success' => true,
            'service' => 'RA App Studio AI Proje Asistanı',
            'status' => 'active',
            'description' => 'POST ile {"message": "..."} gönderiniz.'
        ], JSON_UNESCAPED_UNICODE);
    }
    exit;
}

// Sadece POST isteklerini kabul et
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode([
        'ok' => false,
        'success' => false,
        'error' => 'Yalnızca POST isteği kabul edilir.'
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

try {
    // Gelen JSON verisini oku
    $rawInput = file_get_contents('php://input');
    $data = json_decode($rawInput, true);
    $userMessage = trim($data['message'] ?? '');

    if (empty($userMessage)) {
        http_response_code(400);
        echo json_encode([
            'ok' => false,
            'success' => false,
            'error' => 'Geçerli bir proje mesajı gönderin.'
        ], JSON_UNESCAPED_UNICODE);
        exit;
    }

    // Güvenli Gemini API Anahtarı Yükleme
    $geminiApiKey = '';

    // 1. /home/raappbpf/ra-config.php dosyasını kontrol et (Önerilen güvenli yol)
    $secureConfigPath = '/home/raappbpf/ra-config.php';
    if (file_exists($secureConfigPath) && is_readable($secureConfigPath)) {
        @include_once $secureConfigPath;
    }

    // 2. Aynı dizindeki ra-config.php dosyasını kontrol et
    $localConfigPath = __DIR__ . '/ra-config.php';
    if (file_exists($localConfigPath) && is_readable($localConfigPath)) {
        @include_once $localConfigPath;
    }

    // 3. Tanımlı sabit veya değişkenleri oku
    if (defined('GEMINI_API_KEY') && !empty(constant('GEMINI_API_KEY'))) {
        $geminiApiKey = constant('GEMINI_API_KEY');
    } elseif (isset($GEMINI_API_KEY) && !empty($GEMINI_API_KEY)) {
        $geminiApiKey = $GEMINI_API_KEY;
    } elseif (isset($config['GEMINI_API_KEY']) && !empty($config['GEMINI_API_KEY'])) {
        $geminiApiKey = $config['GEMINI_API_KEY'];
    } else {
        // 4. Ortam değişkenlerini dene
        $envKey = getenv('GEMINI_API_KEY');
        if (!empty($envKey)) {
            $geminiApiKey = $envKey;
        } elseif (!empty($_ENV['GEMINI_API_KEY'])) {
            $geminiApiKey = $_ENV['GEMINI_API_KEY'];
        } elseif (!empty($_SERVER['GEMINI_API_KEY'])) {
            $geminiApiKey = $_SERVER['GEMINI_API_KEY'];
        }
    }

    // API Key kontrolü (placeholder kontrolü dahil)
    $isInvalidKey = empty($geminiApiKey) ||
                    strpos($geminiApiKey, 'BURAYA') !== false ||
                    strpos($geminiApiKey, 'YOUR_') !== false ||
                    strlen($geminiApiKey) < 10;

    if ($isInvalidKey) {
        http_response_code(200);
        echo json_encode([
            'ok' => false,
            'success' => false,
            'error' => 'AI servisi yapılandırılamadı (API anahtarı eksik).',
            'message' => 'Lütfen /home/raappbpf/ra-config.php dosyasında gerçek GEMINI_API_KEY anahtarınızı tanımlayınız.'
        ], JSON_UNESCAPED_UNICODE);
        exit;
    }

    // RA App Studio Sistem Yönergesi
    $systemPrompt = "Sen RA App Studio (raappstudio.online) Proje Asistanısın.\n"
        . "Kullanıcılara iOS ve Android mobil uygulama geliştirme, kurumsal web siteleri, yönetim panelleri ve özel yazılım projeleri hakkında bilgi veriyorsun.\n"
        . "Hizmet başlangıç fiyatları:\n"
        . "- Kurumsal Web Sitesi: 7.500 TL'den\n"
        . "- Mobil Uygulama: 25.000 TL'den\n"
        . "- Özel Yazılım: 40.000 TL'den\n"
        . "Kullanıcıya yardımcı, profesyonel, samimi ve Türkçe cevaplar ver. Net ve anlaşılır ol, gerektiğinde projeyi netleştirmek için sorular sor ve detaylı teklif için WhatsApp (+90 533 486 33 59) veya info@raappstudio.online adresine yönlendir.";

    // Google Gemini API İstek Gövdesi
    $requestPayload = [
        'contents' => [
            [
                'role' => 'user',
                'parts' => [
                    ['text' => $systemPrompt . "\n\nKullanıcı: " . $userMessage]
                ]
            ]
        ],
        'generationConfig' => [
            'temperature' => 0.7,
            'maxOutputTokens' => 800
        ]
    ];

    $payloadJson = json_encode($requestPayload);

    // Güncel ve yedekli Gemini model sıralaması
    $modelsToTry = [
        'gemini-3.1-flash-lite-preview',
        'gemini-flash-latest',
        'gemini-3.8-flash',
        'gemini-3.5-flash'
    ];

    $aiReply = '';
    $lastError = 'AI yanıt üretemedi.';

    foreach ($modelsToTry as $model) {
        $endpoint = "https://generativelanguage.googleapis.com/v1beta/models/{$model}:generateContent?key=" . urlencode($geminiApiKey);

        $response = null;
        $httpCode = 0;

        // 1. Önce cURL dene (varsa)
        if (function_exists('curl_init')) {
            $ch = curl_init($endpoint);
            curl_setopt_array($ch, [
                CURLOPT_POST => true,
                CURLOPT_POSTFIELDS => $payloadJson,
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_HTTPHEADER => [
                    'Content-Type: application/json',
                    'Content-Length: ' . strlen($payloadJson)
                ],
                CURLOPT_TIMEOUT => 20,
                CURLOPT_CONNECTTIMEOUT => 8,
                CURLOPT_SSL_VERIFYPEER => true
            ]);

            $response = curl_exec($ch);
            $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
            $curlErr = curl_error($ch);
            curl_close($ch);

            // SSL sertifika hatası durumunda bir kez güvenli fallback dene
            if ($curlErr && strpos($curlErr, 'certificate') !== false) {
                $ch2 = curl_init($endpoint);
                curl_setopt_array($ch2, [
                    CURLOPT_POST => true,
                    CURLOPT_POSTFIELDS => $payloadJson,
                    CURLOPT_RETURNTRANSFER => true,
                    CURLOPT_HTTPHEADER => [
                        'Content-Type: application/json',
                        'Content-Length: ' . strlen($payloadJson)
                    ],
                    CURLOPT_TIMEOUT => 20,
                    CURLOPT_CONNECTTIMEOUT => 8,
                    CURLOPT_SSL_VERIFYPEER => false
                ]);
                $response = curl_exec($ch2);
                $httpCode = curl_getinfo($ch2, CURLINFO_HTTP_CODE);
                curl_close($ch2);
            }
        }

        // 2. cURL çalışmadıysa stream context (file_get_contents) ile dene
        if (empty($response) && ini_get('allow_url_fopen')) {
            $context = stream_context_create([
                'http' => [
                    'method' => 'POST',
                    'header' => "Content-Type: application/json\r\nContent-Length: " . strlen($payloadJson) . "\r\n",
                    'content' => $payloadJson,
                    'timeout' => 20,
                    'ignore_errors' => true
                ],
                'ssl' => [
                    'verify_peer' => false,
                    'verify_peer_name' => false
                ]
            ]);
            $response = @file_get_contents($endpoint, false, $context);
            if (isset($http_response_header) && is_array($http_response_header)) {
                if (preg_match('#HTTP/[0-9\.]+\s+([0-9]+)#', $http_response_header[0], $matches)) {
                    $httpCode = (int)$matches[1];
                }
            }
        }

        if (!empty($response)) {
            $resData = json_decode($response, true);
            $candidateText = $resData['candidates'][0]['content']['parts'][0]['text'] ?? '';
            if (!empty($candidateText)) {
                $aiReply = trim($candidateText);
                break; // Başarılı yanıt alındı, döngüden çık
            } else {
                $lastError = $resData['error']['message'] ?? "Gemini API HTTP {$httpCode} hatası verdi.";
            }
        }
    }

    if (!empty($aiReply)) {
        http_response_code(200);
        echo json_encode([
            'ok' => true,
            'success' => true,
            'reply' => $aiReply
        ], JSON_UNESCAPED_UNICODE);
    } else {
        http_response_code(200);
        echo json_encode([
            'ok' => false,
            'success' => false,
            'error' => 'AI bağlantısı şu anda kullanılamıyor.',
            'message' => $lastError
        ], JSON_UNESCAPED_UNICODE);
    }

} catch (Throwable $t) {
    http_response_code(200);
    echo json_encode([
        'ok' => false,
        'success' => false,
        'error' => 'Beklenmeyen hata oluştu.',
        'message' => $t->getMessage()
    ], JSON_UNESCAPED_UNICODE);
}
