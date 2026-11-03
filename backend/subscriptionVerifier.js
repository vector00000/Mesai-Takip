/**
 * Mesai Takip — Google Play Developer API Sunucu Tarafı Abonelik Doğrulama Modülü
 *
 * Güvenlik İlkeleri:
 * 1. Paket adı ("com.aistudio.mesaitakip.kvnrpd") ve ürün kimliği ("mesai_takip_premium") kontrol edilir.
 * 2. Tek bir purchaseToken'ın farklı hesaplarda kötüye kullanılması (token replay) engellenir.
 * 3. GOOGLE_PLAY_SERVICE_ACCOUNT_JSON tanımlıysa Google Play Developer API (subscriptionsv2.get)
 *    üzerinden gerçek abonelik durumu, bitiş süresi (expiryTime), iptal ve yenileme durumu doğrulanır.
 * 4. Servis hesabı henüz yapılandırılmamışsa sahte onay ÜRETİLMEZ; açıkça
 *    `serverVerificationConfigured: false, verifiedByGooglePlayApi: false` döndürülür.
 */

const https = require('https');
const crypto = require('crypto');
const fs = require('fs');
const db = require('./db.js');

const EXPECTED_PACKAGE_NAME = 'com.aistudio.mesaitakip.kvnrpd';
const EXPECTED_PRODUCT_ID = 'mesai_takip_premium';
const EXPECTED_BASE_PLAN_ID = 'monthly';

function loadServiceAccountCredentials() {
  const rawJson = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
  if (rawJson && rawJson.trim().startsWith('{')) {
    try {
      return JSON.parse(rawJson);
    } catch (_) {
      return null;
    }
  }

  const keyFilePath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (keyFilePath && fs.existsSync(keyFilePath)) {
    try {
      return JSON.parse(fs.readFileSync(keyFilePath, 'utf8'));
    } catch (_) {
      return null;
    }
  }

  return null;
}

function base64UrlEncode(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(String(input), 'utf8');
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fetchGoogleAccessToken(serviceAccount) {
  return new Promise((resolve, reject) => {
    const nowSec = Math.floor(Date.now() / 1000);
    const header = { alg: 'RS256', typ: 'JWT' };
    const claimSet = {
      iss: serviceAccount.client_email,
      scope: 'https://www.googleapis.com/auth/androidpublisher',
      aud: serviceAccount.token_uri || 'https://oauth2.googleapis.com/token',
      iat: nowSec,
      exp: nowSec + 3600
    };

    const unsignedJwt = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(claimSet))}`;
    const signer = crypto.createSign('RSA-SHA256');
    signer.update(unsignedJwt);
    const signature = base64UrlEncode(signer.sign(serviceAccount.private_key));
    const assertion = `${unsignedJwt}.${signature}`;

    const postData = `grant_type=${encodeURIComponent('urn:ietf:params:oauth:grant-type:jwt-bearer')}&assertion=${encodeURIComponent(assertion)}`;

    const req = https.request(
      {
        hostname: 'oauth2.googleapis.com',
        path: '/token',
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Content-Length': Buffer.byteLength(postData)
        },
        timeout: 10000
      },
      (res) => {
        let body = '';
        res.on('data', (chunk) => (body += chunk));
        res.on('end', () => {
          try {
            const parsed = JSON.parse(body);
            if (res.statusCode === 200 && parsed.access_token) {
              resolve(parsed.access_token);
            } else {
              reject(new Error(parsed.error_description || parsed.error || 'OAuth2 token alınamadı'));
            }
          } catch (e) {
            reject(e);
          }
        });
      }
    );

    req.on('error', reject);
    req.write(postData);
    req.end();
  });
}

function querySubscriptionsV2FromGooglePlay(packageName, purchaseToken, accessToken) {
  return new Promise((resolve, reject) => {
    const apiPath = `/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
    const req = https.request(
      {
        hostname: 'androidpublisher.googleapis.com',
        path: apiPath,
        method: 'GET',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: 'application/json'
        },
        timeout: 10000
      },
      (res) => {
        let body = '';
        res.on('data', (chunk) => (body += chunk));
        res.on('end', () => {
          try {
            const parsed = JSON.parse(body || '{}');
            if (res.statusCode === 200) {
              resolve(parsed);
            } else {
              const errMsg = parsed?.error?.message || `Google Play API HTTP ${res.statusCode}`;
              const err = new Error(errMsg);
              err.statusCode = res.statusCode;
              reject(err);
            }
          } catch (e) {
            reject(e);
          }
        });
      }
    );

    req.on('error', reject);
    req.end();
  });
}

