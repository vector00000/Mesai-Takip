const assert = require('assert');
const http = require('http');
const server = require('./server.js');
const db = require('./db.js');

async function runTests() {
  console.log('--- Kalıcı Veritabanı ve Güvenlik Testleri Başlatılıyor ---');

  const testPort = 3998;
  await new Promise(resolve => server.listen(testPort, '127.0.0.1', resolve));
  console.log(`Test sunucusu ${testPort} üzerinde çalışıyor.`);

  function makeRequest(method, path, body) {
    return new Promise((resolve, reject) => {
      const data = body ? JSON.stringify(body) : null;
      const req = http.request({
        hostname: '127.0.0.1',
        port: testPort,
        path: path,
        method: method,
        headers: {
          'Content-Type': 'application/json',
          ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {})
        }
      }, (res) => {
        let resBody = '';
        res.on('data', chunk => { resBody += chunk; });
        res.on('end', () => {
          try {
            resolve({ status: res.statusCode, body: JSON.parse(resBody), raw: resBody });
          } catch (e) {
            resolve({ status: res.statusCode, raw: resBody });
          }
        });
      });
      req.on('error', reject);
      if (data) req.write(data);
      req.end();
    });
  }

  const validBuyer = {
    name: 'Mehmet',
    surname: 'Demir',
    email: 'mehmet.demir@example.com',
    gsmNumber: '05551234567',
    identityNumber: '12345678901',
    city: 'Istanbul',
    address: 'Kadikoy / Istanbul'
  };

  try {
    // 1. Health check
    const health = await makeRequest('GET', '/api/health');
    assert.strictEqual(health.status, 200);
    assert.strictEqual(health.body.status, 'healthy');
    console.log('✓ Test 1 Başarılı: /api/health çalışıyor');

    // 2. 0 TL reddedilmeli
    const zeroRes = await makeRequest('POST', '/api/payment/create', { amount: 0, currency: 'TRY', buyer: validBuyer });
    assert.strictEqual(zeroRes.status, 400);
    assert.strictEqual(zeroRes.body.success, false);
    console.log('✓ Test 2 Başarılı: 0 TL tutar güvenle reddedildi');

    // 3. Form doldurmadan doğrudan ödeme başlatma (buyer bilgisi olmadan)
    // Backend güvenli varsayılan buyer oluşturmalı ve isteği reddetmemeli (API key yoksa 503, varsa 200 döner, 400 'eksik bilgi' dönmemeli)
    const noBuyerRes = await makeRequest('POST', '/api/payment/create', { amount: 100, currency: 'TRY' });
    assert.ok(noBuyerRes.status === 200 || noBuyerRes.status === 503, 'Buyer olmadan istek kabul edilmeli');
    console.log('✓ Test 3 Başarılı: Formsuz / sadeleştirilmiş ödeme isteği backend tarafından güvenle kabul ediliyor');

    // 6. Kalıcı Veritabanı Persistence Testi (Sunucu yeniden başlasa bile verilerin korunması)
    const testPaymentId = `test_${Date.now()}`;
    db.createPayment({
      paymentId: testPaymentId,
      iyzicoToken: 'token_persisted_test',
      amount: 150.0,
      currency: 'TRY',
      status: 'PENDING',
      buyer: validBuyer
    });

    // Durumu güncelle
    db.updatePaymentStatus(testPaymentId, {
      status: 'SUCCESS',
      providerTransactionId: 'tx_persisted_999'
    });

    // Belleği simüle olarak sıfırlayıp diskten yeniden yükle
    db.payments.clear();
    db.tokenIndex.clear();
    db.loadFromDisk();

    const restored = db.getPaymentById(testPaymentId);
    assert.notStrictEqual(restored, null);
    assert.strictEqual(restored.status, 'SUCCESS');
    assert.strictEqual(restored.providerTransactionId, 'tx_persisted_999');
    assert.strictEqual(restored.amount, 150.0);
    console.log('✓ Test 6 Başarılı: Kalıcı veritabanı (Persistence) sunucu yeniden başlasa bile veriyi koruyor');

    // 7. GET /api/payment/status/:paymentId diskteki kaydı döndürmeli
    const statusRes = await makeRequest('GET', `/api/payment/status/${testPaymentId}`);
    assert.strictEqual(statusRes.status, 200);
    assert.strictEqual(statusRes.body.status, 'SUCCESS');
    assert.strictEqual(statusRes.body.providerTransactionId, 'tx_persisted_999');
    console.log('✓ Test 7 Başarılı: GET /api/payment/status kalıcı kaydı başarıyla döndürdü');

    // 8. AI Proje Asistanı Endpoint Testi (/ai-proje-asistani.php)
    const aiRes = await makeRequest('POST', '/ai-proje-asistani.php', {
      message: 'Merhaba, ben RA App Studio Proje Asistanıyım için test yapıyorum. Bana kısa bir cevap ver.'
    });
    assert.strictEqual(aiRes.status, 200);
    assert.strictEqual(aiRes.body.ok, true);
    assert.strictEqual(aiRes.body.success, true);
    assert.ok(aiRes.body.reply && aiRes.body.reply.length > 5, 'AI yanıtı dolu olmalı');
    console.log('✓ Test 8 Başarılı: AI Proje Asistanı (/ai-proje-asistani.php) gerçek Gemini yanıtı döndürdü');

    console.log('--- TÜM BACKEND VE PERSISTENCE TESTLERİ BAŞARIYLA GEÇTİ ---');
  } finally {
    server.close();
  }
}

runTests().catch(err => {
  console.error('Test hatası:', err);
  process.exit(1);
});
