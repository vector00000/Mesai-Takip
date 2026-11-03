package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PayrollDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface PayrollDocumentDao {

    @Query(
        """
        SELECT * FROM payroll_documents 
        WHERE ownerAccountId = :ownerAccountId 
        ORDER BY documentDate DESC, createdAtEpochMs DESC
        """
    )
    fun getAllDocumentsForUser(ownerAccountId: String): Flow<List<PayrollDocument>>

    @Query(
        """
        SELECT * FROM payroll_documents 
        WHERE ownerAccountId = :ownerAccountId 
        ORDER BY documentDate DESC, createdAtEpochMs DESC
        """
    )
    suspend fun getAllDocumentsForUserList(ownerAccountId: String): List<PayrollDocument>

    @Query(
        """
        SELECT * FROM payroll_documents 
        WHERE ownerAccountId = :ownerAccountId AND yearMonth = :yearMonth 
        ORDER BY documentDate DESC, createdAtEpochMs DESC
        """
    )
    fun getDocumentsForUserByMonth(ownerAccountId: String, yearMonth: String): Flow<List<PayrollDocument>>

    @Query(
        """
        SELECT * FROM payroll_documents 
        WHERE id = :documentId AND ownerAccountId = :ownerAccountId 
        LIMIT 1
        """
    )
    suspend fun getDocumentByIdForUser(documentId: Long, ownerAccountId: String): PayrollDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: PayrollDocument): Long

    @Update
    suspend fun updateDocument(document: PayrollDocument)

    @Query("DELETE FROM payroll_documents WHERE id = :documentId AND ownerAccountId = :ownerAccountId")
    suspend fun deleteDocumentForUser(documentId: Long, ownerAccountId: String): Int
}
