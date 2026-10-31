package com.zipbug.base.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zipbug.base.R
import com.zipbug.base.ai.AiMode
import com.zipbug.base.ui.fragments.AiFragment

class AiModeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)
        if (savedInstanceState == null) {
            val mode = runCatching { AiMode.valueOf(intent.getStringExtra("mode").orEmpty()) }.getOrDefault(AiMode.CHAT)
            supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer,
                AiFragment.newInstance(intent.getStringExtra("prompt").orEmpty(), mode)).commit()
        }
    }
    companion object {
        fun createIntent(context: Context, prompt: String, mode: AiMode): Intent =
            Intent(context, AiModeActivity::class.java).putExtra("prompt", prompt).putExtra("mode", mode.name)
    }
}
