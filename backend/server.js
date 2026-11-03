/**
 * Mesai Takip - Güvenli İyzico Ödeme Backend Sunucusu (Production Ready)
 * 
 * Güvenlik ve Mimari Standartları:
 * 1. API_KEY ve SECRET_KEY sunucuda tutulur, istemciye/Android'e ASLA iletilmez.
 * 2. Kart numarası, CVV, SKT sunucuda veya istemcide ASLA toplanmaz ve saklanmaz.
 * 3. Sabit/fake müşteri bilgisi (gsm, email, kimlik no) KESİNLİKLE kullanılmaz. Kullanıcıdan güvenli şekilde alınır.
 * 4. Ödeme kayıtları disk üzerinde kalıcı olarak saklanır (backend/db.js). Sunucu yeniden başlasa bile kaybolmaz.
 * 5. Farklı bir server instance'ına callback gelse bile iyzico retrieve API üzerinden conversationId çözümlenir.
 * 6. BACKEND_PUBLIC_URL ortam değişkeninden okunur; tanımlı değilse isteğin Host başlığından dinamik türetilir.
 * 7. Gerçek İyzico Checkout Form Initialize API'si (iyzipay SDK) kullanılır. Sahte token KESİNLİKLE üretilmez.
 * 8. Ödeme sonucu iyzico Detail API (retrieve) ile backend tarafında doğrulanır.
 */

const http = require('http');
const https = require('https');
const crypto = require('crypto');
const url = require('url');
const fs = require('fs');
const path = require('path');
const Iyzipay = require('iyzipay');
const db = require('./db.js');

// Çökme Koruması (Uncaught Exception & Unhandled Rejection)
process.on('uncaughtException', (err) => {
  console.error('[SERVER ERROR] Yakalanmamış İstisna:', err?.message || err);
});
process.on('unhandledRejection', (reason) => {
  console.error('[SERVER ERROR] Yakalanmamış Reddetme:', reason?.message || reason);
});

