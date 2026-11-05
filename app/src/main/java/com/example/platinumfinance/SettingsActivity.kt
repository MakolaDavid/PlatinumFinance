package com.example.platinumfinance

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
        const val PREFS_NAME = "PlatinumFinancePrefs"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_DARK_MODE = "dark_mode_enabled"
        const val KEY_BIOMETRIC = "biometric_enabled"
        const val KEY_LANGUAGE = "app_language"

        fun applySavedLocale(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lang = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
            val locale = Locale(lang)
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

        Log.d(TAG, "onCreate: Settings screen initialized")

        val switchBiometric = findViewById<Switch>(R.id.switchBiometric)
        val switchNotifications = findViewById<Switch>(R.id.switchNotifications)
        val switchDarkMode = findViewById<Switch>(R.id.switchDarkMode)
        val rgLanguage = findViewById<RadioGroup>(R.id.rgLanguage)
        val rbEnglish = findViewById<RadioButton>(R.id.rbEnglish)
        val rbSesotho = findViewById<RadioButton>(R.id.rbSesotho)
        val rbZulu = findViewById<RadioButton>(R.id.rbZulu)
        val btnSave = findViewById<Button>(R.id.btnSaveSettings)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        switchBiometric.isChecked = prefs.getBoolean(KEY_BIOMETRIC, true)
        switchNotifications.isChecked = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        switchDarkMode.isChecked = prefs.getBoolean(KEY_DARK_MODE, false)

        when (prefs.getString(KEY_LANGUAGE, "en")) {
            "st" -> rbSesotho.isChecked = true
            "zu" -> rbZulu.isChecked = true
            else -> rbEnglish.isChecked = true
        }

        btnSave.setOnClickListener {
            val selectedLang = when (rgLanguage.checkedRadioButtonId) {
                R.id.rbSesotho -> "st"
                R.id.rbZulu -> "zu"
                else -> "en"
            }

            val darkMode = switchDarkMode.isChecked
            val notifications = switchNotifications.isChecked
            val biometric = switchBiometric.isChecked

            prefs.edit()
                .putBoolean(KEY_BIOMETRIC, biometric)
                .putBoolean(KEY_NOTIFICATIONS, notifications)
                .putBoolean(KEY_DARK_MODE, darkMode)
                .putString(KEY_LANGUAGE, selectedLang)
                .apply()

            Log.i(TAG, "Settings saved: biometric=$biometric, lang=$selectedLang, dark=$darkMode")

            AppCompatDelegate.setDefaultNightMode(
                if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )

            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}