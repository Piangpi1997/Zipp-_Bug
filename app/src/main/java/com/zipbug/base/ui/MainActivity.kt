package com.zipbug.base.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.zipbug.base.R
import com.zipbug.base.databinding.ActivityMainBinding
import com.zipbug.base.ai.AiMode
import com.zipbug.base.command.ZippyCommandAction
import com.zipbug.base.command.ZippyCommandRouter
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

        binding.askZippySend.setOnClickListener {
            submitZippyCommand()
        }

        binding.askZippyInput.setOnEditorActionListener { _, actionId, _ ->
            if (
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            ) {
                submitZippyCommand()
                true
            } else {
                false
            }
        }
    }

    private fun submitZippyCommand() {
        val raw = binding.askZippyInput.text?.toString().orEmpty()
        val action = ZippyCommandRouter.route(raw) ?: return
        binding.askZippyInput.text?.clear()

        when (action) {
            is ZippyCommandAction.Ask ->
                navigateToAiWithPrompt(action.prompt, AiMode.PROJECT_EDIT)

            is ZippyCommandAction.Create ->
                navigateToAiWithPrompt(action.prompt, AiMode.APP_CREATOR)

            is ZippyCommandAction.Fix ->
                navigateToAiWithPrompt(action.prompt, AiMode.FIX_ERROR)

            ZippyCommandAction.BuildDebug ->
                navigateToTerminal("/apkbuilder --debug")

            ZippyCommandAction.OpenStudio ->
                navigateToStudio()

            ZippyCommandAction.OpenProjects ->
                navigateToProjects()

            ZippyCommandAction.OpenAppCreator -> {
                startActivity(Intent(this, AppCreatorActivity::class.java))
            }

            ZippyCommandAction.UndoLastChange -> {
                val prefs = getSharedPreferences("zipbug.settings", 0)
                val target = prefs.getString("lastReviewedPatchTarget", "").orEmpty()
                if (target.isBlank()) {
                    navigateToAiWithPrompt(
                        "There is no recorded reviewed change to restore yet.",
                        AiMode.CHAT
                    )
                } else {
                    navigateToTerminal("/restorepatch " + target)
                }
            }
        }
    }

    fun navigateToAiWithPrompt(
        prompt: String,
        mode: AiMode = AiMode.FIX_ERROR
    ) {
        val fragment = AiFragment.newInstance(prompt, mode)
        binding.bottomNav.selectedItemId = R.id.nav_ai
        show(fragment)
    }

    fun navigateToStudio() {
        binding.bottomNav.selectedItemId = R.id.nav_studio
        show(StudioFragment())
    }

    fun navigateToProjects() {
        binding.bottomNav.selectedItemId = R.id.nav_projects
        show(ProjectsFragment())
    }

    fun navigateToTerminal(command: String = "") {
        binding.bottomNav.selectedItemId = R.id.nav_terminal
        show(TerminalFragment.newInstance(command))
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentHost, fragment)
            .commit()
    }
}