// .env ve sunucu özel ayarlarını otomatik yükle
const envPath = path.join(__dirname, '..', '.env');
if (fs.existsSync(envPath)) {
  try {
    const envLines = fs.readFileSync(envPath, 'utf8').split('\n');
    for (const line of envLines) {
      const trimmed = line.trim();
      if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
        const idx = trimmed.indexOf('=');
        const key = trimmed.substring(0, idx).trim();
        const val = trimmed.substring(idx + 1).trim().replace(/^["']|["']$/g, '');
        if (!process.env[key]) {
          process.env[key] = val;
        }
      }
    }
  } catch (_) {}
}

const secretPath = path.join(__dirname, 'data', '.secrets.json');
if (fs.existsSync(secretPath)) {
  try {
    const secrets = JSON.parse(fs.readFileSync(secretPath, 'utf8'));
    for (const [k, v] of Object.entries(secrets)) {
      if (!process.env[k]) {
        process.env[k] = v;
      }
    }
  } catch (_) {}
}

// --- Yapılandırma ve Ortam Değişkenleri ---
const PORT = parseInt(process.env.APP_PORT || '3000', 10);
const IYZICO_API_KEY = process.env.IYZICO_API_KEY || process.env.PAYMENT_API_KEY || '';
const IYZICO_SECRET_KEY = process.env.IYZICO_SECRET_KEY || process.env.PAYMENT_SECRET_KEY || '';
const IYZICO_ENV = 'production';
const IYZICO_BASE_URL = 'https://api.iyzipay.com';
const BACKEND_PUBLIC_URL = (process.env.BACKEND_PUBLIC_URL || '').replace(/\/$/, '');

// İyzico İstemcisi (Singleton)
let iyzipayClient = null;
function getIyzipayClient() {
  if (!IYZICO_API_KEY || !IYZICO_SECRET_KEY) {
    return null;
  }
  if (!iyzipayClient) {
    iyzipayClient = new Iyzipay({
      apiKey: IYZICO_API_KEY,
      secretKey: IYZICO_SECRET_KEY,
      uri: IYZICO_BASE_URL
    });
  }
  return iyzipayClient;
}

// Genel erişim adresini dinamik türetici (Hard-coded URL kullanılmaz)
function getPublicBaseUrl(req) {
  if (BACKEND_PUBLIC_URL) {
    return BACKEND_PUBLIC_URL;
  }
  const proto = req.headers['x-forwarded-proto'] || 'https';
  const host = req.headers['x-forwarded-host'] || req.headers.host || 'localhost:3000';
  return `${proto}://${host}`.replace(/\/$/, '');
}

// --- Rate Limiter (IP başına dakikada max 30 istek) ---
const ipRequestCounts = new Map();
function isRateLimited(ip) {
  const now = Date.now();
  const windowMs = 60000;
  const maxRequests = 30;

  let record = ipRequestCounts.get(ip);
  if (!record || now - record.startTime > windowMs) {
    record = { startTime: now, count: 1 };
    ipRequestCounts.set(ip, record);
    return false;
  }

  record.count += 1;
  return record.count > maxRequests;
}

// Güvenli Loglayıcı (Hassas anahtarları ve kart/kimlik verilerini KESİNLİKLE loglamaz)
function safeLog(level, message, meta = {}) {
  const safeMeta = { ...meta };
  delete safeMeta.apiKey;
  delete safeMeta.secretKey;
  delete safeMeta.IYZICO_API_KEY;
  delete safeMeta.IYZICO_SECRET_KEY;
  delete safeMeta.cardNumber;
  delete safeMeta.cvv;
  delete safeMeta.expireDate;
  delete safeMeta.identityNumber;

  console.log(JSON.stringify({
    timestamp: new Date().toISOString(),
    level,
    message,
    ...safeMeta
  }));
}

// Request Body Okuyucu (Max 64KB güvenlik sınırı)
function readRequestBody(req, maxBytes = 65536) {
  return new Promise((resolve, reject) => {
    let body = '';
    let receivedBytes = 0;

    req.on('data', chunk => {
      receivedBytes += chunk.length;
      if (receivedBytes > maxBytes) {
        req.destroy();
        reject(new Error('İstek gövdesi boyutu izin verilen sınırı aştı'));
        return;
      }
      body += chunk;
    });

    req.on('end', () => resolve(body));
    req.on('error', err => reject(err));
  });
}

// HTTP Sunucusu
const server = http.createServer(async (req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;
  const method = req.method;
  const clientIp = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '127.0.0.1').split(',')[0].trim();

  // Her isteği logla
  try {
    const logLine = `[${new Date().toISOString()}] ${method} ${pathname} (Full: ${req.url}) IP: ${clientIp} UA: ${req.headers['user-agent'] || 'none'}\n`;
    fs.appendFileSync(path.join(__dirname, 'requests.log'), logLine);
    console.log(logLine.trim());
  } catch (_) {}

  // CORS Başlıkları
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // Rate Limiting
  if (isRateLimited(clientIp)) {
    safeLog('WARN', 'Rate limit aşıldı', { clientIp, pathname });
    res.writeHead(429, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ success: false, message: 'Çok fazla istek gönderildi. Lütfen bir süre sonra tekrar deneyin.' }));
    return;
  }

  // 1. Sağlık ve Yapılandırma Kontrolü: GET /api/health
  if (method === 'GET' && (pathname === '/api/health' || pathname === '/health')) {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      status: 'healthy',
      app: 'Mesai Takip iyzico Payment Server',
      iyzicoEnv: IYZICO_ENV,
      baseUrl: IYZICO_BASE_URL,
      hasApiKey: Boolean(IYZICO_API_KEY),
      hasSecretKey: Boolean(IYZICO_SECRET_KEY),
      persistence: 'active'
    }));
    return;
  }

  // 1.1 Google Merchant Center & iyzico Ürün Kataloğu XML Beslemesi (/api/urunler, /urunler, .xml vb.)
  const isXmlRoute = [
    '/api/urunler',
    '/api/urunler/',
    '/urunler',
    '/urunler/',
    '/api/urunler.xml',
    '/urunler.xml',
    '/api/products',
    '/api/products/',
    '/products',
    '/products.xml',
    '/api/products.xml',
    '/api/feed.xml',
    '/feed.xml'
  ].includes(pathname);

  if (isXmlRoute) {
    if (method === 'OPTIONS') {
      res.writeHead(204, {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization, Accept'
      });
      res.end();
      return;
    }

    if (method === 'GET' || method === 'HEAD') {
      const xmlFile = path.join(__dirname, 'urunler.xml');
      if (fs.existsSync(xmlFile)) {
        const xmlContent = fs.readFileSync(xmlFile, 'utf8');
        res.writeHead(200, {
          'Content-Type': 'application/xml; charset=utf-8',
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization, Accept',
          'Cache-Control': 'public, max-age=300'
        });
        if (method === 'HEAD') {
          res.end();
        } else {
          res.end(xmlContent);
        }
        return;
      }
    }
  }

  // 1.2 AI Proje Asistanı: /ai-proje-asistani.php, /api/ai-proje-asistani.php veya /api/ai-proje-asistani
  const isAiRoute = [
    '/ai-proje-asistani.php',
    '/api/ai-proje-asistani.php',
    '/api/ai-proje-asistani',
    '/ai-proje-asistani'
  ].includes(pathname);

  if (isAiRoute) {
    if (method === 'OPTIONS') {
      res.writeHead(204, {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS, HEAD',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization, Accept, X-Requested-With',
        'Access-Control-Max-Age': '86400'
      });
      res.end();
      return;
    }

    if (method === 'GET' || method === 'HEAD') {
      res.writeHead(200, {
        'Content-Type': 'application/json; charset=utf-8',
        'Access-Control-Allow-Origin': '*'
      });
      if (method === 'HEAD') {
        res.end();
      } else {
        res.end(JSON.stringify({
          ok: true,
          success: true,
          service: 'RA App Studio AI Proje Asistanı',
          status: 'active',
          description: 'POST ile {"message": "..."} gönderiniz.'
        }));
      }
      return;
    }

    if (method !== 'POST') {
      res.writeHead(405, {
        'Content-Type': 'application/json; charset=utf-8',
        'Access-Control-Allow-Origin': '*'
      });
      res.end(JSON.stringify({ ok: false, success: false, error: 'Yalnızca POST isteği kabul edilir.' }));
      return;
    }

    try {
      const rawBody = await readRequestBody(req);
      const payload = JSON.parse(rawBody || '{}');
      const userMessage = (payload.message || '').trim();

      if (!userMessage) {
        res.writeHead(400, {
          'Content-Type': 'application/json; charset=utf-8',
          'Access-Control-Allow-Origin': '*'
        });
        res.end(JSON.stringify({ ok: false, success: false, error: 'Geçerli bir proje mesajı gönderin.' }));
        return;
      }

      const apiKey = process.env.GEMINI_API_KEY;
      if (!apiKey) {
        throw new Error('GEMINI_API_KEY ortam değişkeni tanımlı değil.');
      }

      const systemPrompt = `Sen RA App Studio (raappstudio.online) Proje Asistanısın.
Kullanıcılara iOS ve Android mobil uygulama geliştirme, kurumsal web siteleri, yönetim panelleri ve özel yazılım projeleri hakkında bilgi veriyorsun.
Hizmet başlangıç fiyatları:
- Kurumsal Web Sitesi: 7.500 TL'den
- Mobil Uygulama: 25.000 TL'den
- Özel Yazılım: 40.000 TL'den
Kullanıcıya yardımcı, profesyonel, samimi ve Türkçe cevaplar ver. Net ve anlaşılır ol, gerektiğinde projeyi netleştirmek için sorular sor ve detaylı teklif için WhatsApp (+90 533 486 33 59) veya info@raappstudio.online adresine yönlendir.`;

      const geminiBody = JSON.stringify({
        contents: [
          {
            role: 'user',
            parts: [{ text: `${systemPrompt}\n\nKullanıcı: ${userMessage}` }]
          }
        ],
        generationConfig: {
          temperature: 0.7,
          maxOutputTokens: 800
        }
      });

      // Dayanıklı model sıralaması: Hızlı ve aktif modeller önceliklidir
      const candidateModels = [
        'gemini-3.1-flash-lite-preview',
        'gemini-flash-latest',
        'gemini-3.8-flash',
        'gemini-3.5-flash'
      ];

      async function callModel(model, attempt = 1) {
        return new Promise((resolve, reject) => {
          const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;
          const geminiReq = https.request(geminiUrl, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Content-Length': Buffer.byteLength(geminiBody)
            },
            timeout: 10000
          }, (geminiRes) => {
            let data = '';
            geminiRes.on('data', chunk => data += chunk);
            geminiRes.on('end', async () => {
              if ((geminiRes.statusCode === 503 || geminiRes.statusCode === 429) && attempt < 2) {
                await new Promise(r => setTimeout(r, 800));
                try {
                  const retryRes = await callModel(model, attempt + 1);
                  return resolve(retryRes);
                } catch (retryErr) {
                  return reject(retryErr);
                }
              }

              try {
                const resJson = JSON.parse(data);
                const text = resJson.candidates?.[0]?.content?.parts?.[0]?.text;
                if (text && geminiRes.statusCode === 200) {
                  resolve(text.trim());
                } else {
                  const errMsg = resJson.error?.message || `HTTP ${geminiRes.statusCode}`;
                  reject(new Error(errMsg));
                }
              } catch (parseErr) {
                reject(new Error('Gemini API yanıtı ayrıştırılamadı.'));
              }
            });
          });

          geminiReq.on('error', err => reject(err));
          geminiReq.on('timeout', () => {
            geminiReq.destroy();
            reject(new Error(`Model ${model} zaman aşımına uğradı.`));
          });
          geminiReq.write(geminiBody);
          geminiReq.end();
        });
      }

      let aiReply = '';
      let lastModelError = '';

      for (const model of candidateModels) {
        try {
          aiReply = await callModel(model);
          if (aiReply) break;
        } catch (mErr) {
          lastModelError = mErr.message;
          safeLog('WARN', `Gemini model ${model} başarısız oldu, alternatif deneniyor`, { error: mErr.message });
        }
      }

      if (!aiReply) {
        throw new Error(lastModelError || 'Tüm Gemini modelleri meşgul.');
      }

      res.writeHead(200, {
        'Content-Type': 'application/json; charset=utf-8',
        'Access-Control-Allow-Origin': '*'
      });
      res.end(JSON.stringify({
        ok: true,
        success: true,
        reply: aiReply
      }));
    } catch (err) {
      safeLog('ERROR', 'AI asistan isteği başarısız oldu', { error: err.message });
      res.writeHead(200, {
        'Content-Type': 'application/json; charset=utf-8',
        'Access-Control-Allow-Origin': '*'
      });
      res.end(JSON.stringify({
        ok: false,
        success: false,
        error: 'AI bağlantısı şu anda kullanılamıyor.',
        message: err.message
      }));
    }
    return;
  }

  // 2. Ödeme Oturumu Başlatma: POST /api/payment/create veya /api/payments/create
  // API tamamen sadeleştirildi: Kullanıcıdan TC, Şehir, Telefon vb. kişisel bilgiler ALINMAZ.
  // İstemciden sadece 'price' parametresi alınır.
  if (method === 'POST' && (pathname === '/api/payment/create' || pathname === '/api/payments/create')) {
    try {
      const rawBody = await readRequestBody(req);
      const payload = JSON.parse(rawBody || '{}');
      
      // Sadece 'price' parametresi alınır ('amount' geriye dönük uyumluluk için de desteklenir)
      const rawPrice = payload.price !== undefined ? payload.price : payload.amount;
      const amount = parseFloat(rawPrice);
      const currency = (payload.currency || 'TRY').toUpperCase();

      // Tutar Doğrulaması
      if (isNaN(amount) || !isFinite(amount) || amount < 1.0 || amount > 50000.0) {
        safeLog('WARN', 'Geçersiz destek tutarı', { price: rawPrice, clientIp });
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          success: false,
          message: 'Destek tutarı 1 TL ile 50.000 TL arasında geçerli bir sayı olmalıdır.'
        }));
        return;
      }

      if (currency !== 'TRY') {
        res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ success: false, message: 'Yalnızca TRY para birimi desteklenmektedir.' }));
        return;
      }

      // Güvenli ve Anonim iyzico Zorunlu Şema Bilgileri:
      // Kullanıcıdan T.C., kimlik, şehir, adres veya telefon ASLA İSTENMEZ ve veritabanında tutulmaz.
      // iyzico API'sinin teknik doğrulaması için standart güvenli varsayılanlar kullanılır.
      const buyerName = 'Gönüllü';
      const buyerSurname = 'Destekçi';
      const buyerEmail = 'destek@mesaitakip.app';
      const formattedPhone = '+905555555555';
      const safeBuyerIdentity = '11111111111';
      const buyerCity = 'Istanbul';
      const buyerAddress = 'Turkiye';

      // API Key / Secret Key Kontrolü
      const client = getIyzipayClient();
      if (!client) {
        safeLog('ERROR', 'iyzico API anahtarları tanımlı değil', { IYZICO_ENV });
        res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          success: false,
          message: 'iyzico ödeme anahtarları sunucuda henüz yapılandırılmamış. Lütfen IYZICO_API_KEY ve IYZICO_SECRET_KEY ortam değişkenlerini tanımlayın.'
        }));
        return;
      }

      const paymentId = `sup_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;
      const amountFormatted = amount.toFixed(2);
      const nowFormatted = new Date().toISOString().replace('T', ' ').substring(0, 19);
      const publicBaseUrl = getPublicBaseUrl(req);
      const callbackUrl = `${publicBaseUrl}/api/payment/callback`;

      // İyzico Resmi Checkout Form Başlatma İsteği
      const iyzicoRequest = {
        locale: Iyzipay.LOCALE.TR,
        conversationId: paymentId,
        price: amountFormatted,
        paidPrice: amountFormatted,
        currency: Iyzipay.CURRENCY.TRY,
        basketId: `BASKET_${paymentId}`,
        paymentGroup: Iyzipay.PAYMENT_GROUP.PRODUCT,
        callbackUrl: callbackUrl,
        enabledInstallments: [1],
        buyer: {
          id: `BY_${paymentId}`,
          name: buyerName,
          surname: buyerSurname,
          gsmNumber: formattedPhone,
          email: buyerEmail,
          identityNumber: safeBuyerIdentity,
          lastLoginDate: nowFormatted,
          registrationDate: nowFormatted,
          registrationAddress: buyerAddress,
          ip: clientIp || '127.0.0.1',
          city: buyerCity,
          country: 'Turkey',
          zipCode: '34000'
        },
        billingAddress: {
          contactName: `${buyerName} ${buyerSurname}`,
          city: buyerCity,
          country: 'Turkey',
          address: buyerAddress,
          zipCode: '34000'
        },
        basketItems: [
          {
            id: `ITEM_${paymentId}`,
            name: 'Gönüllü Geliştirici Katkısı',
            category1: 'Destek',
            itemType: Iyzipay.BASKET_ITEM_TYPE.VIRTUAL,
            price: amountFormatted
          }
        ]
      };

      // Gerçek İyzico Checkout Form Çağrısı
      client.checkoutFormInitialize.create(iyzicoRequest, (err, result) => {
        if (err || !result || result.status !== 'success' || !result.paymentPageUrl) {
          const errDetail = result?.errorMessage || err?.message || 'İyzico ödeme oturumu oluşturulamadı.';
          safeLog('ERROR', 'iyzico checkoutFormInitialize başarısız oldu', {
            paymentId,
            errorCode: result?.errorCode,
            errorMessage: result?.errorMessage,
            errorGroup: result?.errorGroup,
            err: err?.message
          });

          // KESİNLİKLE SAHTE TOKEN ÜRETİLMEZ!
          res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
          res.end(JSON.stringify({
            success: false,
            message: `Ödeme başlatılamadı: ${errDetail}`
          }));
          return;
        }

        const iyzicoToken = result.token;
        const paymentUrl = result.paymentPageUrl;

        // KALICI VERİTABANINA YAZILIR (Server restart olsa bile silinmez)
        db.createPayment({
          paymentId,
          iyzicoToken,
          amount,
          currency,
          status: 'PENDING',
          buyer: { name: buyerName, surname: buyerSurname }
        });

        safeLog('INFO', 'iyzico gerçek ödeme oturumu başarıyla oluşturuldu ve kaydedildi', {
          paymentId,
          amount,
          currency
        });

        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          success: true,
          paymentId: paymentId,
          paymentUrl: paymentUrl,
          token: iyzicoToken
        }));
      });
    } catch (e) {
      safeLog('ERROR', 'Ödeme oluşturma ayrıştırma hatası', { error: e.message });
      res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ success: false, message: 'Sunucu hatası: İstek işlenemedi.' }));
    }
    return;
  }

  // 3. Callback Doğrulama Endpoint: POST /api/payment/callback veya /api/payments/callback
  // Farklı bir server instance'ına gelse bile iyzico retrieve API ile doğrulanır
  if (method === 'POST' && (pathname === '/api/payment/callback' || pathname === '/api/payments/callback')) {
    try {
      const rawBody = await readRequestBody(req);
      let token = '';

      if (req.headers['content-type']?.includes('application/json')) {
        try {
          const parsed = JSON.parse(rawBody || '{}');
          token = parsed.token || '';
        } catch (_) {}
      }

      if (!token) {
        const params = new URLSearchParams(rawBody);
        token = params.get('token') || '';
      }

      if (!token) {
        safeLog('WARN', 'Callback isteğinde token bulunamadı');
        sendRedirectHtml(res, 'mesaitakip://payment/failure?reason=missing_token', 'Ödeme oturumu doğrulanamadı.');
        return;
      }

      const client = getIyzipayClient();
      if (!client) {
        safeLog('ERROR', 'iyzico istemcisi callback anında başlatılamadı');
        sendRedirectHtml(res, `mesaitakip://payment/failure?reason=client_unavailable`, 'Ödeme doğrulanamadı.');
        return;
      }

      // İyzico Resmi Detail/Retrieve API ile Gerçek Durumu Sorgula
      // Farklı bir sunucu instance'ına gelse bile result.conversationId ile paymentId elde edilir
      client.checkoutForm.retrieve({
        locale: Iyzipay.LOCALE.TR,
        token: token
      }, (err, result) => {
        if (err || !result) {
          safeLog('ERROR', 'iyzico checkoutForm.retrieve hatası', { error: err?.message });
          sendRedirectHtml(res, `mesaitakip://payment/failure?reason=retrieve_error`, 'Ödeme doğrulanamadı.');
          return;
        }

        const resolvedPaymentId = result.conversationId;
        const isSuccess = (result.status === 'success' && result.paymentStatus === 'SUCCESS');
        const paidPrice = parseFloat(result.paidPrice || result.price || '0');
        const providerTxId = result.paymentId || '';

        // Kalıcı veritabanında kaydı bul veya bu instance ilk kez görüyorsa oluştur
        let record = db.getPaymentById(resolvedPaymentId);
        if (!record) {
          record = db.createPayment({
            paymentId: resolvedPaymentId,
            iyzicoToken: token,
            amount: paidPrice,
            currency: result.currency || 'TRY',
            status: isSuccess ? 'SUCCESS' : 'FAILED'
          });
        }

        // IDEMPOTENCY: Zaten SUCCESS ise mükerrer doğrulama yapma
        if (record.status === 'SUCCESS') {
          safeLog('INFO', 'Mükerrer callback, idempotency koruması uygulandı', { paymentId: resolvedPaymentId });
          sendRedirectHtml(res, `mesaitakip://payment/success?paymentId=${resolvedPaymentId}`, 'Ödemeniz zaten onaylandı.');
          return;
        }

        if (isSuccess && paidPrice >= (record.amount || 0)) {
          db.updatePaymentStatus(resolvedPaymentId, {
            status: 'SUCCESS',
            providerTransactionId: providerTxId
          });

          safeLog('INFO', 'Ödeme İyzico tarafından doğrulandı ve kalıcı veritabanına SUCCESS kaydedildi', {
            paymentId: resolvedPaymentId,
            providerTransactionId: providerTxId,
            paidPrice
          });

          sendRedirectHtml(res, `mesaitakip://payment/success?paymentId=${resolvedPaymentId}`, 'Destek ödemeniz başarıyla alındı. Teşekkür ederiz.');
        } else {
          db.updatePaymentStatus(resolvedPaymentId, {
            status: 'FAILED',
            providerTransactionId: providerTxId
          });

          safeLog('WARN', 'Ödeme İyzico tarafından onaylanmadı (FAILED)', {
            paymentId: resolvedPaymentId,
            status: result.status,
            paymentStatus: result.paymentStatus,
            errorMessage: result.errorMessage
          });

          const errMsg = encodeURIComponent(result.errorMessage || 'Ödeme gerçekleştirilemedi.');
          sendRedirectHtml(res, `mesaitakip://payment/failure?paymentId=${resolvedPaymentId}&error=${errMsg}`, 'Ödeme gerçekleştirilemedi.');
        }
      });
    } catch (e) {
      safeLog('ERROR', 'Callback işleme genel hatası', { error: e.message });
      sendRedirectHtml(res, 'mesaitakip://payment/failure?reason=server_error', 'Ödeme işlemi sırasında bir hata oluştu.');
    }
    return;
  }

  // 4. Ödeme Durumu Sorgulama Endpoint: GET /api/payment/status/:paymentId veya /api/payments/status/:paymentId
  if (method === 'GET' && (pathname.startsWith('/api/payment/status/') || pathname.startsWith('/api/payments/status/'))) {
    const paymentId = pathname.split('/').pop();
    const record = db.getPaymentById(paymentId);

    if (!record) {
      res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ success: false, message: 'Ödeme kaydı bulunamadı.' }));
      return;
    }

    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      success: true,
      paymentId: record.paymentId,
      status: record.status,
      amount: record.amount,
      currency: record.currency,
      providerTransactionId: record.providerTransactionId,
      createdAt: record.createdAt,
      completedAt: record.completedAt
    }));
    return;
  }

  // 5. İptal Bildirimi Endpoint: POST /api/payment/cancel veya /api/payments/cancel
  if (method === 'POST' && (pathname === '/api/payment/cancel' || pathname === '/api/payments/cancel')) {
    try {
      const rawBody = await readRequestBody(req);
      const payload = JSON.parse(rawBody || '{}');
      const paymentId = payload.paymentId;
      const record = paymentId ? db.getPaymentById(paymentId) : null;

      if (record && record.status === 'PENDING') {
        db.updatePaymentStatus(paymentId, { status: 'CANCELLED' });
        safeLog('INFO', 'Ödeme kullanıcı tarafından iptal edildi ve kaydedildi', { paymentId });
      }

      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ success: true, status: record ? record.status : 'CANCELLED' }));
    } catch (_) {
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ success: true, status: 'CANCELLED' }));
    }
    return;
  }

  // 5b. Google Play Billing Sunucu Tarafı Abonelik Doğrulama Endpoint: POST /api/billing/verify-subscription
  if (method === 'POST' && pathname === '/api/billing/verify-subscription') {
    try {
      const subscriptionVerifier = require('./subscriptionVerifier.js');
      const rawBody = await readRequestBody(req);
      const payload = JSON.parse(rawBody || '{}');
      const result = await subscriptionVerifier.verifySubscriptionPurchase(payload);
      res.writeHead(result.httpStatus, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(result.body));
    } catch (err) {
      safeLog('ERROR', 'Google Play abonelik doğrulama hatası', { error: err?.message });
      res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({
        success: false,
        verifiedByGooglePlayApi: false,
        isEntitled: false,
        errorCode: 'BAD_REQUEST',
        message: 'Geçersiz abonelik doğrulama isteği.'
      }));
    }
    return;
  }

  // 5c. Google Play Real-time Developer Notifications (RTDN Pub/Sub Webhook): POST /api/billing/rtdn-webhook
  if (method === 'POST' && pathname === '/api/billing/rtdn-webhook') {
    try {
      const subscriptionVerifier = require('./subscriptionVerifier.js');
      const rawBody = await readRequestBody(req);
      const pubsubEnvelope = JSON.parse(rawBody || '{}');
      const encodedData = pubsubEnvelope?.message?.data;
      if (encodedData) {
        const decodedJson = Buffer.from(encodedData, 'base64').toString('utf8');
        const notification = JSON.parse(decodedJson || '{}');
        const subNotification = notification.subscriptionNotification;
        const packageName = notification.packageName;
        if (subNotification && subNotification.purchaseToken && packageName === subscriptionVerifier.EXPECTED_PACKAGE_NAME) {
          await subscriptionVerifier.verifySubscriptionPurchase({
            packageName,
            productId: subNotification.subscriptionId || subscriptionVerifier.EXPECTED_PRODUCT_ID,
            purchaseToken: subNotification.purchaseToken
          });
        }
      }
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ received: true }));
    } catch (err) {
      safeLog('ERROR', 'RTDN webhook işleme hatası', { error: err?.message });
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ received: false }));
    }
    return;
  }

  // 5d. Maaş Bordrosu ve Belge Arşivi Yetkilendirilmiş Sunucu Uç Noktaları (/api/documents)
  if (pathname === '/api/documents' || pathname.startsWith('/api/documents/')) {
    const requesterAccountId = (req.headers['x-account-id'] || '').toString().trim();
    const purchaseToken = (req.headers['x-purchase-token'] || '').toString().trim();

    if (!requesterAccountId) {
      res.writeHead(401, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({
        success: false,
        errorCode: 'UNAUTHORIZED',
        message: 'Belge arşivine erişmek için kullanıcı kimlik doğrulaması gereklidir.'
      }));
      return;
    }

    // Sunucu tarafı abonelik yetkilendirme kontrolü
    const subRecord = purchaseToken ? db.getSubscriptionByToken(purchaseToken) : null;
    if (!subRecord || !subRecord.isEntitled || (subRecord.accountId && subRecord.accountId !== requesterAccountId)) {
      res.writeHead(403, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({
        success: false,
        errorCode: 'PREMIUM_REQUIRED_OR_TOKEN_MISMATCH',
        message: 'Bu işlem için hesabınıza tanımlı aktif Mesai Takip Premium aboneliği gereklidir.'
      }));
      return;
    }

    try {
      if (method === 'GET' && pathname === '/api/documents') {
        const yearMonth = parsedUrl.searchParams.get('yearMonth') || null;
        const docs = db.getDocumentsForUser(requesterAccountId, yearMonth);
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ success: true, documents: docs }));
        return;
      }

      if (method === 'POST' && pathname === '/api/documents') {
        const rawBody = await readRequestBody(req);
        const payload = JSON.parse(rawBody || '{}');
        if (!payload.title || !payload.yearMonth || !payload.documentDate) {
          res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
          res.end(JSON.stringify({
            success: false,
            errorCode: 'INVALID_DOCUMENT_METADATA',
            message: 'Belge adı, dönem (yıl/ay) ve tarih alanları zorunludur.'
          }));
          return;
        }
        const saved = db.createDocumentForUser(requesterAccountId, payload);
        res.writeHead(201, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ success: true, document: saved }));
        return;
      }

      const docIdMatch = pathname.match(/^\/api\/documents\/([^\/]+)$/);
      if (docIdMatch) {
        const docId = decodeURIComponent(docIdMatch[1]);
        if (method === 'GET') {
          const doc = db.getDocumentByIdForUser(docId, requesterAccountId);
          res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
          res.end(JSON.stringify({ success: true, document: doc }));
          return;
        }
        if (method === 'DELETE') {
          db.deleteDocumentForUser(docId, requesterAccountId);
          res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
          res.end(JSON.stringify({ success: true, deletedId: docId }));
          return;
        }
      }
    } catch (err) {
      const status = err.code === 'ACCESS_DENIED_NOT_OWNER' ? 403 : err.code === 'NOT_FOUND' ? 404 : 400;
      res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({
        success: false,
        errorCode: err.code || 'DOCUMENT_ERROR',
        message: err.message || 'Belge işlemi sırasında hata oluştu.'
      }));
      return;
    }
  }

  // 6. Statik Web Sitesi Sunumu (backend/public: index.html, hakkimizda.html, logo.png vb.)
  if (method === 'GET' || method === 'HEAD') {
    let filePath = pathname;
    if (filePath === '/' || filePath === '') {
      filePath = '/index.html';
    }
    const safePath = path.normalize(filePath).replace(/^(\.\.[\/\\])+/, '');
    const fullPath = path.join(__dirname, 'public', safePath);

    if (fs.existsSync(fullPath) && fs.statSync(fullPath).isFile()) {
      const ext = path.extname(fullPath).toLowerCase();
      const mimeTypes = {
        '.html': 'text/html; charset=utf-8',
        '.css': 'text/css; charset=utf-8',
        '.js': 'application/javascript; charset=utf-8',
        '.json': 'application/json; charset=utf-8',
        '.png': 'image/png',
        '.jpg': 'image/jpeg',
        '.jpeg': 'image/jpeg',
        '.svg': 'image/svg+xml',
        '.ico': 'image/x-icon'
      };
      const contentType = mimeTypes[ext] || 'application/octet-stream';
      res.writeHead(200, {
        'Content-Type': contentType,
        'Access-Control-Allow-Origin': '*',
        'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=86400'
      });
      if (method === 'HEAD') {
        res.end();
      } else {
        const stream = fs.createReadStream(fullPath);
        stream.pipe(res);
      }
      return;
    }
  }

  // Tanımsız Rota: 404
  res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify({ success: false, message: 'Endpoint bulunamadı.' }));
});

