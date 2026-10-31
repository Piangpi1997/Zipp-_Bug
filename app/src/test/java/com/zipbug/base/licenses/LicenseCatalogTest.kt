package com.zipbug.base.licenses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseCatalogTest {

    private fun entry(
        name: String,
        license: String = "Apache License 2.0",
        category: String = "apache",
        scope: List<String> = listOf("app"),
        author: String = "The Android Open Source Project",
        displayName: String = name.substringAfterLast(':')
    ) = LicenseEntry(
        name = name,
        displayName = displayName,
        version = "1.0.0",
        author = author,
        license = license,
        licenseUrl = "https://example.org/license",
        category = category,
        scope = scope
    )

    private val entries = listOf(
        entry("androidx.core:core-ktx", scope = listOf("app", "generated_apps")),
        entry("com.squareup.okhttp3:okhttp", author = "Square, Inc."),
        entry(
            "com.github.bumptech.glide:glide",
            license = "BSD 3-Clause License",
            category = "bsd",
            author = "Bumptech"
        ),
        entry(
            "com.example:mystery",
            license = LicenseEntry.REVIEW_REQUIRED,
            category = "other"
        )
    )

    @Test
    fun filterAllReturnsEverything() {
        assertEquals(4, LicenseCatalog.filter(entries, LicenseFilter.ALL, "").size)
    }

    @Test
    fun filterGeneratedOnlyReturnsGeneratedScope() {
        val result = LicenseCatalog.filter(entries, LicenseFilter.GENERATED, "")
        assertEquals(listOf("androidx.core:core-ktx"), result.map { it.name })
    }

    @Test
    fun filterApacheAndBsdUseCategories() {
        assertEquals(
            2, LicenseCatalog.filter(entries, LicenseFilter.APACHE, "").size
        )
        assertEquals(
            listOf("com.github.bumptech.glide:glide"),
            LicenseCatalog.filter(entries, LicenseFilter.BSD, "").map { it.name }
        )
        assertTrue(LicenseCatalog.filter(entries, LicenseFilter.MIT, "").isEmpty())
        assertTrue(
            LicenseCatalog.filter(entries, LicenseFilter.GOOGLE_SDK, "").isEmpty()
        )
    }

    @Test
    fun filterOtherIncludesReviewRequired() {
        val result = LicenseCatalog.filter(entries, LicenseFilter.OTHER, "")
        assertEquals(listOf("com.example:mystery"), result.map { it.name })
    }

    @Test
    fun searchMatchesNameAuthorAndLicenseCaseInsensitively() {
        assertEquals(
            1, LicenseCatalog.filter(entries, LicenseFilter.ALL, "OKHTTP").size
        )
        assertEquals(
            1, LicenseCatalog.filter(entries, LicenseFilter.ALL, "square").size
        )
        assertEquals(
            1, LicenseCatalog.filter(entries, LicenseFilter.ALL, "bsd").size
        )
        assertTrue(
            LicenseCatalog.filter(entries, LicenseFilter.ALL, "nonexistent").isEmpty()
        )
    }

    @Test
    fun unknownCountCountsReviewRequiredOnly() {
        assertEquals(1, LicenseCatalog.unknownCount(entries))
        assertEquals(0, LicenseCatalog.unknownCount(entries.dropLast(1)))
    }
}
