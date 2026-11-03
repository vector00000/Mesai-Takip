const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const verifier = require('./subscriptionVerifier.js');

test('1. Paket adı ve ürün kimliği doğrulaması', async () => {
  const badPkg = await verifier.verifySubscriptionPurchase({
    packageName: 'com.wrong.package',
    productId: 'mesai_takip_premium',
    purchaseToken: 'valid_token_1234567890'
  });
  assert.equal(badPkg.httpStatus, 400);
  assert.equal(badPkg.body.errorCode, 'INVALID_PACKAGE_NAME');

  const badProd = await verifier.verifySubscriptionPurchase({
    packageName: 'com.aistudio.mesaitakip.kvnrpd',
    productId: 'wrong_product',
    purchaseToken: 'valid_token_1234567890'
  });
  assert.equal(badProd.httpStatus, 400);
  assert.equal(badProd.body.errorCode, 'INVALID_PRODUCT_ID');
});

test('2. İptal edilmiş ancak mevcut ödenmiş dönemi devam eden abonelik (SUBSCRIPTION_STATE_CANCELED)', () => {
  const now = Date.now();
  const futureIso = new Date(now + 10 * 24 * 3600 * 1000).toISOString();
  const evalResult = verifier.evaluateGooglePlaySubscriptionState(
    {
      subscriptionState: 'SUBSCRIPTION_STATE_CANCELED',
      acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
      lineItems: [
        {
          productId: 'mesai_takip_premium',
          expiryTime: futureIso,
          offerDetails: { basePlanId: 'monthly' },
          autoRenewingPlan: { autoRenewEnabled: false }
        }
      ]
    },
    now
  );

  assert.equal(evalResult.isEntitled, true);
  assert.equal(evalResult.statusReason, 'CANCELED_ACTIVE_UNTIL_EXPIRY');
  assert.equal(evalResult.autoRenewEnabled, false);
});

test('3. Süresi dolmuş (EXPIRED) ve ödeme başarısızlığı (ON_HOLD) durumları', () => {
  const now = Date.now();
  const pastIso = new Date(now - 60000).toISOString();

  const expiredEval = verifier.evaluateGooglePlaySubscriptionState(
    {
      subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED',
      lineItems: [{ productId: 'mesai_takip_premium', expiryTime: pastIso }]
    },
    now
  );
  assert.equal(expiredEval.isEntitled, false);
  assert.equal(expiredEval.statusReason, 'EXPIRED');

  const onHoldEval = verifier.evaluateGooglePlaySubscriptionState(
    {
      subscriptionState: 'SUBSCRIPTION_STATE_ON_HOLD',
      lineItems: [{ productId: 'mesai_takip_premium', expiryTime: pastIso }]
    },
    now
  );
  assert.equal(onHoldEval.isEntitled, false);
  assert.equal(onHoldEval.statusReason, 'PAYMENT_FAILED_ON_HOLD');
});

test('4. Servis hesabı yapılandırılmadığında sahte doğrulama üretilmemesi', async () => {
  delete process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
  delete process.env.GOOGLE_APPLICATION_CREDENTIALS;

  const res = await verifier.verifySubscriptionPurchase({
    packageName: 'com.aistudio.mesaitakip.kvnrpd',
    productId: 'mesai_takip_premium',
    purchaseToken: 'valid_token_9876543210',
    accountId: 'user_account_1'
  });

  assert.equal(res.httpStatus, 200);
  assert.equal(res.body.serverVerificationConfigured, false);
  assert.equal(res.body.verifiedByGooglePlayApi, false);
  assert.equal(res.body.isEntitled, false);
});

test('5. RSA imza doğrulaması', () => {
  const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
  const spkiDer = publicKey.export({ type: 'spki', format: 'der' });
  const pubBase64 = spkiDer.toString('base64');

  const originalJson = JSON.stringify({
    packageName: 'com.aistudio.mesaitakip.kvnrpd',
    productId: 'mesai_takip_premium',
    purchaseToken: 'token_abc_123456'
  });

  const signer = crypto.createSign('RSA-SHA1');
  signer.update(originalJson, 'utf8');
  const sigBase64 = signer.sign(privateKey, 'base64');

  assert.equal(verifier.verifyPlayRsaSignature(pubBase64, originalJson, sigBase64), true);
  assert.equal(verifier.verifyPlayRsaSignature(pubBase64, originalJson + 'x', sigBase64), false);
});

test('6. Maaş Bordrosu ve Belge Arşivi sunucu tarafı kullanıcı izolasyonu (başka kullanıcının belgesine erişilemez)', () => {
  const db = require('./db.js');
  const savedDoc = db.createDocumentForUser('user_account_1', {
    title: 'Ekim 2026 Maaş Bordrosu',
    documentType: 'PAYSLIP',
    yearMonth: '2026-10',
    documentDate: '2026-10-15',
    fileName: 'ekim_bordro.pdf',
    mimeType: 'application/pdf',
    fileSizeBytes: 24500
  });

  assert.equal(savedDoc.ownerAccountId, 'user_account_1');

  // Kullanıcı 2 kendi belgelerini sorguladığında Kullanıcı 1'in belgesini göremez
  const user2Docs = db.getDocumentsForUser('user_account_2', '2026-10');
  assert.equal(user2Docs.some((d) => d.id === savedDoc.id), false);

  // Kullanıcı 2, Kullanıcı 1'in belge ID'si ile okuma veya silme denediğinde ACCESS_DENIED_NOT_OWNER hatası alır
  assert.throws(
    () => db.getDocumentByIdForUser(savedDoc.id, 'user_account_2'),
    (err) => err.code === 'ACCESS_DENIED_NOT_OWNER'
  );
  assert.throws(
    () => db.deleteDocumentForUser(savedDoc.id, 'user_account_2'),
    (err) => err.code === 'ACCESS_DENIED_NOT_OWNER'
  );

  // Kullanıcı 1 kendi belgesini okuyabilir ve silebilir
  const ownerDoc = db.getDocumentByIdForUser(savedDoc.id, 'user_account_1');
  assert.equal(ownerDoc.title, 'Ekim 2026 Maaş Bordrosu');
  assert.equal(db.deleteDocumentForUser(savedDoc.id, 'user_account_1'), true);
});
