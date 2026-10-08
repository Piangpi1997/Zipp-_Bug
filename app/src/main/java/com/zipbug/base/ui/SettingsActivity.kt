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

        // One-time convenience migration for users who pasted an
        // OpenRouter key into any old provider field.
        val savedOpenRouter = secrets.get("openrouter")
        if (savedOpenRouter.isBlank()) {
            listOf("openai", "gemini", "anthropic")
                .firstOrNull {
                    secrets.get(it).startsWith("sk-or-")
                }
                ?.let { oldField ->
                    secrets.put(
                        "openrouter",
                        secrets.get(oldField)
                    )
                    secrets.put(oldField, "")
                }
        }

        binding.openrouter.setText(secrets.get("openrouter"))
        binding.openai.setText(secrets.get("openai"))
        binding.gemini.setText(secrets.get("gemini"))
        binding.anthropic.setText(secrets.get("anthropic"))

        val home = prefs.getString(
            "termuxHome",
            "/data/data/com.termux/files/home"
        )!!

        binding.termuxHome.setText(home)

        val savedProjectRoot = prefs.getString(
            "projectRoot",
            "$home/OpenDots/Zip_Bug_Antigravity"
        )!!

        val migratedProjectRoot =
            if (savedProjectRoot == "$home/OpenDots/Zip_Bug") {
                "$home/OpenDots/Zip_Bug_Antigravity"
            } else {
                savedProjectRoot
            }

        binding.projectRoot.setText(migratedProjectRoot)
        binding.artifactExportPath.setText(
            prefs.getString(
                "artifactExportPath",
                "/storage/emulated/0/Download/Zip_Bug-debug.apk"
            )
        )

        binding.save.setOnClickListener {
            secrets.put(
                "openrouter",
                binding.openrouter.text.toString().trim()
            )
            secrets.put(
                "openai",
                binding.openai.text.toString().trim()
            )
            secrets.put(
                "gemini",
                binding.gemini.text.toString().trim()
            )
            secrets.put(
                "anthropic",
                binding.anthropic.text.toString().trim()
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
                .putString(
                    "artifactExportPath",
                    binding.artifactExportPath.text.toString()
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
