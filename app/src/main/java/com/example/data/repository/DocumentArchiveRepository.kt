package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.PayrollDocumentDao
import com.example.data.model.PayrollDocument
import com.example.data.model.PayrollDocumentType
import com.example.util.ExportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class DocumentArchiveRepository(
    private val context: Context,
    private val payrollDocumentDao: PayrollDocumentDao,
    private val accountIdProvider: () -> String,
    private val premiumEntitlementChecker: () -> Boolean
) {
    companion object {
        const val MAX_FILE_SIZE_BYTES: Long = 15L * 1024L * 1024L // 15 MB

        val ALLOWED_MIME_TYPES = setOf(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
        )

        private val YEAR_MONTH_REGEX = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
        private val DATE_REGEX = Regex("^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$")

        fun normalizeMimeType(rawMimeType: String?, fileName: String): String {
            val lowerMime = rawMimeType?.trim()?.lowercase() ?: ""
            if (lowerMime == "image/jpg") return "image/jpeg"
            if (lowerMime in ALLOWED_MIME_TYPES) return lowerMime

            val lowerName = fileName.trim().lowercase()
            return when {
                lowerName.endsWith(".pdf") -> "application/pdf"
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
                lowerName.endsWith(".png") -> "image/png"
                lowerName.endsWith(".webp") -> "image/webp"
                else -> lowerMime
            }
        }

        /**
         * Dosya içeriğinin gerçek başlık baytlarını (magic bytes) kontrol ederek
         * sahte uzantılı veya bozuk dosyaların yüklenmesini engeller.
         */
        fun isValidFileSignature(bytes: ByteArray, normalizedMimeType: String): Boolean {
            if (bytes.size < 4) return false
            return when (normalizedMimeType) {
                "application/pdf" -> {
                    // %PDF-
                    bytes.size >= 5 &&
                        bytes[0] == 0x25.toByte() &&
                        bytes[1] == 0x50.toByte() &&
                        bytes[2] == 0x44.toByte() &&
                        bytes[3] == 0x46.toByte() &&
                        bytes[4] == 0x2D.toByte()
                }
                "image/jpeg" -> {
                    // FF D8 FF
                    bytes[0] == 0xFF.toByte() &&
                        bytes[1] == 0xD8.toByte() &&
                        bytes[2] == 0xFF.toByte()
                }
                "image/png" -> {
                    // 89 50 4E 47 0D 0A 1A 0A
                    bytes.size >= 8 &&
                        bytes[0] == 0x89.toByte() &&
                        bytes[1] == 0x50.toByte() &&
                        bytes[2] == 0x4E.toByte() &&
                        bytes[3] == 0x47.toByte()
                }
                "image/webp" -> {
                    // RIFF....WEBP
                    bytes.size >= 12 &&
                        bytes[0] == 'R'.code.toByte() &&
                        bytes[1] == 'I'.code.toByte() &&
                        bytes[2] == 'F'.code.toByte() &&
                        bytes[3] == 'F'.code.toByte() &&
                        bytes[8] == 'W'.code.toByte() &&
                        bytes[9] == 'E'.code.toByte() &&
                        bytes[10] == 'B'.code.toByte() &&
                        bytes[11] == 'P'.code.toByte()
                }
                else -> false
            }
        }

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    fun getCurrentAccountId(): String = accountIdProvider()

    fun isPremiumEntitled(): Boolean = premiumEntitlementChecker()

    /**
     * Yalnızca oturumdaki kullanıcının (`ownerAccountId`) belgelerini döndürür.
     */
    fun getDocumentsForCurrentUserFlow(): Flow<List<PayrollDocument>> {
        val ownerId = getCurrentAccountId()
        return payrollDocumentDao.getAllDocumentsForUser(ownerId)
    }

    fun getDocumentsForMonthFlow(yearMonth: String): Flow<List<PayrollDocument>> {
        val ownerId = getCurrentAccountId()
        return payrollDocumentDao.getDocumentsForUserByMonth(ownerId, yearMonth)
    }

    suspend fun getDocumentsForUserList(requesterAccountId: String = getCurrentAccountId()): Result<List<PayrollDocument>> =
        withContext(Dispatchers.IO) {
            if (!isPremiumEntitled()) {
                return@withContext Result.failure(
                    SecurityException("Maaş Bordrosu ve Belge Arşivi özelliğine erişmek için aktif Mesai Takip Premium aboneliği gereklidir.")
                )
            }
            if (requesterAccountId.isBlank() || requesterAccountId != getCurrentAccountId()) {
                return@withContext Result.failure(
                    SecurityException("Yetkisiz erişim: Başka bir kullanıcının belge arşivine erişilemez.")
                )
            }
            Result.success(payrollDocumentDao.getAllDocumentsForUserList(requesterAccountId))
        }

    /**
     * Uri üzerinden PDF veya fotoğraf belgesini doğrular, izole dizine kaydeder ve veritabanına ekler.
     */
    suspend fun addDocumentFromUri(
        uri: Uri,
        title: String,
        documentType: PayrollDocumentType,
        yearMonth: String,
        documentDate: String,
        description: String = "",
        requesterAccountId: String = getCurrentAccountId()
    ): Result<PayrollDocument> = withContext(Dispatchers.IO) {
        if (!isPremiumEntitled()) {
            return@withContext Result.failure(
                SecurityException("Belge eklemek için aktif Mesai Takip Premium aboneliği gereklidir.")
            )
        }
        val resolvedFileName = queryDisplayName(uri) ?: "belge_${System.currentTimeMillis()}"
        val rawMime = context.contentResolver.getType(uri)

        val bytesResult = readUriBytesWithLimit(uri, MAX_FILE_SIZE_BYTES)
        if (bytesResult.isFailure) {
            return@withContext Result.failure(bytesResult.exceptionOrNull()!!)
        }

        addDocumentFromBytes(
            bytes = bytesResult.getOrThrow(),
            originalFileName = resolvedFileName,
            rawMimeType = rawMime,
            title = title,
            documentType = documentType,
            yearMonth = yearMonth,
            documentDate = documentDate,
            description = description,
            requesterAccountId = requesterAccountId
        )
    }

    /**
     * Bayt dizisi olarak gelen belgeyi boyut, tür, başlık imzası ve yetki açısından denetleyerek kaydeder.
     */
    suspend fun addDocumentFromBytes(
        bytes: ByteArray,
        originalFileName: String,
        rawMimeType: String?,
        title: String,
        documentType: PayrollDocumentType,
        yearMonth: String,
        documentDate: String,
        description: String = "",
        requesterAccountId: String = getCurrentAccountId()
    ): Result<PayrollDocument> = withContext(Dispatchers.IO) {
        if (!isPremiumEntitled()) {
            return@withContext Result.failure(
                SecurityException("Belge eklemek için aktif Mesai Takip Premium aboneliği gereklidir.")
            )
        }
        val activeAccountId = getCurrentAccountId()
        if (requesterAccountId.isBlank() || requesterAccountId != activeAccountId) {
            return@withContext Result.failure(
                SecurityException("Yetkisiz işlem: Başka bir kullanıcı adına belge yüklenemez.")
            )
        }

        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Lütfen belge için geçerli bir ad girin.")
            )
        }
        if (cleanTitle.length > 120) {
            return@withContext Result.failure(
                IllegalArgumentException("Belge adı en fazla 120 karakter olabilir.")
            )
        }

        val cleanYearMonth = yearMonth.trim()
        if (!YEAR_MONTH_REGEX.matches(cleanYearMonth)) {
            return@withContext Result.failure(
                IllegalArgumentException("Geçersiz yıl/ay formatı. Lütfen YYYY-AA formatında seçin.")
            )
        }

        val cleanDate = documentDate.trim()
        if (!DATE_REGEX.matches(cleanDate)) {
            return@withContext Result.failure(
                IllegalArgumentException("Geçersiz belge tarihi formatı (YYYY-AA-GG olmalıdır).")
            )
        }

        if (bytes.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Seçilen dosya boş veya okunamadı.")
            )
        }
        if (bytes.size.toLong() > MAX_FILE_SIZE_BYTES) {
            return@withContext Result.failure(
                IllegalArgumentException("Dosya boyutu çok büyük. Maksimum 15 MB boyutunda PDF veya fotoğraf yükleyebilirsiniz.")
            )
        }

        val normalizedMime = normalizeMimeType(rawMimeType, originalFileName)
        if (normalizedMime !in ALLOWED_MIME_TYPES) {
            return@withContext Result.failure(
                IllegalArgumentException("Desteklenmeyen dosya türü. Yalnızca PDF, JPG, PNG veya WEBP formatında belge ekleyebilirsiniz.")
            )
        }

        if (!isValidFileSignature(bytes, normalizedMime)) {
            return@withContext Result.failure(
                IllegalArgumentException("Dosya içeriği bozuk veya belirtilen formatla ($normalizedMime) uyuşmuyor.")
            )
        }

        val extension = when (normalizedMime) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }

        val sanitizedDisplayFileName = sanitizeFileName(originalFileName, extension)
        val userDir = getUserIsolatedDirectory(requesterAccountId)
        val monthDir = File(userDir, cleanYearMonth)
        if (!monthDir.exists()) {
            monthDir.mkdirs()
        }

        val uniqueStoredName = "doc_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
        val targetFile = File(monthDir, uniqueStoredName)

        // Path traversal (dizin aşımı) güvenlik kontrolü
        val baseCanonical = userDir.canonicalPath
        val targetCanonical = targetFile.canonicalPath
        if (!targetCanonical.startsWith(baseCanonical)) {
            return@withContext Result.failure(
                SecurityException("Güvenlik ihlali: Geçersiz dosya yolu.")
            )
        }

        try {
            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
        } catch (e: Exception) {
            return@withContext Result.failure(
                IllegalStateException("Belge güvenli depolama alanına kaydedilirken hata oluştu: ${e.message}")
            )
        }

        val checksum = sha256Hex(bytes)
        val relativePath = "$cleanYearMonth/$uniqueStoredName"

        val document = PayrollDocument(
            ownerAccountId = requesterAccountId,
            title = cleanTitle,
            documentType = documentType.name,
            yearMonth = cleanYearMonth,
            documentDate = cleanDate,
            description = description.trim().take(500),
            fileName = sanitizedDisplayFileName,
            storedRelativePath = relativePath,
            mimeType = normalizedMime,
            fileSizeBytes = bytes.size.toLong(),
            sha256Checksum = checksum,
            createdAtEpochMs = System.currentTimeMillis()
        )

        val insertedId = payrollDocumentDao.insertDocument(document)
        Result.success(document.copy(id = insertedId))
    }

    /**
     * Belgeyi görüntülemek veya dışa aktarmak için kullanıcı yetkisini ve Premium durumunu doğrulayarak
     * güvenli yerel dosyayı döndürür. Başka bir kullanıcının belgesiyse SecurityException fırlatır.
     */
    suspend fun getAuthorizedDocumentFile(
        documentId: Long,
        requesterAccountId: String = getCurrentAccountId()
    ): Result<Pair<PayrollDocument, File>> = withContext(Dispatchers.IO) {
        if (!isPremiumEntitled()) {
            return@withContext Result.failure(
                SecurityException("Belgeyi görüntülemek için aktif Mesai Takip Premium aboneliği gereklidir.")
            )
        }
        val activeAccountId = getCurrentAccountId()
        if (requesterAccountId.isBlank() || requesterAccountId != activeAccountId) {
            return@withContext Result.failure(
                SecurityException("Yetkisiz erişim: Başka bir kullanıcının belgesine erişilemez.")
            )
        }

        val doc = payrollDocumentDao.getDocumentByIdForUser(documentId, requesterAccountId)
            ?: return@withContext Result.failure(
                SecurityException("Belge bulunamadı veya bu belgeye erişim yetkiniz yok.")
            )

        if (doc.ownerAccountId != requesterAccountId) {
            return@withContext Result.failure(
                SecurityException("Yetkisiz erişim: Bu belge başka bir kullanıcıya aittir.")
            )
        }

        val userDir = getUserIsolatedDirectory(requesterAccountId)
        val file = File(userDir, doc.storedRelativePath)
        val baseCanonical = userDir.canonicalPath
        val targetCanonical = file.canonicalPath

        if (!targetCanonical.startsWith(baseCanonical) || !file.exists() || !file.isFile) {
            return@withContext Result.failure(
                IllegalStateException("Belge dosyası cihazda bulunamadı veya silinmiş.")
            )
        }

        Result.success(doc to file)
    }

    /**
     * Belgeyi hem Room veritabanından hem de özel depolama alanından güvenli şekilde siler.
     */
    suspend fun deleteDocument(
        documentId: Long,
        requesterAccountId: String = getCurrentAccountId()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isPremiumEntitled()) {
            return@withContext Result.failure(
                SecurityException("Belge silmek için aktif Mesai Takip Premium aboneliği gereklidir.")
            )
        }
        val activeAccountId = getCurrentAccountId()
        if (requesterAccountId.isBlank() || requesterAccountId != activeAccountId) {
            return@withContext Result.failure(
                SecurityException("Yetkisiz işlem: Başka bir kullanıcının belgesi silinemez.")
            )
        }

        val doc = payrollDocumentDao.getDocumentByIdForUser(documentId, requesterAccountId)
            ?: return@withContext Result.failure(
                SecurityException("Silinecek belge bulunamadı veya bu belgeye erişim yetkiniz yok.")
            )

        val userDir = getUserIsolatedDirectory(requesterAccountId)
        val file = File(userDir, doc.storedRelativePath)
        val baseCanonical = userDir.canonicalPath
        val targetCanonical = file.canonicalPath

        if (targetCanonical.startsWith(baseCanonical) && file.exists()) {
            file.delete()
        }

        val deletedRows = payrollDocumentDao.deleteDocumentForUser(documentId, requesterAccountId)
        if (deletedRows <= 0) {
            return@withContext Result.failure(
                SecurityException("Belge silinemedi veya yetkiniz bulunmuyor.")
            )
        }
        Result.success(Unit)
    }

    /**
     * Belgeyi kullanıcının seçtiği hedef Uri'ye güvenli şekilde indirir / dışa aktarır.
     */
    suspend fun exportDocumentToUri(
        documentId: Long,
        destinationUri: Uri,
        requesterAccountId: String = getCurrentAccountId()
    ): Result<PayrollDocument> = withContext(Dispatchers.IO) {
        val authResult = getAuthorizedDocumentFile(documentId, requesterAccountId)
        if (authResult.isFailure) {
            return@withContext Result.failure(authResult.exceptionOrNull()!!)
        }
        val (doc, sourceFile) = authResult.getOrThrow()
        try {
            val outStream = context.contentResolver.openOutputStream(destinationUri)
                ?: return@withContext Result.failure(IllegalStateException("Hedef konum açılamadı."))
            outStream.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Belge dışa aktarılırken hata oluştu: ${e.message}"))
        }
    }

    /**
     * Belgeyi FileProvider üzerinden güvenli paylaşım veya görüntüleme için hazırlar.
     */
    suspend fun shareOrExportDocument(
        documentId: Long,
        requesterAccountId: String = getCurrentAccountId()
    ): Result<File> = withContext(Dispatchers.IO) {
        val authResult = getAuthorizedDocumentFile(documentId, requesterAccountId)
        if (authResult.isFailure) {
            return@withContext Result.failure(authResult.exceptionOrNull()!!)
        }
        val (doc, sourceFile) = authResult.getOrThrow()
        try {
            val exportDir = ExportHelper.getExportsDir(context)
            val exportFile = File(exportDir, doc.fileName)
            sourceFile.copyTo(exportFile, overwrite = true)
            ExportHelper.shareFile(
                context = context,
                file = exportFile,
                mimeType = doc.mimeType,
                title = "${doc.title} (${doc.yearMonth})"
            )
            Result.success(exportFile)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Belge paylaşılırken hata oluştu: ${e.message}"))
        }
    }

    private fun getUserIsolatedDirectory(accountId: String): File {
        val accountHash = sha256Hex(accountId.toByteArray()).take(24)
        val dir = File(context.filesDir, "secure_documents/user_$accountHash")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeFileName(rawName: String, fallbackExt: String): String {
        val cleaned = rawName
            .replace(Regex("[^a-zA-Z0-9ğüşöçıİĞÜŞÖÇ._-]"), "_")
            .trim('_')
            .take(80)
        if (cleaned.isBlank()) {
            return "belge_${System.currentTimeMillis()}.$fallbackExt"
        }
        return if (cleaned.contains('.')) cleaned else "$cleaned.$fallbackExt"
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readUriBytesWithLimit(uri: Uri, maxBytes: Long): Result<ByteArray> {
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(IllegalArgumentException("Seçilen dosya açılamadı."))
            input.use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var totalRead = 0L
                while (true) {
                    val read = stream.read(chunk)
                    if (read == -1) break
                    totalRead += read
                    if (totalRead > maxBytes) {
                        return Result.failure(
                            IllegalArgumentException("Dosya boyutu çok büyük. Maksimum 15 MB boyutunda PDF veya fotoğraf yükleyebilirsiniz.")
                        )
                    }
                    buffer.write(chunk, 0, read)
                }
                Result.success(buffer.toByteArray())
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Dosya okunurken hata oluştu: ${e.message}"))
        }
    }
}
