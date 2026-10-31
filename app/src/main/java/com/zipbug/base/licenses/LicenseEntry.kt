package com.zipbug.base.licenses

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One third-party library entry, generated at build time from the project's
 * real Gradle dependencies by scripts/generate-licenses.py and bundled as
 * assets/open_source_licenses.json. Never edit the JSON by hand.
 */
data class LicenseEntry(
    val name: String,
    val displayName: String,
    val version: String,
    val author: String,
    val license: String,
    val licenseUrl: String,
    val category: String,
    val scope: List<String>,
    val notes: String? = null
) {
    val isReviewRequired: Boolean
        get() = license == REVIEW_REQUIRED

    companion object {
        const val REVIEW_REQUIRED = "License review required"
        const val SCOPE_APP = "app"
        const val SCOPE_GENERATED = "generated_apps"
        const val ASSET_PATH = "open_source_licenses.json"

        fun parse(jsonText: String): List<LicenseEntry> {
            val array = JSONArray(jsonText)
            val entries = ArrayList<LicenseEntry>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                entries += obj.toLicenseEntry()
            }
            return entries
        }

        fun loadFromAssets(context: Context): List<LicenseEntry> {
            val text = context.assets.open(ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
            return parse(text)
        }

        private fun JSONObject.toLicenseEntry(): LicenseEntry {
            val scopeArray = getJSONArray("scope")
            val scope = ArrayList<String>(scopeArray.length())
            for (i in 0 until scopeArray.length()) {
                scope += scopeArray.getString(i)
            }
            return LicenseEntry(
                name = getString("name"),
                displayName = getString("displayName"),
                version = getString("version"),
                author = getString("author"),
                license = getString("license"),
                licenseUrl = getString("licenseUrl"),
                category = optString("category", "other"),
                scope = scope,
                notes = if (has("notes")) getString("notes") else null
            )
        }
    }
}

/** Filter chips on the Open Source Licenses screen. */
enum class LicenseFilter {
    ALL, APP, GENERATED, APACHE, MIT, BSD, GOOGLE_SDK, OTHER;

    fun matches(entry: LicenseEntry): Boolean = when (this) {
        ALL -> true
        APP -> LicenseEntry.SCOPE_APP in entry.scope
        GENERATED -> LicenseEntry.SCOPE_GENERATED in entry.scope
        APACHE -> entry.category == "apache"
        MIT -> entry.category == "mit"
        BSD -> entry.category == "bsd"
        GOOGLE_SDK -> entry.category == "google_sdk"
        OTHER -> entry.category == "other" || entry.isReviewRequired
    }
}

object LicenseCatalog {

    fun filter(
        entries: List<LicenseEntry>,
        filter: LicenseFilter,
        query: String
    ): List<LicenseEntry> {
        val q = query.trim().lowercase()
        return entries.filter { entry ->
            filter.matches(entry) && (q.isEmpty() || entry.matchesQuery(q))
        }
    }

    fun unknownCount(entries: List<LicenseEntry>): Int =
        entries.count { it.isReviewRequired }

    private fun LicenseEntry.matchesQuery(q: String): Boolean =
        displayName.lowercase().contains(q) ||
            name.lowercase().contains(q) ||
            author.lowercase().contains(q) ||
            license.lowercase().contains(q)
}
