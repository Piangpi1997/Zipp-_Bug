package com.zipbug.base.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zipbug.base.databinding.ActivityMediaRecapBinding

class MediaRecapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaRecapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fun buildPrompt(mode: String) {
            val url = binding.url.text.toString().trim()
            val language = binding.language.text.toString()

            binding.output.setText(
                "$mode workflow\n" +
                    "1. Use /termux yt-dlp to fetch media you are authorized to process.\n" +
                    "2. Extract audio with ffmpeg.\n" +
                    "3. Transcribe with your configured Python speech model.\n" +
                    "4. Send transcript to AI Provider.\n\n" +
                    "AI prompt:\n" +
                    "Create a concise social recap in $language. " +
                    "Preserve facts, mark uncertainty, and add dialogue only when present.\n" +
                    "Source: $url"
            )
        }

        binding.recap.setOnClickListener { buildPrompt("Recap") }
        binding.dialogue.setOnClickListener { buildPrompt("Recap + Dialogue") }
        binding.dub.setOnClickListener { buildPrompt("Dub") }
    }
}
