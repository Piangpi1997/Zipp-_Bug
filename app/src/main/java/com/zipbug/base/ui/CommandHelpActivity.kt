package com.zipbug.base.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zipbug.base.R
import com.zipbug.base.command.CommandGroups
import com.zipbug.base.command.CommandRegistry
import com.zipbug.base.command.CommandSpec
import com.zipbug.base.databinding.ActivityCommandHelpBinding

class CommandHelpActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_GROUP = "command_group"

        fun createIntent(
            context: Context,
            group: String? = null
        ): Intent =
            Intent(
                context,
                CommandHelpActivity::class.java
            ).apply {
                group?.let {
                    putExtra(
                        EXTRA_GROUP,
                        it
                    )
                }
            }
    }

    private lateinit var binding: ActivityCommandHelpBinding
    private var selectedGroup: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommandHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedGroup =
            intent.getStringExtra(EXTRA_GROUP)
                ?.let(CommandGroups::normalize)

        selectedGroup?.let { group ->
            title = group + " Commands"
            binding.search.hint =
                "Search " + group + " commands"
        }

        binding.search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    render(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )

        render("")
    }

    private fun render(query: String) {
        val commands =
            if (selectedGroup == null) {
                CommandRegistry.search(query)
            } else {
                CommandRegistry.forGroup(
                    selectedGroup!!
                ).filter {
                    query.isBlank() ||
                        searchableText(it).contains(
                            query.trim(),
                            ignoreCase = true
                        )
                }
            }

        binding.results.removeAllViews()

        binding.count.text = buildString {
            selectedGroup?.let {
                append(it)
                append(" • ")
            }

            if (query.isBlank()) {
                append(commands.size)
                append(" commands")
            } else {
                append(commands.size)
                append(" matches")
            }
        }

        if (commands.isEmpty()) {
            binding.results.addView(
                TextView(this).apply {
                    text = "No command matches this search."
                    setTextColor(
                        ContextCompat.getColor(
                            this@CommandHelpActivity,
                            R.color.z_muted
                        )
                    )
                    textSize = 14f
                    setPadding(0, dp(12), 0, dp(12))
                }
            )
            return
        }

        var previousGroup: String? = null

        commands.forEach { spec ->
            if (
                selectedGroup == null &&
                spec.group != previousGroup
            ) {
                binding.results.addView(
                    TextView(this).apply {
                        text = "/" + spec.group
                        setTextColor(
                            ContextCompat.getColor(
                                this@CommandHelpActivity,
                                R.color.z_orange
                            )
                        )
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, dp(14), 0, dp(6))
                    }
                )
                previousGroup = spec.group
            }

            binding.results.addView(
                commandCard(spec)
            )
        }
    }

    private fun searchableText(
        spec: CommandSpec
    ): String =
        buildString {
            append(spec.command)
            append(' ')
            append(spec.aliases.joinToString(" "))
            append(' ')
            append(spec.group)
            append(' ')
            append(spec.description)
            append(' ')
            append(spec.example)
        }

    private fun commandCard(spec: CommandSpec): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(
                ContextCompat.getColor(
                    this@CommandHelpActivity,
                    R.color.z_surface
                )
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }

            addView(
                TextView(this@CommandHelpActivity).apply {
                    text = buildString {
                        append(spec.command)
                        if (spec.aliases.isNotEmpty()) {
                            append("  • alias: ")
                            append(spec.aliases.joinToString())
                        }
                    }
                    setTextColor(
                        ContextCompat.getColor(
                            this@CommandHelpActivity,
                            if (spec.available) {
                                R.color.z_text
                            } else {
                                R.color.z_muted
                            }
                        )
                    )
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                }
            )

            addView(
                TextView(this@CommandHelpActivity).apply {
                    text = spec.description
                    setTextColor(
                        ContextCompat.getColor(
                            this@CommandHelpActivity,
                            R.color.z_muted
                        )
                    )
                    textSize = 13f
                    setPadding(0, dp(4), 0, dp(4))
                }
            )

            addView(
                TextView(this@CommandHelpActivity).apply {
                    text = "Example: " + spec.example
                    setTextColor(
                        ContextCompat.getColor(
                            this@CommandHelpActivity,
                            R.color.z_green
                        )
                    )
                    typeface = Typeface.MONOSPACE
                    textSize = 12f
                    setTextIsSelectable(true)
                }
            )

            if (!spec.available) {
                addView(
                    TextView(this@CommandHelpActivity).apply {
                        text =
                            "Unavailable: " + spec.unavailableReason
                        setTextColor(
                            ContextCompat.getColor(
                                this@CommandHelpActivity,
                                R.color.z_muted
                            )
                        )
                        textSize = 12f
                        setPadding(0, dp(4), 0, 0)
                    }
                )
            }
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density)
            .toInt()
}
