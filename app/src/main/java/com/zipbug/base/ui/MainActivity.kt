package com.zipbug.base.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.zipbug.base.R
import com.zipbug.base.databinding.ActivityMainBinding
import com.zipbug.base.ui.fragments.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            show(HomeFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            show(
                when (item.itemId) {
                    R.id.nav_studio -> StudioFragment()
                    R.id.nav_ai -> AiFragment()
                    R.id.nav_terminal -> TerminalFragment()
                    R.id.nav_projects -> ProjectsFragment()
                    else -> HomeFragment()
                }
            )
            true
        }

        binding.settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentHost, fragment)
            .commit()
    }
}
