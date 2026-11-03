/**
 * Mesai Takip - Güvenli Kalıcı Ödeme Veritabanı Modülü (Persistence Layer)
 * 
 * Özellikler:
 * 1. Disk üzerinde JSON tabanlı kalıcı depolama (payments.json).
 * 2. Atomik yazma (temp file + rename) ile elektrik/sunucu kesintisinde dosya bozulmasını önleme.
 * 3. Sunucu yeniden başlasa bile tüm ödeme kayıtları korunur.
 * 4. paymentId ve iyzicoToken üzerinden hızlı O(1) indeksleme.
 */

const fs = require('fs');
const path = require('path');

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const DB_FILE = process.env.DB_FILE || path.join(DATA_DIR, 'payments.json');

// Dizin yoksa oluştur
if (!fs.existsSync(DATA_DIR)) {
  try {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  } catch (err) {
    console.error('Veri dizini oluşturulamadı:', err.message);
  }
}

class PaymentDatabase {
  constructor() {
    this.payments = new Map(); // paymentId -> record
    this.tokenIndex = new Map(); // token -> paymentId
    this.loadFromDisk();
  }

  loadFromDisk() {
    try {
      if (fs.existsSync(DB_FILE)) {
        const raw = fs.readFileSync(DB_FILE, 'utf8');
        if (raw.trim()) {
          const list = JSON.parse(raw);
          if (Array.isArray(list)) {
            for (const item of list) {
              if (item && item.paymentId) {
                this.payments.set(item.paymentId, item);
                if (item.iyzicoToken) {
                  this.tokenIndex.set(item.iyzicoToken, item.paymentId);
                }
              }
            }
          }
        }
      }
    } catch (err) {
      console.error('Kalıcı ödeme veritabanı yüklenirken hata:', err.message);
    }
  }

  saveToDisk() {
    try {
      const list = Array.from(this.payments.values());
      const tempFile = `${DB_FILE}.${Date.now()}.${Math.random().toString(36).substring(7)}.tmp`;
      fs.writeFileSync(tempFile, JSON.stringify(list, null, 2), 'utf8');
      fs.renameSync(tempFile, DB_FILE);
    } catch (err) {
      console.error('Kalıcı ödeme veritabanı kaydedilirken hata:', err.message);
    }
  }

  /**
   * Yeni ödeme kaydı oluşturur ve diske yazar
   */
  createPayment({ paymentId, iyzicoToken, amount, currency, status = 'PENDING', buyer = null }) {
    const record = {
      paymentId,
      iyzicoToken: iyzicoToken || '',
      amount: parseFloat(amount),
      currency: currency || 'TRY',
      status: status || 'PENDING',
      provider: 'iyzico',
      providerTransactionId: '',
      createdAt: Date.now(),
      completedAt: 0,
      buyerName: buyer ? `${buyer.name || ''} ${buyer.surname || ''}`.trim() : ''
    };

    this.payments.set(paymentId, record);
    if (record.iyzicoToken) {
      this.tokenIndex.set(record.iyzicoToken, paymentId);
    }

    this.saveToDisk();
    return record;
  }

  /**
   * paymentId ile kayıt sorgular
   */
  getPaymentById(paymentId) {
    if (!paymentId) return null;
    return this.payments.get(paymentId) || null;
  }

  /**
   * iyzicoToken ile kayıt sorgular
   */
  getPaymentByToken(token) {
    if (!token) return null;
    const paymentId = this.tokenIndex.get(token);
    if (paymentId) {
      return this.payments.get(paymentId) || null;
    }
    // İndekste yoksa tüm listeyi tara
    for (const record of this.payments.values()) {
      if (record.iyzicoToken === token) {
        this.tokenIndex.set(token, record.paymentId);
        return record;
      }
    }
    return null;
  }

