package com.zipbug.base.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.zipbug.base.databinding.ActivityOpenSourceLicensesBinding
import com.zipbug.base.databinding.BottomSheetLicenseDetailBinding
import com.zipbug.base.licenses.LicenseCatalog
import com.zipbug.base.licenses.LicenseEntry
import com.zipbug.base.licenses.LicenseFilter

/**
 * Open Source Licenses screen (Settings -> About).
 *
 * Data comes from assets/open_source_licenses.json, generated at build time
 * from the project's real Gradle dependencies by
 * scripts/generate-licenses.py. MODE_NOTICES shows the bundled
 * THIRD_PARTY_NOTICES text instead of the searchable list.
 */
class OpenSourceLicensesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOpenSourceLicensesBinding
    private var entries: List<LicenseEntry> = emptyList()
    private var currentFilter: LicenseFilter = LicenseFilter.ALL
    private var currentQuery: String = ""
    private val adapter = LicenseAdapter { entry -> showDetail(entry) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOpenSourceLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLicensesBack.setOnClickListener { finish() }

        if (intent.getStringExtra(EXTRA_MODE) == MODE_NOTICES) {
            showNotices()
            return
        }

        entries = try {
            LicenseEntry.loadFromAssets(this)
        } catch (e: Exception) {
            emptyList()
        }

        binding.tvLicenseCount.text = getString(
            R.string.licenses_count,
            entries.size,
            LicenseCatalog.unknownCount(entries)
        )

        binding.rvLicenses.layoutManager = LinearLayoutManager(this)
        binding.rvLicenses.adapter = adapter

        binding.etLicenseSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                currentQuery = s?.toString().orEmpty()
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupLicenses.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chipFilterApp -> LicenseFilter.APP
                R.id.chipFilterGenerated -> LicenseFilter.GENERATED
                R.id.chipFilterApache -> LicenseFilter.APACHE
                R.id.chipFilterMit -> LicenseFilter.MIT
                R.id.chipFilterBsd -> LicenseFilter.BSD
                R.id.chipFilterGoogle -> LicenseFilter.GOOGLE_SDK
                R.id.chipFilterOther -> LicenseFilter.OTHER
                else -> LicenseFilter.ALL
            }
            applyFilter()
        }

        applyFilter()
    }

    private fun applyFilter() {
        val visible = LicenseCatalog.filter(entries, currentFilter, currentQuery)
        adapter.submit(visible)
        binding.tvLicensesEmpty.visibility =
            if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showNotices() {
        binding.tvLicensesTitle.text = getString(R.string.notices_title)
        binding.etLicenseSearch.visibility = View.GONE
        binding.scrollLicenseFilters.visibility = View.GONE
        binding.rvLicenses.visibility = View.GONE
        binding.tvLicenseCount.visibility = View.GONE
        binding.layoutNotices.visibility = View.VISIBLE
        val text = try {
            assets.open(NOTICES_ASSET).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            getString(R.string.licenses_empty)
        }
        binding.tvNoticesText.text = text
        binding.btnCopyNotices.setOnClickListener {
            copyText("third_party_notices", text)
        }
    }

    private fun showDetail(entry: LicenseEntry) {
        val dialog = BottomSheetDialog(this)
        val sheet = BottomSheetLicenseDetailBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)

        sheet.tvDetailName.text = entry.displayName
        sheet.tvDetailCoordinate.text =
            getString(R.string.licenses_detail_coordinate, entry.name)
        sheet.tvDetailVersion.text =
            getString(R.string.licenses_detail_version, entry.version)
        sheet.tvDetailAuthor.text =
            getString(R.string.licenses_detail_author, entry.author)
        sheet.tvDetailLicense.text = getString(
            R.string.licenses_detail_license,
            if (entry.isReviewRequired) {
                getString(R.string.licenses_review_required)
            } else {
                entry.license
            }
        )
        sheet.tvDetailScope.text =
            getString(R.string.licenses_detail_scope, scopeLabel(entry))
        if (!entry.notes.isNullOrBlank()) {
            sheet.tvDetailNotes.visibility = View.VISIBLE
            sheet.tvDetailNotes.text = entry.notes
        }

        sheet.btnDetailCopyLicense.setOnClickListener {
            copyText(
                "license",
                "${entry.displayName} (${entry.name}:${entry.version})\n" +
                    "License: ${entry.license}\n${entry.licenseUrl}"
            )
            dialog.dismiss()
        }
        sheet.btnDetailOpenUrl.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.licenseUrl)))
            } catch (e: Exception) {
                Toast.makeText(
                    this, R.string.licenses_open_url_failed, Toast.LENGTH_SHORT
                ).show()
            }
        }
        sheet.btnDetailCopyName.setOnClickListener {
            copyText("library", entry.name)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun scopeLabel(entry: LicenseEntry): String {
        val labels = entry.scope.map { scope ->
            when (scope) {
                LicenseEntry.SCOPE_GENERATED ->
                    getString(R.string.licenses_scope_generated)
                else -> getString(R.string.licenses_scope_app)
            }
        }
        return labels.joinToString(", ")
    }

    private fun tagsLabel(entry: LicenseEntry): String {
        val parts = ArrayList<String>()
        parts += if (entry.isReviewRequired) {
            getString(R.string.licenses_review_required)
        } else {
            entry.license
        }
        entry.scope.forEach { scope ->
            parts += if (scope == LicenseEntry.SCOPE_GENERATED) {
                getString(R.string.licenses_scope_generated)
            } else {
                getString(R.string.licenses_scope_app)
            }
        }
        return parts.joinToString("  •  ")
    }

    private fun copyText(label: String, text: String) {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, R.string.licenses_copied, Toast.LENGTH_SHORT).show()
    }

    private inner class LicenseAdapter(
        private val onClick: (LicenseEntry) -> Unit
    ) : RecyclerView.Adapter<LicenseAdapter.Holder>() {

        private var items: List<LicenseEntry> = emptyList()

        fun submit(list: List<LicenseEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_license, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = items[position]
            holder.name.text = entry.displayName
            holder.version.text = entry.version
            holder.author.text = entry.author
            holder.tags.text = tagsLabel(entry)
            holder.itemView.setOnClickListener { onClick(entry) }
        }

        override fun getItemCount(): Int = items.size

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvLibName)
            val version: TextView = view.findViewById(R.id.tvLibVersion)
            val author: TextView = view.findViewById(R.id.tvLibAuthor)
            val tags: TextView = view.findViewById(R.id.tvLibTags)
        }
    }

    companion object {
        const val EXTRA_MODE = "com.zipbug.base.extra.LICENSES_MODE"
        const val MODE_LIST = "list"
        const val MODE_NOTICES = "notices"
        private const val NOTICES_ASSET = "third_party_notices.txt"
    }
}