function verifyPlayRsaSignature(publicKeyBase64, originalJson, signatureBase64) {
  if (!publicKeyBase64 || !originalJson || !signatureBase64) return false;
  try {
    const formattedKey = `-----BEGIN PUBLIC KEY-----\n${publicKeyBase64.match(/.{1,64}/g).join('\n')}\n-----END PUBLIC KEY-----`;
    const verifier = crypto.createVerify('RSA-SHA1');
    verifier.update(originalJson, 'utf8');
    return verifier.verify(formattedKey, signatureBase64, 'base64');
  } catch (_) {
    return false;
  }
}

/**
 * Google Play Developer API subscriptionsv2 yanıtını değerlendirir.
 * Abonelik iptal edilmiş (SUBSCRIPTION_STATE_CANCELED) olsa bile ödenmiş mevcut dönem
 * sona ermemişse (expiryTimeMillis > now) Premium erişimini korur!
 */
function evaluateGooglePlaySubscriptionState(subV2Response, nowMs = Date.now()) {
  const subscriptionState = subV2Response.subscriptionState || 'SUBSCRIPTION_STATE_UNSPECIFIED';
  const lineItems = Array.isArray(subV2Response.lineItems) ? subV2Response.lineItems : [];
  const primaryLineItem =
    lineItems.find((item) => item.productId === EXPECTED_PRODUCT_ID) || lineItems[0] || {};

  const productId = primaryLineItem.productId || '';
  const basePlanId = primaryLineItem.offerDetails?.basePlanId || '';
  const expiryTimeStr = primaryLineItem.expiryTime || null;
  const expiryTimeMillis = expiryTimeStr ? Date.parse(expiryTimeStr) : 0;
  const autoRenewEnabled = Boolean(primaryLineItem.autoRenewingPlan?.autoRenewEnabled);
  const acknowledgementState = subV2Response.acknowledgementState || '';

  const isNotExpiredYet = expiryTimeMillis > nowMs;

  let isEntitled = false;
  let statusReason = subscriptionState;

  switch (subscriptionState) {
    case 'SUBSCRIPTION_STATE_ACTIVE':
      isEntitled = isNotExpiredYet || expiryTimeMillis === 0;
      statusReason = 'ACTIVE';
      break;
    case 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD':
      isEntitled = true;
      statusReason = 'IN_GRACE_PERIOD';
      break;
    case 'SUBSCRIPTION_STATE_CANCELED':
      // Kullanıcı aboneliği iptal etmiş ancak ödenmiş dönem henüz bitmemişse erişim devam eder!
      isEntitled = isNotExpiredYet;
      statusReason = isNotExpiredYet ? 'CANCELED_ACTIVE_UNTIL_EXPIRY' : 'EXPIRED';
      break;
    case 'SUBSCRIPTION_STATE_ON_HOLD':
      isEntitled = false;
      statusReason = 'PAYMENT_FAILED_ON_HOLD';
      break;
    case 'SUBSCRIPTION_STATE_PAUSED':
      isEntitled = false;
      statusReason = 'PAUSED';
      break;
    case 'SUBSCRIPTION_STATE_EXPIRED':
    default:
      isEntitled = false;
      statusReason = 'EXPIRED';
      break;
  }

  return {
    productId,
    basePlanId,
    subscriptionState,
    statusReason,
    isEntitled: isEntitled && productId === EXPECTED_PRODUCT_ID,
    expiryTimeMillis,
    autoRenewEnabled,
    isAcknowledged: acknowledgementState === 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED'
  };
}

/**
 * İstemciden gelen abonelik doğrulama isteğini işler.
 */
