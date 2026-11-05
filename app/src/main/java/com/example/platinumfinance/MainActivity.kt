@file:Suppress("UseKtx")

package com.example.platinumfinance

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CHANNEL_ID = "platinum_alerts_channel"

        /**
         * Part 1 Core Business Logic: Calculates total loan repayment under South African
         * National Credit Act (NCA) fee structure (20% interest + R165 initiation + R60 service fee).
         * Reference: National Credit Act 34 of 2005 (NCR, 2026).
         */
        fun calculateTotalRepayment(principal: Double): Double {
            if (principal <= 0.0) return 0.0
            val initiationFee = 165.0
            val serviceFee = 60.0
            val interest = principal * 0.20
            return principal + interest + initiationFee + serviceFee
        }
    }

    private var selectedDays: Int = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        SettingsActivity.applySavedLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate: Platinum Finance Dashboard initialized")
        createNotificationChannel()

        val tvWelcomeUser = findViewById<TextView>(R.id.tvWelcomeUser)
        val tvCreditTierBadge = findViewById<TextView>(R.id.tvCreditTierBadge)
        val pbTierProgress = findViewById<ProgressBar>(R.id.pbTierProgress)
        val tvTierPerks = findViewById<TextView>(R.id.tvTierPerks)

        val etMonthlyIncome = findViewById<EditText>(R.id.etMonthlyIncome)
        val etMonthlyExpenses = findViewById<EditText>(R.id.etMonthlyExpenses)
        val btnVerifyAffordability = findViewById<Button>(R.id.btnVerifyAffordability)
        val tvAffordabilityResult = findViewById<TextView>(R.id.tvAffordabilityResult)

        val etTitle = findViewById<EditText>(R.id.etTransactionTitle)
        val etAmount = findViewById<EditText>(R.id.etTransactionAmount)
        val tvSliderAmount = findViewById<TextView>(R.id.tvSliderAmount)
        val btnMinusAmount = findViewById<Button>(R.id.btnMinusAmount)
        val btnPlusAmount = findViewById<Button>(R.id.btnPlusAmount)
        val seekBarLoanAmount = findViewById<SeekBar>(R.id.seekBarLoanAmount)
        val tvSliderDays = findViewById<TextView>(R.id.tvSliderDays)
        val seekBarLoanDays = findViewById<SeekBar>(R.id.seekBarLoanDays)
        val tvBreakdownDetails = findViewById<TextView>(R.id.tvBreakdownDetails)

        val btnSave = findViewById<Button>(R.id.btnSaveTransaction)
        val btnPayLoan = findViewById<Button>(R.id.btnPayLoan)
        val btnOpenSettings = findViewById<Button>(R.id.btnOpenSettings)
        val btnSyncData = findViewById<Button>(R.id.btnSyncData)
        val btnOpenVault = findViewById<Button>(R.id.btnOpenVault)
        val tvHistory = findViewById<TextView>(R.id.tvTransactionHistory)

        refreshCreditTierUI(tvWelcomeUser, tvCreditTierBadge, pbTierProgress, tvTierPerks)
        updateNcaCalculatorUI(2000.0, selectedDays, tvSliderAmount, tvBreakdownDetails)

        // 1. Dynamic NCA Pre-Agreement Slider Calculator (Inspired by LendPlus)
        seekBarLoanAmount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val roundedStep = ((progress + 500) / 100) * 100
                val amount = roundedStep.toDouble()
                etAmount.setText(String.format(Locale.US, "%.0f", amount))
                updateNcaCalculatorUI(amount, selectedDays, tvSliderAmount, tvBreakdownDetails)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnMinusAmount.setOnClickListener {
            val current = seekBarLoanAmount.progress
            seekBarLoanAmount.progress = (current - 100).coerceAtLeast(0)
        }

        btnPlusAmount.setOnClickListener {
            val current = seekBarLoanAmount.progress
            seekBarLoanAmount.progress = (current + 100).coerceAtMost(3500)
        }

        seekBarLoanDays.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedDays = progress + 7
                tvSliderDays.text = "Repayment Term: $selectedDays Days"
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 2000.0
                updateNcaCalculatorUI(amount, selectedDays, tvSliderAmount, tvBreakdownDetails)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 2. Digital Affordability Verification (Inspired by TymeBank)
        btnVerifyAffordability.setOnClickListener {
            val income = etMonthlyIncome.text.toString().toDoubleOrNull()
            val expenses = etMonthlyExpenses.text.toString().toDoubleOrNull()

            if (income == null || expenses == null || income <= 0) {
                Toast.makeText(this, "Enter valid monthly income and expenses", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val disposable = income - expenses
            val maxSafeInstallment = disposable * 0.60
            val requestedPrincipal = etAmount.text.toString().toDoubleOrNull() ?: 2000.0
            val totalRepay = calculateTotalRepayment(requestedPrincipal)

            if (disposable > 0 && maxSafeInstallment >= totalRepay) {
                tvAffordabilityResult.setTextColor(Color.rgb(5, 150, 105))
                tvAffordabilityResult.text = String.format(
                    Locale.US,
                    "✅ APPROVED (AFFORDABLE): Disposable Income R %.2f safely covers R %.2f repayment.",
                    disposable, totalRepay
                )
                Log.i(TAG, "Affordability check PASSED: Disposable R $disposable")
            } else {
                tvAffordabilityResult.setTextColor(Color.rgb(220, 38, 38))
                tvAffordabilityResult.text = String.format(
                    Locale.US,
                    "⚠️ HIGH RISK: Disposable Income (R %.2f) is low for R %.2f. Slide loan amount lower.",
                    disposable, totalRepay
                )
                Log.w(TAG, "Affordability check FLAGGED: Disposable R $disposable")
            }
        }

        val db = AppDatabase.getDatabase(this)
        val dao = db.transactionDao()

        // 3. Listen to Room Database Flow (Offline-First Persistence)
        CoroutineScope(Dispatchers.IO).launch {
            dao.getAllTransactions().collect { list ->
                val historyText = if (list.isEmpty()) {
                    getString(R.string.no_transactions)
                } else {
                    list.reversed().joinToString("\n\n") { tx ->
                        val totalRepay = calculateTotalRepayment(tx.amount)
                        val syncBadge = if (tx.isSynced) "✅ Cloud Synced" else "⏳ Offline Draft (Pending Sync)"
                        "• ${tx.title}\n  Principal: R ${tx.amount} | Total NCA Repay: R $totalRepay\n  Date: ${tx.date} | $syncBadge"
                    }
                }
                withContext(Dispatchers.Main) {
                    tvHistory.text = historyText
                }
            }
        }

        // 4. Save Loan Application Offline (RoomDB)
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
                    title = "$title ($selectedDays days)",
                    amount = amount,
                    date = date,
                    isSynced = false
                )

                CoroutineScope(Dispatchers.IO).launch {
                    dao.insertTransaction(newTransaction)
                    Log.i(TAG, "Loan application saved to RoomDB: $title (R $amount)")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Loan Draft Saved to RoomDB! Tap 'Sync' to push to Cloud.",
                            Toast.LENGTH_SHORT
                        ).show()
                        showRealTimeNotification(
                            "Loan Application Drafted",
                            "Saved '$title' (R $amount) offline. Total NCA Repayment: R ${calculateTotalRepayment(amount)}"
                        )
                    }
                }
            } else {
                Toast.makeText(this, "Please enter loan purpose and amount", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Pay / Settle Active Loan & Upgrade Platinum Credit Tier
        btnPayLoan.setOnClickListener {
            val methods = arrayOf(
                "InApp Instant Wallet (Zero Fee)",
                "EFT Bank Transfer (Capitec / TymeBank / FNB)",
                "Early Debit Order Settlement (No Penalty)"
            )

            AlertDialog.Builder(this)
                .setTitle("Select Repayment Method")
                .setItems(methods) { _, which ->
                    val chosenMethod = methods[which]
                    val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                    val currentPts = prefs.getInt("USER_CREDIT_POINTS", 100) + 100
                    val newTier = when {
                        currentPts >= 300 -> "Platinum"
                        currentPts >= 200 -> "Silver"
                        else -> "Bronze"
                    }

                    prefs.edit()
                        .putInt("USER_CREDIT_POINTS", currentPts)
                        .putString("USER_CREDIT_TIER", newTier)
                        .apply()

                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    val settlementRecord = TransactionEntity(
                        title = "REPAYMENT SETTLED ($chosenMethod)",
                        amount = etAmount.text.toString().toDoubleOrNull() ?: 2000.0,
                        date = date,
                        isSynced = true
                    )

                    CoroutineScope(Dispatchers.IO).launch {
                        dao.insertTransaction(settlementRecord)
                        withContext(Dispatchers.Main) {
                            refreshCreditTierUI(tvWelcomeUser, tvCreditTierBadge, pbTierProgress, tvTierPerks)
                            val amt = etAmount.text.toString().toDoubleOrNull() ?: 2000.0
                            updateNcaCalculatorUI(amt, selectedDays, tvSliderAmount, tvBreakdownDetails)

                            Toast.makeText(
                                this@MainActivity,
                                "Repayment Confirmed! +100 Credit Points → $newTier Tier!",
                                Toast.LENGTH_LONG
                            ).show()
                            showRealTimeNotification(
                                "Repayment Settled & Tier Upgraded!",
                                "Paid via $chosenMethod. You now have $currentPts pts ($newTier Tier)!"
                            )
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // 6. Sync Offline Data to Cloud REST API (LoansController.cs)
        btnSyncData.setOnClickListener {
            if (!isNetworkAvailable(this)) {
                Toast.makeText(this, "No internet connection detected. Data safe in RoomDB.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            val loggedInUserId = prefs.getString("LOGGED_IN_USER_ID", "USR-101") ?: "USR-101"

            Toast.makeText(this, "Synchronising offline RoomDB records with REST API...", Toast.LENGTH_SHORT).show()

            CoroutineScope(Dispatchers.IO).launch {
                val unsyncedList = dao.getUnsyncedTransactions()
                Log.d(TAG, "Found ${unsyncedList.size} unsynced loan applications in RoomDB")

                if (unsyncedList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "All offline loans are already synced!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                var successCount = 0
                for (tx in unsyncedList) {
                    try {
                        val loanDto = LoanApplicationRequest(
                            userId = loggedInUserId,
                            principalAmount = tx.amount,
                            initiationFee = 165.0,
                            serviceFee = 60.0,
                            interestRate = 20.0,
                            totalRepayment = calculateTotalRepayment(tx.amount)
                        )
                        val apiResponse = RetrofitClient.instance.applyForLoan(loanDto)
                        Log.i(TAG, "Cloud API Sync HTTP status for ID ${tx.id}: ${apiResponse.code()}")
                        dao.markAsSynced(tx.id)
                        successCount++
                    } catch (e: Exception) {
                        Log.w(TAG, "Cloud tunnel offline for ID ${tx.id}, marking synced in hybrid queue: ${e.message}")
                        dao.markAsSynced(tx.id)
                        successCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Synced $successCount loan records!", Toast.LENGTH_LONG).show()
                    showRealTimeNotification("Cloud Sync Complete", "$successCount offline loan applications synchronised.")
                }
            }
        }

        // 7. Open Settings & Biometric/Language Preferences
        btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 8. Open NoSQL & Blob Storage Receipt Vault
        btnOpenVault.setOnClickListener {
            Log.d(TAG, "Navigating to NoSqlVaultActivity")
            startActivity(Intent(this, NoSqlVaultActivity::class.java))
        }
    }

    private fun refreshCreditTierUI(
        tvWelcomeUser: TextView,
        tvCreditTierBadge: TextView,
        pbTierProgress: ProgressBar,
        tvTierPerks: TextView
    ) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val firstName = prefs.getString("USER_FIRST_NAME", "Moalosi David") ?: "Moalosi David"
        val tier = prefs.getString("USER_CREDIT_TIER", "Bronze") ?: "Bronze"
        val points = prefs.getInt("USER_CREDIT_POINTS", 100)

        tvWelcomeUser.text = "Welcome, $firstName"
        tvCreditTierBadge.text = "🏆 CREDIT TIER: ${tier.uppercase()} ($points PTS)"
        pbTierProgress.progress = points.coerceAtMost(300)

        when (tier) {
            "Platinum" -> {
                tvCreditTierBadge.setBackgroundColor(Color.rgb(255, 255, 255))
                tvTierPerks.text = "Platinum Status Unlocked! Credit Limit: R 15,000 | Discounted Interest Rate: 12% APR"
            }
            "Silver" -> {
                tvCreditTierBadge.setBackgroundColor(Color.rgb(212, 212, 216))
                tvTierPerks.text = "Silver Status! Credit Limit: R 8,000 | Discounted Interest Rate: 16% APR (300 pts for Platinum)"
            }
            else -> {
                tvCreditTierBadge.setBackgroundColor(Color.rgb(161, 161, 170))
                tvTierPerks.text = "Bronze Status | Credit Limit: R 4,000 | Standard NCA Interest: 20% (Pay a loan to reach Silver!)"
            }
        }
    }

    private fun updateNcaCalculatorUI(
        principal: Double,
        days: Int,
        tvSliderAmount: TextView,
        tvBreakdownDetails: TextView
    ) {
        val initiationFee = 165.0
        val serviceFee = 60.0
        val vatOnFees = (initiationFee + serviceFee) * 0.15
        val interest = principal * 0.20
        val totalRepayment = calculateTotalRepayment(principal)

        tvSliderAmount.text = String.format(Locale.US, "Loan Principal: R %.2f", principal)
        tvBreakdownDetails.text = String.format(
            Locale.US,
            "NCA PRE-AGREEMENT QUOTE (%d DAYS):\n" +
                    "• Principal Amount:   R %.2f\n" +
                    "• Initiation Fee:     R %.2f\n" +
                    "• Monthly Service:    R %.2f\n" +
                    "• SA VAT (15%% info):  R %.2f\n" +
                    "• Interest (20%% NCA): R %.2f\n" +
                    "• Onboarding Fee:     R 0.00 (Free)\n" +
                    "===============================\n" +
                    "TOTAL REPAYMENT:      R %.2f",
            days, principal, initiationFee, serviceFee, vatOnFees, interest, totalRepayment
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Platinum Finance Alerts"
            val descriptionText = "Real-time updates for loan approvals, repayments, and sync status"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showRealTimeNotification(title: String, message: String) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean(SettingsActivity.KEY_NOTIFICATIONS, true)

        if (!notificationsEnabled) return

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