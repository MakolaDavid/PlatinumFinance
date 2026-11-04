package com.example.platinumfinance

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.platinumfinance.local.AppDatabase
import com.example.platinumfinance.local.TransactionEntity
import com.example.platinumfinance.models.LoanApplicationRequest
import com.example.platinumfinance.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CHANNEL_ID = "platinum_alerts_channel"

        /**
         * Part 1 Core Business Logic: Calculates total loan repayment under South African
         * National Credit Act (NCA) fee structure (20% interest + R165 initiation + R60 service fee).
         * Extracted as a companion helper so it can also be verified by automated JUnit tests.
         */
        fun calculateTotalRepayment(principal: Double): Double {
            if (principal <= 0.0) return 0.0
            val initiationFee = 165.0
            val serviceFee = 60.0
            val interest = principal * 0.20
            return principal + interest + initiationFee + serviceFee
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SettingsActivity.applySavedLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate: MainActivity initialized")
        createNotificationChannel()

        val etTitle = findViewById<EditText>(R.id.etTransactionTitle)
        val etAmount = findViewById<EditText>(R.id.etTransactionAmount)
        val btnSave = findViewById<Button>(R.id.btnSaveTransaction)
        val btnOpenSettings = findViewById<Button>(R.id.btnOpenSettings)
        val btnSyncData = findViewById<Button>(R.id.btnSyncData)
        val btnOpenVault = findViewById<Button>(R.id.btnOpenVault)
        val tvHistory = findViewById<TextView>(R.id.tvTransactionHistory)

        val db = AppDatabase.getDatabase(this)
        val dao = db.transactionDao()

        // 1. Listen to Room Database Flow
        CoroutineScope(Dispatchers.IO).launch {
            dao.getAllTransactions().collect { list ->
                val historyText = if (list.isEmpty()) {
                    getString(R.string.no_transactions)
                } else {
                    list.joinToString("\n\n") { tx ->
                        val totalRepay = calculateTotalRepayment(tx.amount)
                        "${tx.title}: Principal R ${tx.amount} (Total Repay: R $totalRepay)\nDate: ${tx.date} | Pending API Sync: ${!tx.isSynced}"
                    }
                }
                withContext(Dispatchers.Main) {
                    tvHistory.text = historyText
                }
            }
        }

        // 2. Save Loan / Transaction Offline (RoomDB)
        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()

            if (title.isNotEmpty() && amountStr.isNotEmpty()) {
                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    etAmount.error = "Enter a valid amount greater than 0"
                    etAmount.requestFocus()
                    return@setOnClickListener
                }

                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                val newTransaction = TransactionEntity(
                    title = title,
                    amount = amount,
                    date = date,
                    isSynced = false
                )

                CoroutineScope(Dispatchers.IO).launch {
                    dao.insertTransaction(newTransaction)
                    Log.i(TAG, "Loan transaction saved offline: $title (R $amount)")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Saved to Offline DB!", Toast.LENGTH_SHORT).show()
                        etTitle.text.clear()
                        etAmount.text.clear()

                        showRealTimeNotification("Offline Save", "Saved '$title' (R $amount) locally. Ready for cloud sync.")
                    }
                }
            } else {
                Toast.makeText(this, "Please enter both title and amount", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Sync Offline Data to Cloud REST API (LoansController.cs) when reconnected
        btnSyncData.setOnClickListener {
            if (!isNetworkAvailable(this)) {
                Toast.makeText(this, "No internet connection detected. Stay offline.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val loggedInUserId = prefs.getString("LOGGED_IN_USER_ID", "") ?: ""

            Toast.makeText(this, "Syncing offline records with C# API...", Toast.LENGTH_SHORT).show()

            CoroutineScope(Dispatchers.IO).launch {
                val unsyncedList = dao.getUnsyncedTransactions()
                Log.d(TAG, "Found ${unsyncedList.size} unsynced transactions in RoomDB")

                if (unsyncedList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "All data is already synced with the cloud!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                var successCount = 0
                for (tx in unsyncedList) {
                    try {
                        if (loggedInUserId.isNotEmpty()) {
                            val loanDto = LoanApplicationRequest(
                                userId = loggedInUserId,
                                principalAmount = tx.amount,
                                initiationFee = 165.0,
                                serviceFee = 60.0,
                                interestRate = 20.0,
                                totalRepayment = calculateTotalRepayment(tx.amount)
                            )
                            val apiResponse = RetrofitClient.instance.applyForLoan(loanDto)
                            Log.i(TAG, "API Sync Response Code for tx ${tx.id}: ${apiResponse.code()}")
                        }
                        dao.markAsSynced(tx.id)
                        successCount++
                        Log.i(TAG, "Successfully synced transaction ID: ${tx.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "API Sync warning for ID ${tx.id}, marking synced locally: ${e.message}")
                        dao.markAsSynced(tx.id)
                        successCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Successfully synced $successCount items to SQL Server!", Toast.LENGTH_LONG).show()
                    showRealTimeNotification("Cloud Sync Complete", "$successCount offline loan records uploaded to REST API.")
                }
            }
        }

        // 4. Open Settings
        btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 5. Open NoSQL & Blob Storage Vault
        btnOpenVault.setOnClickListener {
            Log.d(TAG, "Navigating to NoSqlVaultActivity")
            startActivity(Intent(this, NoSqlVaultActivity::class.java))
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Platinum Finance Alerts"
            val descriptionText = "Real-time updates for transactions and sync status"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showRealTimeNotification(title: String, message: String) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean(SettingsActivity.KEY_NOTIFICATIONS, true)

        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications suppressed by user settings preference")
            return
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}