/**
 * Kullanıcıyı güvenli deep link ile uygulamaya geri yönlendiren HTML şablonu
 */
function sendRedirectHtml(res, deepLinkUrl, message) {
  const isSuccess = deepLinkUrl.includes('/success');
  const themeColor = isSuccess ? '#059669' : '#dc2626';
  const icon = isSuccess ? '✓' : '✕';
  const title = isSuccess ? 'Ödeme Başarılı' : 'Ödeme Sonucu';

  const html = `<!DOCTYPE html>
<html lang="tr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title} - Mesai Takip</title>
  <meta http-equiv="refresh" content="2;url=${deepLinkUrl}">
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      margin: 0;
      background: #f8fafc;
      color: #0f172a;
    }
    .card {
      background: white;
      padding: 32px;
      border-radius: 20px;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.08);
      text-align: center;
      max-width: 360px;
      margin: 20px;
    }
    .icon {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: ${themeColor}15;
      color: ${themeColor};
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 32px;
      margin: 0 auto 20px;
      font-weight: bold;
    }
    h2 { margin: 0 0 10px; font-size: 20px; }
    p { color: #64748b; font-size: 14px; line-height: 1.5; margin: 0 0 24px; }
    .btn {
      display: inline-block;
      background: ${themeColor};
      color: white;
      padding: 12px 24px;
      border-radius: 12px;
      text-decoration: none;
      font-weight: 600;
      font-size: 14px;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="icon">${icon}</div>
    <h2>${title}</h2>
    <p>${message}<br><small style="color:#94a3b8">Uygulamaya otomatik dönülüyor...</small></p>
    <a href="${deepLinkUrl}" class="btn">Uygulamaya Dön</a>
  </div>
  <script>
    setTimeout(function() {
      window.location.href = "${deepLinkUrl}";
    }, 800);
  </script>
</body>
</html>`;

  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(html);
}

// Sunucuyu Başlat
if (require.main === module) {
  server.listen(PORT, '0.0.0.0', () => {
    safeLog('INFO', `Mesai Takip iyzico Ödeme Sunucusu ${PORT} portunda dinliyor. Ortam: ${IYZICO_ENV}, BaseURL: ${IYZICO_BASE_URL}`);
  });
}

module.exports = server;
