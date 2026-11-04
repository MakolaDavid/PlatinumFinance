package com.example.platinumfinance

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
        const val PREFS_NAME = "PlatinumPrefs"
        const val KEY_LANG = "app_language"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_DARK_MODE = "dark_mode_enabled"

        /**
         * Applies the saved South African language locale to any Activity context.
         * Reference: Android Developers (2025) Per-app language preferences & Locale configuration.
         */
        fun applySavedLocale(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val langCode = prefs.getString(KEY_LANG, "en") ?: "en"
            val locale = Locale(langCode)
            Locale.setDefault(locale)

            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        Log.d(TAG, "onCreate: SettingsActivity opened")

        val rbEnglish = findViewById<RadioButton>(R.id.rbEnglish)
        val rbSesotho = findViewById<RadioButton>(R.id.rbSesotho)
        val rbZulu = findViewById<RadioButton>(R.id.rbZulu)
        val switchNotifications = findViewById<Switch>(R.id.switchNotifications)
        val switchDarkMode = findViewById<Switch>(R.id.switchDarkMode)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)

        // Load existing saved preferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentLang = prefs.getString(KEY_LANG, "en") ?: "en"
        val notificationsOn = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        val darkModeOn = prefs.getBoolean(KEY_DARK_MODE, false)

        when (currentLang) {
            "st" -> rbSesotho.isChecked = true
            "zu" -> rbZulu.isChecked = true
            else -> rbEnglish.isChecked = true
        }
        switchNotifications.isChecked = notificationsOn
        switchDarkMode.isChecked = darkModeOn

        btnSaveSettings.setOnClickListener {
            val selectedLang = when {
                rbSesotho.isChecked -> "st"
                rbZulu.isChecked -> "zu"
                else -> "en"
            }

            val notifyEnabled = switchNotifications.isChecked
            val darkEnabled = switchDarkMode.isChecked

            // Save user preferences persistently using SharedPreferences
            prefs.edit()
                .putString(KEY_LANG, selectedLang)
                .putBoolean(KEY_NOTIFICATIONS, notifyEnabled)
                .putBoolean(KEY_DARK_MODE, darkEnabled)
                .apply()

            Log.i(TAG, "User settings updated: lang=$selectedLang, notify=$notifyEnabled, dark=$darkEnabled")

            // Apply Dark Mode preference immediately
            if (darkEnabled) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }

            // Apply new language locale and restart MainActivity so UI refreshes immediately
            applySavedLocale(this)
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}