async function verifySubscriptionPurchase(payload) {
  const {
    packageName,
    productId,
    purchaseToken,
    accountId,
    originalJson,
    signature
  } = payload || {};

  if (!packageName || packageName !== EXPECTED_PACKAGE_NAME) {
    return {
      httpStatus: 400,
      body: {
        success: false,
        verifiedByGooglePlayApi: false,
        isEntitled: false,
        errorCode: 'INVALID_PACKAGE_NAME',
        message: `Geçersiz uygulama paketi. Beklenen: ${EXPECTED_PACKAGE_NAME}`
      }
    };
  }

  if (!productId || productId !== EXPECTED_PRODUCT_ID) {
    return {
      httpStatus: 400,
      body: {
        success: false,
        verifiedByGooglePlayApi: false,
        isEntitled: false,
        errorCode: 'INVALID_PRODUCT_ID',
        message: `Geçersiz abonelik ürün kimliği. Beklenen: ${EXPECTED_PRODUCT_ID}`
      }
    };
  }

  if (!purchaseToken || typeof purchaseToken !== 'string' || purchaseToken.trim().length < 10) {
    return {
      httpStatus: 400,
      body: {
        success: false,
        verifiedByGooglePlayApi: false,
        isEntitled: false,
        errorCode: 'INVALID_PURCHASE_TOKEN',
        message: 'Geçersiz satın alma anahtarı (purchaseToken).'
      }
    };
  }

  // Sunucuda GOOGLE_PLAY_PUBLIC_KEY tanımlıysa ve originalJson + signature gönderildiyse RSA imzasını kontrol et
  const playPublicKey = process.env.GOOGLE_PLAY_PUBLIC_KEY || '';
  if (playPublicKey && originalJson && signature) {
    const sigValid = verifyPlayRsaSignature(playPublicKey, originalJson, signature);
    if (!sigValid) {
      return {
        httpStatus: 403,
        body: {
          success: false,
          verifiedByGooglePlayApi: false,
          isEntitled: false,
          errorCode: 'INVALID_SIGNATURE',
          message: 'Google Play RSA satın alma imzası doğrulanamadı.'
        }
      };
    }
  }

  // Anti-replay kontrolü: Bu token başka bir hesaba/cihaza bağlı mı?
  const existingRecord = db.getSubscriptionByToken(purchaseToken);
  if (
    existingRecord &&
    existingRecord.accountId &&
    accountId &&
    existingRecord.accountId !== accountId
  ) {
    return {
      httpStatus: 409,
      body: {
        success: false,
        verifiedByGooglePlayApi: false,
        isEntitled: false,
        errorCode: 'TOKEN_BOUND_TO_DIFFERENT_ACCOUNT',
        message: 'Bu satın alma anahtarı başka bir kullanıcı hesabıyla ilişkilendirilmiş.'
      }
    };
  }

  // Google Play Developer API Servis Hesabı kontrolü
  const serviceAccount = loadServiceAccountCredentials();
  if (!serviceAccount || !serviceAccount.client_email || !serviceAccount.private_key) {
    // ÖNEMLİ KURAL: Servis hesabı yapılandırılmamışsa SAHTE DOĞRULAMA ÜRETME!
    return {
      httpStatus: 200,
      body: {
        success: true,
        serverVerificationConfigured: false,
        verifiedByGooglePlayApi: false,
        isEntitled: false,
        errorCode: 'SERVICE_ACCOUNT_NOT_CONFIGURED',
        message:
          'Sunucu tarafı Google Play Developer API servis hesabı (GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) henüz yapılandırılmadı.'
      }
    };
  }

  try {
    const accessToken = await fetchGoogleAccessToken(serviceAccount);
    const subV2Response = await querySubscriptionsV2FromGooglePlay(
      packageName,
      purchaseToken,
      accessToken
    );

    const evaluation = evaluateGooglePlaySubscriptionState(subV2Response);

    const saved = db.bindAndSaveSubscription({
      purchaseToken,
      accountId: accountId || '',
      packageName,
      productId: evaluation.productId || productId,
      basePlanId: evaluation.basePlanId || EXPECTED_BASE_PLAN_ID,
      subscriptionState: evaluation.subscriptionState,
      statusReason: evaluation.statusReason,
      isEntitled: evaluation.isEntitled,
      expiryTimeMillis: evaluation.expiryTimeMillis,
      autoRenewEnabled: evaluation.autoRenewEnabled,
      verifiedByGooglePlayApi: true
    });

    return {
      httpStatus: 200,
      body: {
        success: true,
        serverVerificationConfigured: true,
        verifiedByGooglePlayApi: true,
        isEntitled: saved.isEntitled,
        subscriptionState: saved.subscriptionState,
        statusReason: saved.statusReason,
        expiryTimeMillis: saved.expiryTimeMillis,
        autoRenewEnabled: saved.autoRenewEnabled,
        productId: saved.productId,
        basePlanId: saved.basePlanId
      }
    };
  } catch (err) {
    if (err.code === 'TOKEN_BOUND_TO_DIFFERENT_ACCOUNT') {
      return {
        httpStatus: 409,
        body: {
          success: false,
          verifiedByGooglePlayApi: false,
          isEntitled: false,
          errorCode: 'TOKEN_BOUND_TO_DIFFERENT_ACCOUNT',
          message: err.message
        }
      };
    }

    return {
      httpStatus: err.statusCode === 404 ? 404 : 502,
      body: {
        success: false,
        serverVerificationConfigured: true,
        verifiedByGooglePlayApi: false,
        isEntitled: false,
        errorCode: 'GOOGLE_PLAY_API_ERROR',
        message: err.message || 'Google Play Developer API doğrulaması sırasında hata oluştu.'
      }
    };
  }
}

module.exports = {
  EXPECTED_PACKAGE_NAME,
  EXPECTED_PRODUCT_ID,
  EXPECTED_BASE_PLAN_ID,
  evaluateGooglePlaySubscriptionState,
  verifySubscriptionPurchase,
  verifyPlayRsaSignature
};
