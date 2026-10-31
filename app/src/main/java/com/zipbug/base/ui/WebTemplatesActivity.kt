package com.zipbug.base.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.zipbug.base.R
import com.zipbug.base.creator.WebHardware
import com.zipbug.base.creator.WebTemplate
import com.zipbug.base.creator.WebTemplateCategory
import com.zipbug.base.creator.WebTemplates
import com.zipbug.base.databinding.ActivityWebTemplatesBinding
import com.zipbug.base.databinding.BottomSheetWebTemplateBinding

/**
 * Built-in web template catalog for the HTML App creator: native
 * capability demos (vibration, torch, notifications, storage, ...),
 * starter pages and CDN library starters. Each template can be copied
 * as an API snippet or sent straight into the HTML App Wizard.
 */
class WebTemplatesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebTemplatesBinding

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Entry(val template: WebTemplate) : Row()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebTemplatesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnTemplatesBack.setOnClickListener { finish() }

        val rows = mutableListOf<Row>()
        for (category in WebTemplateCategory.values()) {
            val templates = WebTemplates.byCategory(category)
            if (templates.isEmpty()) continue
            rows += Row.Header(categoryTitle(category))
            templates.forEach { rows += Row.Entry(it) }
        }
        binding.templatesList.layoutManager = LinearLayoutManager(this)
        binding.templatesList.adapter = TemplateAdapter(rows) { template ->
            showDetail(template)
        }
    }

    private fun categoryTitle(category: WebTemplateCategory): String =
        getString(
            when (category) {
                WebTemplateCategory.INTERACTION -> R.string.templates_cat_interaction
                WebTemplateCategory.STARTERS -> R.string.templates_cat_starters
                WebTemplateCategory.DEVICE -> R.string.templates_cat_device
                WebTemplateCategory.SYSTEM -> R.string.templates_cat_system
                WebTemplateCategory.LIBRARIES -> R.string.templates_cat_libraries
            }
        )

    private fun hardwareLabel(template: WebTemplate): String? {
        if (template.hardware.isEmpty()) return null
        val names = template.hardware.map {
            getString(
                when (it) {
                    WebHardware.CAMERA -> R.string.wizard_perm_camera
                    WebHardware.MICROPHONE -> R.string.wizard_perm_microphone
                    WebHardware.LOCATION -> R.string.wizard_perm_location
                    WebHardware.NOTIFICATIONS -> R.string.wizard_perm_notifications
                }
            )
        }
        return getString(R.string.templates_hardware_label, names.joinToString(", "))
    }

    private fun showDetail(template: WebTemplate) {
        val dialog = BottomSheetDialog(this)
        val sheet = BottomSheetWebTemplateBinding.inflate(layoutInflater)
        sheet.templateDetailTitle.text = template.title
        sheet.templateDetailCategory.text = categoryTitle(template.category)
        sheet.templateDetailDesc.text = template.description
        sheet.templateDetailSnippet.text = template.snippet
        val hardware = hardwareLabel(template)
        if (hardware != null) {
            sheet.templateDetailHardware.visibility = View.VISIBLE
            sheet.templateDetailHardware.text = hardware
        }
        sheet.templateCopySnippet.setOnClickListener {
            val clipboard =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("api", template.snippet)
            )
            Toast.makeText(
                this,
                R.string.templates_snippet_copied,
                Toast.LENGTH_SHORT
            ).show()
        }
        sheet.templateUseInWizard.setOnClickListener {
            dialog.dismiss()
            startActivity(
                Intent(this, HtmlAppWizardActivity::class.java)
                    .putExtra(HtmlAppWizardActivity.EXTRA_TEMPLATE_ID, template.id)
            )
        }
        dialog.setContentView(sheet.root)
        dialog.show()
    }

    private inner class TemplateAdapter(
        private val rows: List<Row>,
        private val onClick: (WebTemplate) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int =
            if (rows[position] is Row.Header) 0 else 1

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                HeaderHolder(
                    inflater.inflate(
                        R.layout.item_web_template_header,
                        parent,
                        false
                    )
                )
            } else {
                EntryHolder(
                    inflater.inflate(
                        R.layout.item_web_template,
                        parent,
                        false
                    )
                )
            }
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int
        ) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).bind(row.title)
                is Row.Entry -> (holder as EntryHolder).bind(row.template)
            }
        }

        inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val title: TextView = view as TextView
            fun bind(text: String) {
                title.text = text
            }
        }

        inner class EntryHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val title: TextView =
                view.findViewById(R.id.templateItemTitle)
            private val desc: TextView =
                view.findViewById(R.id.templateItemDesc)

            fun bind(template: WebTemplate) {
                title.text = template.title
                desc.text = template.description
                itemView.setOnClickListener { onClick(template) }
            }
        }
    }
}
