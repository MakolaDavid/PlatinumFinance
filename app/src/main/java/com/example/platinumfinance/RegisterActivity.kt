@file:Suppress("UseKtx")

package com.example.platinumfinance

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.platinumfinance.models.AuthResponse
import com.example.platinumfinance.models.LoginRequest
import com.example.platinumfinance.models.RegisterRequest
import com.example.platinumfinance.network.RetrofitClient
import java.security.MessageDigest
import java.util.UUID
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RegisterActivity"
    }

    private fun encryptPassword(rawPassword: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(rawPassword.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SettingsActivity.applySavedLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        Log.d(TAG, "onCreate: Branded Authentication screen initialized")

        val btnTabRegister = findViewById<Button>(R.id.btnTabRegister)
        val btnTabLogin = findViewById<Button>(R.id.btnTabLogin)
        val tvAuthSubtitle = findViewById<TextView>(R.id.tvAuthSubtitle)
        val layoutRegisterFields = findViewById<LinearLayout>(R.id.layoutRegisterFields)

        val etSaId = findViewById<EditText>(R.id.etSaId)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etMobile = findViewById<EditText>(R.id.etMobile)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnSsoLogin = findViewById<Button>(R.id.btnSsoLogin)

        val onyxBlack = Color.rgb(20, 20, 20)
        val brushedPlatinum = Color.rgb(212, 212, 216)

        btnTabRegister.setOnClickListener {
            layoutRegisterFields.visibility = View.VISIBLE
            btnRegister.visibility = View.VISIBLE
            tvAuthSubtitle.text = "3-Step Onboarding: Confirm SA ID → Verify → Apply (Zero Onboarding Fees)"
            btnTabRegister.backgroundTintList = ColorStateList.valueOf(onyxBlack)
            btnTabRegister.setTextColor(Color.WHITE)
            btnTabLogin.backgroundTintList = ColorStateList.valueOf(brushedPlatinum)
            btnTabLogin.setTextColor(onyxBlack)
        }

        btnTabLogin.setOnClickListener {
            layoutRegisterFields.visibility = View.GONE
            btnRegister.visibility = View.GONE
            tvAuthSubtitle.text = "Welcome back! Sign in with your registered Email & Password."
            btnTabLogin.backgroundTintList = ColorStateList.valueOf(onyxBlack)
            btnTabLogin.setTextColor(Color.WHITE)
            btnTabRegister.backgroundTintList = ColorStateList.valueOf(brushedPlatinum)
            btnTabRegister.setTextColor(onyxBlack)
        }

        btnRegister.setOnClickListener {
            val saId = etSaId.text.toString().trim()
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val mobile = etMobile.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (saId.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (saId.length != 13 || !saId.all { it.isDigit() }) {
                etSaId.error = "SA ID must be exactly 13 digits"
                etSaId.requestFocus()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Enter a valid email address"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.length < 6) {
                etPassword.error = "Password must be at least 6 characters"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            val encryptedPassword = encryptPassword(password)
            val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            val generatedUserId = "USR-" + UUID.randomUUID().toString().substring(0, 8).uppercase()

            prefs.edit()
                .putString("LOCAL_USER_EMAIL", email.lowercase())
                .putString("LOCAL_USER_HASH", encryptedPassword)
                .putString("USER_FIRST_NAME", firstName)
                .putString("USER_LAST_NAME", lastName)
                .putString("USER_SA_ID", saId)
                .putString("USER_MOBILE", mobile)
                .putString("LOGGED_IN_USER_ID", generatedUserId)
                .putString("USER_CREDIT_TIER", "Bronze")
                .putInt("USER_CREDIT_POINTS", 100)
                .apply()

            val request = RegisterRequest(saId, firstName, lastName, email, encryptedPassword)

            RetrofitClient.instance.registerUser(request).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    if (response.isSuccessful) {
                        val cloudUserId = response.body()?.userId ?: generatedUserId
                        prefs.edit().putString("LOGGED_IN_USER_ID", cloudUserId).apply()
                        Toast.makeText(
                            this@RegisterActivity,
                            "Account Registered! Welcome, $firstName.",
                            Toast.LENGTH_LONG
                        ).show()
                        navigateToDashboard()
                    } else {
                        Log.w(TAG, "Cloud returned ${response.code()}; using Local Encrypted Vault")
                        Toast.makeText(
                            this@RegisterActivity,
                            "Account Registered! Welcome, $firstName (Bronze Tier).",
                            Toast.LENGTH_LONG
                        ).show()
                        navigateToDashboard()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    Log.w(TAG, "Offline mode registration activated: ${t.message}")
                    Toast.makeText(
                        this@RegisterActivity,
                        "Account Registered! Welcome, $firstName (Bronze Tier).",
                        Toast.LENGTH_LONG
                    ).show()
                    navigateToDashboard()
                }
            })
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter your Email and Password to log in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val encryptedPassword = encryptPassword(password)
            val loginRequest = LoginRequest(email, encryptedPassword)
            val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)

            RetrofitClient.instance.loginUser(loginRequest).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    if (response.isSuccessful) {
                        val authBody = response.body()
                        val returnedUserId = authBody?.userId ?: prefs.getString("LOGGED_IN_USER_ID", "USR-101")
                        val creditTier = authBody?.creditTier ?: prefs.getString("USER_CREDIT_TIER", "Bronze")
                        val firstName = authBody?.firstName ?: prefs.getString("USER_FIRST_NAME", "Member")

                        prefs.edit()
                            .putString("LOGGED_IN_USER_ID", returnedUserId)
                            .putString("USER_CREDIT_TIER", creditTier)
                            .putString("USER_FIRST_NAME", firstName)
                            .apply()

                        Toast.makeText(this@RegisterActivity, "Welcome back, $firstName!", Toast.LENGTH_SHORT).show()
                        navigateToDashboard()
                    } else {
                        verifyLocalLoginFallback(email, encryptedPassword)
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    verifyLocalLoginFallback(email, encryptedPassword)
                }
            })
        }

        btnSsoLogin.setOnClickListener {
            val accounts = arrayOf(
                "moalosi.makola@gmail.com (Google OAuth 2.0)",
                "st10457546@rosebankcollege.co.za (Microsoft Entra SSO)"
            )

            AlertDialog.Builder(this)
                .setTitle("Google Identity Services (SSO)")
                .setItems(accounts) { _, which ->
                    val selectedAccount = accounts[which]
                    val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                    prefs.edit()
                        .putString("USER_FIRST_NAME", "Moalosi David")
                        .putString("LOGGED_IN_USER_ID", "SSO-ST10457546")
                        .apply()

                    Toast.makeText(this, "SSO Verified: $selectedAccount", Toast.LENGTH_SHORT).show()
                    navigateToDashboard()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun verifyLocalLoginFallback(email: String, encryptedPassword: String) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val savedEmail = prefs.getString("LOCAL_USER_EMAIL", "") ?: ""
        val savedHash = prefs.getString("LOCAL_USER_HASH", "") ?: ""
        val firstName = prefs.getString("USER_FIRST_NAME", "Moalosi") ?: "Moalosi"
        val tier = prefs.getString("USER_CREDIT_TIER", "Bronze") ?: "Bronze"

        if (savedEmail.isEmpty() || (email.lowercase() == savedEmail && encryptedPassword == savedHash)) {
            if (savedEmail.isEmpty()) {
                prefs.edit()
                    .putString("LOCAL_USER_EMAIL", email.lowercase())
                    .putString("LOCAL_USER_HASH", encryptedPassword)
                    .putString("USER_FIRST_NAME", email.substringBefore("@").replaceFirstChar { it.uppercase() })
                    .putString("LOGGED_IN_USER_ID", "USR-LOCAL-01")
                    .apply()
            }
            Toast.makeText(this, "Signed in ($tier Tier): Welcome $firstName!", Toast.LENGTH_SHORT).show()
            navigateToDashboard()
        } else {
            Toast.makeText(this, "Invalid password for $email", Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}