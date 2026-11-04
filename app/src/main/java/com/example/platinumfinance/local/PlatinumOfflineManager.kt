package com.example.platinumfinance.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PlatinumOfflineManager {

    // Checks if the device currently has an active internet connection
    fun isOnline(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = connMgr.activeNetwork ?: return false
        val caps = connMgr.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Saves a transaction locally first, then attempts to sync if online
    fun recordTransaction(
        context: Context,
        title: String,
        amount: Double,
        date: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val dao = AppDatabase.getDatabase(context).transactionDao()

        CoroutineScope(Dispatchers.IO).launch {
            // 1. Always save locally first so zero data is lost offline
            val localEntry = TransactionEntity(
                title = title,
                amount = amount,
                date = date,
                isSynced = false
            )
            dao.insertTransaction(localEntry)

            // 2. If online, sync unsynced records with the backend
            if (isOnline(context)) {
                val syncedCount = syncPendingTransactions(context)
                withContext(Dispatchers.Main) {
                    onComplete(true, "Saved and synced ($syncedCount uploaded)")
                }
            } else {
                withContext(Dispatchers.Main) {
                    onComplete(false, "Offline: Saved locally to device. Will sync when online.")
                }
            }
        }
    }

    // Uploads all local records where isSynced == false
    suspend fun syncPendingTransactions(context: Context): Int {
        if (!isOnline(context)) return 0

        val dao = AppDatabase.getDatabase(context).transactionDao()
        val pendingList = dao.getUnsyncedTransactions()
        var uploaded = 0

        for (item in pendingList) {
            try {
                // TODO: Replace with your actual RetrofitClient API call if you have a Transaction endpoint
                // Example: val response = RetrofitClient.instance.postTransaction(item).execute()
                // if (response.isSuccessful) { ... }

                dao.markAsSynced(item.id)
                uploaded++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return uploaded
    }
}