package com.zipbug.base.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.zipbug.base.R
import com.zipbug.base.ui.fragments.TerminalFragment

class TerminalActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_COMMAND = "extra_command"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)

        val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }

        val cmd = intent.getStringExtra(EXTRA_COMMAND).orEmpty()
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, TerminalFragment.newInstance(cmd))
                .commit()
        }
    }
}
