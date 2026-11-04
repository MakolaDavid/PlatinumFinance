package com.example.platinumfinance.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM offline_transactions")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM offline_transactions WHERE isSynced = 0")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Query("UPDATE offline_transactions SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
}