  /**
   * Ödeme durumunu günceller ve diske yazar
   */
  updatePaymentStatus(paymentId, { status, providerTransactionId = '', completedAt = null }) {
    const record = this.payments.get(paymentId);
    if (!record) return null;

    record.status = status;
    if (providerTransactionId) {
      record.providerTransactionId = providerTransactionId;
    }
    if (completedAt !== null) {
      record.completedAt = completedAt;
    } else if (status === 'SUCCESS' || status === 'FAILED' || status === 'CANCELLED') {
      record.completedAt = Date.now();
    }

    this.saveToDisk();
    return record;
  }

  /**
   * Token kaydını sonradan ilişkilendirmek için
   */
  setTokenForPayment(paymentId, token) {
    const record = this.payments.get(paymentId);
    if (record) {
      record.iyzicoToken = token;
      this.tokenIndex.set(token, paymentId);
      this.saveToDisk();
    }
    return record;
  }

  // --- Google Play Billing Abonelik Token Kayıtları & Anti-Replay Koruması ---

  initSubscriptionsStore() {
    if (this.subscriptions) return;
    this.subscriptionsFile = process.env.SUBSCRIPTIONS_DB_FILE || path.join(DATA_DIR, 'subscriptions.json');
    this.subscriptions = new Map(); // purchaseToken -> subscriptionRecord
    try {
      if (fs.existsSync(this.subscriptionsFile)) {
        const raw = fs.readFileSync(this.subscriptionsFile, 'utf8');
        if (raw.trim()) {
          const list = JSON.parse(raw);
          if (Array.isArray(list)) {
            for (const item of list) {
              if (item && item.purchaseToken) {
                this.subscriptions.set(item.purchaseToken, item);
              }
            }
          }
        }
      }
    } catch (err) {
      console.error('Abonelik veritabanı yüklenirken hata:', err.message);
    }
  }

  saveSubscriptionsToDisk() {
    this.initSubscriptionsStore();
    try {
      const list = Array.from(this.subscriptions.values());
      const tempFile = `${this.subscriptionsFile}.${Date.now()}.${Math.random().toString(36).substring(7)}.tmp`;
      fs.writeFileSync(tempFile, JSON.stringify(list, null, 2), 'utf8');
      fs.renameSync(tempFile, this.subscriptionsFile);
    } catch (err) {
      console.error('Abonelik veritabanı kaydedilirken hata:', err.message);
    }
  }

  getSubscriptionByToken(purchaseToken) {
    this.initSubscriptionsStore();
    if (!purchaseToken) return null;
    return this.subscriptions.get(purchaseToken) || null;
  }

  /**
   * Tek bir purchaseToken'ın farklı hesaplarda/cihazlarda kötüye kullanılmasını (replay) engeller.
   */
  bindAndSaveSubscription(record) {
    this.initSubscriptionsStore();
    const { purchaseToken, accountId } = record;
    if (!purchaseToken) {
      throw new Error('INVALID_PURCHASE_TOKEN');
    }

    const existing = this.subscriptions.get(purchaseToken);
    if (
      existing &&
      existing.accountId &&
      accountId &&
      existing.accountId !== accountId
    ) {
      const err = new Error('Bu satın alma anahtarı (purchaseToken) başka bir hesaba/cihaza tanımlanmış.');
      err.code = 'TOKEN_BOUND_TO_DIFFERENT_ACCOUNT';
      throw err;
    }

    const merged = {
      ...(existing || {}),
      ...record,
      accountId: accountId || (existing ? existing.accountId : ''),
      updatedAt: Date.now(),
      createdAt: existing ? existing.createdAt : Date.now()
    };

    this.subscriptions.set(purchaseToken, merged);
    this.saveSubscriptionsToDisk();
    return merged;
  }

  // --- Maaş Bordrosu ve Belge Arşivi Sunucu Tarafı Yetkilendirme & Meta Veri Deposu ---

  initDocumentsStore() {
    if (this.documents) return;
    this.documentsFile = process.env.DOCUMENTS_DB_FILE || path.join(DATA_DIR, 'documents.json');
    this.documents = new Map(); // docId -> documentRecord
    try {
      if (fs.existsSync(this.documentsFile)) {
        const raw = fs.readFileSync(this.documentsFile, 'utf8');
        if (raw.trim()) {
          const list = JSON.parse(raw);
          if (Array.isArray(list)) {
            for (const item of list) {
              if (item && item.id) {
                this.documents.set(String(item.id), item);
              }
            }
          }
        }
      }
    } catch (err) {
      console.error('Belge veritabanı yüklenirken hata:', err.message);
    }
  }

