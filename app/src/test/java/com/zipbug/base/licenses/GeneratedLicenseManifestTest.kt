package com.zipbug.base.licenses

import com.zipbug.base.creator.AppScaffolder
import com.zipbug.base.creator.AppSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedLicenseManifestTest {

    @Test
    fun coreDependenciesAllResolveWithKnownLicenses() {
        val resolved =
            GeneratedLicenseManifest.resolve(GeneratedLicenseManifest.CORE_DEPENDENCIES)
        assertEquals(4, resolved.size)
        assertEquals(0, GeneratedLicenseManifest.unknownCount(resolved))
        assertTrue(resolved.all { it.licenseName == "Apache License 2.0" })
    }

    @Test
    fun unknownDependencyIsFlaggedForReviewNotRelabelled() {
        val resolved =
            GeneratedLicenseManifest.resolve(listOf("com.example:mystery-lib:1.2.3"))
        assertEquals(1, resolved.size)
        assertTrue(resolved[0].isReviewRequired)
        assertEquals(
            LicenseEntry.REVIEW_REQUIRED, resolved[0].licenseName
        )
    }

    @Test
    fun duplicatesAreResolvedOnce() {
        val resolved = GeneratedLicenseManifest.resolve(
            listOf(
                "androidx.core:core-ktx:1.13.1",
                "androidx.core:core-ktx:1.13.1"
            )
        )
        assertEquals(1, resolved.size)
    }

    @Test
    fun manifestJsonCarriesCountsAndDependencies() {
        val json = GeneratedLicenseManifest.buildManifestJson(
            appName = "Demo",
            packageName = "com.example.demo",
            features = listOf("core"),
            coordinates = GeneratedLicenseManifest.CORE_DEPENDENCIES +
                listOf("com.example:mystery-lib:1.2.3")
        )
        assertTrue(json.contains("\"appName\": \"Demo\""))
        assertTrue(json.contains("\"packageName\": \"com.example.demo\""))
        assertTrue(json.contains("\"libraries\": 5"))
        assertTrue(json.contains("\"unknown\": 1"))
        assertTrue(json.contains("\"name\": \"androidx.core:core-ktx\""))
        assertTrue(json.contains(LicenseEntry.REVIEW_REQUIRED))
    }

    @Test
    fun noticesIncludeKnownLicenseAndReviewFlag() {
        val notices = GeneratedLicenseManifest.buildNotices(
            "Demo",
            GeneratedLicenseManifest.CORE_DEPENDENCIES +
                listOf("com.example:mystery-lib:1.2.3")
        )
        assertTrue(notices.contains("Apache License 2.0"))
        assertTrue(notices.contains("Unknown: 1"))
        assertTrue(notices.contains(LicenseEntry.REVIEW_REQUIRED))
    }

    @Test
    fun scaffolderPackagesNoticesAndManifestCoveringItsGradleFile() {
        val spec = AppSpec(
            name = "Demo",
            packageName = "com.example.demo",
            goal = "demo",
            dependencies = listOf(
                "com.squareup.okhttp3:okhttp:4.12.0",
                "com.example:mystery-lib:1.2.3"
            )
        )
        val files = AppScaffolder.files(spec)
        assertTrue(files.containsKey("app/src/main/assets/third_party_notices.txt"))
        assertTrue(files.containsKey("license-manifest.json"))

        val manifest = files.getValue("license-manifest.json")
        val gradle = files.getValue("app/build.gradle.kts")
        val coordinateRegex =
            Regex("""(?:implementation|kapt)\("([^"]+)"\)""")
        val packaged = coordinateRegex.findAll(gradle)
            .map { it.groupValues[1] }
            .toList()
        assertFalse(packaged.isEmpty())
        for (coordinate in packaged) {
            val ga = coordinate.substringBeforeLast(':')
            assertTrue(
                "manifest must cover $ga",
                manifest.contains("\"name\": \"$ga\"")
            )
        }
        // The unknown extra dependency must be flagged, never hidden.
        assertTrue(manifest.contains("\"unknown\": 1"))
        assertTrue(
            files.getValue("app/src/main/assets/third_party_notices.txt")
                .contains(LicenseEntry.REVIEW_REQUIRED)
        )
    }
}
