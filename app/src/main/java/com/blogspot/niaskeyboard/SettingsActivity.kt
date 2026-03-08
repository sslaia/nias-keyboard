package com.blogspot.niaskeyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvLangLabel: TextView
    private lateinit var checkDarkMode: CheckBox
    private lateinit var btnEnable: Button
    private lateinit var tvFooter: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("nias_prefs", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        
        // Apply Dark Mode before super.onCreate
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        // Initialize Views
        tvTitle = findViewById(R.id.tv_title)
        tvSubtitle = findViewById(R.id.tv_subtitle)
        tvLangLabel = findViewById(R.id.tv_lang_label)
        checkDarkMode = findViewById(R.id.check_dark_mode)
        btnEnable = findViewById(R.id.btn_enable_ime)
        tvFooter = findViewById(R.id.tv_footer)

        // Load saved settings
        checkDarkMode.isChecked = isDarkMode
        val savedLang = prefs.getString("app_lang", "en") ?: "en"
        updateLanguageUI(savedLang)

        checkDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("dark_mode", isChecked) }
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            // recreate() is often needed to refresh the theme completely
            recreate()
        }

        findViewById<Button>(R.id.btn_enable_ime).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        // Language Buttons
        findViewById<Button>(R.id.btn_lang_nia).setOnClickListener {
            prefs.edit().putString("app_lang", "nia").apply()
            updateLanguageUI("nia")
        }
        findViewById<Button>(R.id.btn_lang_id).setOnClickListener {
            prefs.edit().putString("app_lang", "id").apply()
            updateLanguageUI("id")
        }
        findViewById<Button>(R.id.btn_lang_en).setOnClickListener {
            prefs.edit().putString("app_lang", "en").apply()
            updateLanguageUI("en")
        }
    }

    private fun updateLanguageUI(lang: String) {
        when (lang) {
            "nia" -> {
                tvTitle.text = "Fafa Wanura Nias"
                tvSubtitle.text = "Ya'ahowu!"
                tvLangLabel.text = "Halö li:"
                checkDarkMode.text = "Orifi Mode Ogömi"
                btnEnable.text = "Ae Orifi Wafa Wanura"
                tvFooter.text = "Fa'anö stelan Android"
            }
            "id" -> {
                tvTitle.text = "Keyboard Nias"
                tvSubtitle.text = "Ya'ahowu!"
                tvLangLabel.text = "Pilih Bahasa:"
                checkDarkMode.text = "Gunakan Mode Gelap"
                btnEnable.text = "Aktifkan Keyboard"
                tvFooter.text = "Atur di Pengaturan Android"
            }
            else -> { // English
                tvTitle.text = "Nias Keyboard"
                tvSubtitle.text = "Ya'ahowu!"
                tvLangLabel.text = "Choose Language:"
                checkDarkMode.text = "Enable Dark Mode"
                btnEnable.text = "Activate Keyboard"
                tvFooter.text = "Configure in Android Settings"
            }
        }
    }
}