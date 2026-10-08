package com.zipbug.base.ui

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
import com.zipbug.base.command.CommandRegistry
import com.zipbug.base.command.CommandSpec
import com.zipbug.base.databinding.ActivityCommandHelpBinding

class CommandHelpActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCommandHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommandHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        val commands = CommandRegistry.search(query)
        binding.results.removeAllViews()

        binding.count.text =
            if (query.isBlank()) {
                "${commands.size} commands"
            } else {
                "${commands.size} matches"
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
            if (spec.group != previousGroup) {
                binding.results.addView(
                    TextView(this).apply {
                        text = spec.group.uppercase()
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

            binding.results.addView(commandCard(spec))
        }
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
                            if (spec.available) R.color.z_text else R.color.z_muted
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
                    text = "Example: ${spec.example}"
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
                        text = "Unavailable: ${spec.unavailableReason}"
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
        (value * resources.displayMetrics.density).toInt()
}
