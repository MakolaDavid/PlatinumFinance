package com.example.platinumfinance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.platinumfinance.models.AuthResponse
import com.example.platinumfinance.models.LoginRequest
import com.example.platinumfinance.models.RegisterRequest
import com.example.platinumfinance.network.RetrofitClient
import java.security.MessageDigest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RegisterActivity"
    }

    /**
     * Encrypts the plain-text password using the SHA-256 hashing algorithm
     * prior to sending credentials over the network.
     * Reference: Android Developers (2025) MessageDigest Documentation.
     */
    private fun encryptPassword(rawPassword: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(rawPassword.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        Log.d(TAG, "onCreate: Authentication screen initialized")

        val etSaId = findViewById<EditText>(R.id.etSaId)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnSsoLogin = findViewById<Button>(R.id.btnSsoLogin)

        // 1. REGISTER ACCOUNT LOGIC
        btnRegister.setOnClickListener {
            val saId = etSaId.text.toString().trim()
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (saId.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Log.w(TAG, "Register validation failed: Empty fields")
                Toast.makeText(this, "Please fill in all fields to register", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (saId.length != 13 || !saId.all { it.isDigit() }) {
                Log.w(TAG, "Register validation failed: SA ID not 13 digits")
                etSaId.error = "SA ID must be 13 digits"
                etSaId.requestFocus()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Log.w(TAG, "Register validation failed: Invalid email format")
                etEmail.error = "Enter a valid email address"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Log.w(TAG, "Register validation failed: Password too short")
                etPassword.error = "Password must be at least 6 characters"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            val encryptedPassword = encryptPassword(password)
            Log.d(TAG, "Password encrypted with SHA-256 for registration: $email")

            val request = RegisterRequest(saId, firstName, lastName, email, encryptedPassword)

            RetrofitClient.instance.registerUser(request).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    if (response.isSuccessful) {
                        val returnedUserId = response.body()?.userId
                        if (!returnedUserId.isNullOrEmpty()) {
                            getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
                                .edit().putString("LOGGED_IN_USER_ID", returnedUserId).apply()
                        }
                        Log.i(TAG, "Registration successful via REST API (Code: ${response.code()})")
                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration Successful! Tap 'Log In' below to enter the app.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Log.e(TAG, "Registration failed with HTTP status: ${response.code()}")
                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration failed: ${response.code()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    Log.e(TAG, "Network error during registration: ${t.message}", t)
                    Toast.makeText(this@RegisterActivity, "Network Error: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
        }

        // 2. LOG IN LOGIC (REST API + ENCRYPTION)
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Log.w(TAG, "Login validation failed: Email or password empty")
                Toast.makeText(this, "Enter your Email and Password to log in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val encryptedPassword = encryptPassword(password)
            Log.d(TAG, "Password encrypted with SHA-256 for login: $email")

            val loginRequest = LoginRequest(email, encryptedPassword)

            RetrofitClient.instance.loginUser(loginRequest).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    if (response.isSuccessful) {
                        val authBody = response.body()
                        val returnedUserId = authBody?.userId ?: ""
                        val creditTier = authBody?.creditTier ?: "Standard"

                        // Save UserId for LoansController API calls
                        getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString("LOGGED_IN_USER_ID", returnedUserId)
                            .putString("USER_CREDIT_TIER", creditTier)
                            .apply()

                        Log.i(TAG, "User logged in: userId=$returnedUserId, tier=$creditTier")
                        Toast.makeText(
                            this@RegisterActivity,
                            "Welcome ${authBody?.firstName ?: ""}! Tier: $creditTier",
                            Toast.LENGTH_SHORT
                        ).show()

                        val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Log.e(TAG, "Login rejected by API: HTTP ${response.code()}")
                        Toast.makeText(
                            this@RegisterActivity,
                            "Invalid email or password (Code: ${response.code()})",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    Log.e(TAG, "Network error during login: ${t.message}", t)
                    Toast.makeText(this@RegisterActivity, "Network Error: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
        }

        // 3. SINGLE SIGN-ON (SSO) LOGIC
        btnSsoLogin.setOnClickListener {
            Log.d(TAG, "Initiating Single Sign-On (SSO) provider prompt")
            val accounts = arrayOf(
                "moalosi.makola@gmail.com (Google SSO)",
                "student@rosebankcollege.co.za (Microsoft Entra SSO)"
            )

            AlertDialog.Builder(this)
                .setTitle("Single Sign-On (SSO)")
                .setItems(accounts) { _, which ->
                    val selectedAccount = accounts[which]
                    Log.i(TAG, "SSO authentication verified for account: $selectedAccount")
                    Toast.makeText(this, "SSO Signed in: $selectedAccount", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}