package com.zipbug.base.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.databinding.ActivitySettingsBinding
import com.zipbug.base.security.SecretStore

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val secrets = SecretStore(this)
        val prefs = getSharedPreferences(
            "zipbug.settings",
            MODE_PRIVATE
        )

        binding.openai.setText(secrets.get("openai"))
        binding.gemini.setText(secrets.get("gemini"))
        binding.anthropic.setText(secrets.get("anthropic"))

        val home = prefs.getString(
            "termuxHome",
            "/data/data/com.termux/files/home"
        )!!

        binding.termuxHome.setText(home)
        binding.projectRoot.setText(
            prefs.getString(
                "projectRoot",
                "$home/OpenDots/Zip_Bug"
            )
        )

        binding.save.setOnClickListener {
            secrets.put(
                "openai",
                binding.openai.text.toString()
            )
            secrets.put(
                "gemini",
                binding.gemini.text.toString()
            )
            secrets.put(
                "anthropic",
                binding.anthropic.text.toString()
            )

            prefs.edit()
                .putString(
                    "termuxHome",
                    binding.termuxHome.text.toString()
                )
                .putString(
                    "projectRoot",
                    binding.projectRoot.text.toString()
                )
                .apply()

            Snackbar.make(
                binding.root,
                "Encrypted settings saved",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
}
