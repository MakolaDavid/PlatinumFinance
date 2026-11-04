package com.example.platinumfinance.local

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "offline_transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val date: String,
    val isSynced: Boolean = false
)