  saveDocumentsToDisk() {
    this.initDocumentsStore();
    try {
      const list = Array.from(this.documents.values());
      const tempFile = `${this.documentsFile}.${Date.now()}.${Math.random().toString(36).substring(7)}.tmp`;
      fs.writeFileSync(tempFile, JSON.stringify(list, null, 2), 'utf8');
      fs.renameSync(tempFile, this.documentsFile);
    } catch (err) {
      console.error('Belge veritabanı kaydedilirken hata:', err.message);
    }
  }

  createDocumentForUser(ownerAccountId, docPayload) {
    this.initDocumentsStore();
    if (!ownerAccountId || typeof ownerAccountId !== 'string' || !ownerAccountId.trim()) {
      const err = new Error('Kimlik doğrulaması gerekli (ownerAccountId eksik).');
      err.code = 'UNAUTHORIZED';
      throw err;
    }
    const id = docPayload.id ? String(docPayload.id) : `doc_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const record = {
      id,
      ownerAccountId: ownerAccountId.trim(),
      title: String(docPayload.title || '').trim(),
      documentType: String(docPayload.documentType || 'PAYSLIP').trim(),
      yearMonth: String(docPayload.yearMonth || '').trim(),
      documentDate: String(docPayload.documentDate || '').trim(),
      description: String(docPayload.description || '').trim(),
      fileName: String(docPayload.fileName || '').trim(),
      mimeType: String(docPayload.mimeType || 'application/pdf').trim(),
      fileSizeBytes: Number(docPayload.fileSizeBytes || 0),
      sha256Checksum: String(docPayload.sha256Checksum || '').trim(),
      createdAt: Date.now()
    };
    this.documents.set(id, record);
    this.saveDocumentsToDisk();
    return record;
  }

  getDocumentsForUser(requesterAccountId, yearMonth = null) {
    this.initDocumentsStore();
    if (!requesterAccountId || typeof requesterAccountId !== 'string' || !requesterAccountId.trim()) {
      const err = new Error('Kimlik doğrulaması gerekli.');
      err.code = 'UNAUTHORIZED';
      throw err;
    }
    const cleanRequester = requesterAccountId.trim();
    const results = [];
    for (const doc of this.documents.values()) {
      if (doc.ownerAccountId === cleanRequester) {
        if (!yearMonth || doc.yearMonth === yearMonth) {
          results.push(doc);
        }
      }
    }
    return results.sort((a, b) => (b.documentDate || '').localeCompare(a.documentDate || ''));
  }

  getDocumentByIdForUser(docId, requesterAccountId) {
    this.initDocumentsStore();
    if (!requesterAccountId || typeof requesterAccountId !== 'string' || !requesterAccountId.trim()) {
      const err = new Error('Kimlik doğrulaması gerekli.');
      err.code = 'UNAUTHORIZED';
      throw err;
    }
    const doc = this.documents.get(String(docId));
    if (!doc) {
      const err = new Error('Belge bulunamadı.');
      err.code = 'NOT_FOUND';
      throw err;
    }
    if (doc.ownerAccountId !== requesterAccountId.trim()) {
      const err = new Error('Bu belgeye erişim yetkiniz yok (başka bir kullanıcıya ait).');
      err.code = 'ACCESS_DENIED_NOT_OWNER';
      throw err;
    }
    return doc;
  }

  deleteDocumentForUser(docId, requesterAccountId) {
    const doc = this.getDocumentByIdForUser(docId, requesterAccountId);
    this.documents.delete(String(doc.id));
    this.saveDocumentsToDisk();
    return true;
  }
}

// Singleton veritabanı örneği
const dbInstance = new PaymentDatabase();

module.exports = dbInstance;
