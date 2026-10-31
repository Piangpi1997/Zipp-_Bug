package com.zipbug.base.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScaffolderTest {
    @Test
    fun createsBuildableProjectShape() {
        val files = AppScaffolder.files(
            AppSpec(
                name = "Demo",
                packageName = "com.example.demo",
                goal = "A real demo project"
            )
        )

        assertTrue(files.containsKey("settings.gradle.kts"))
        assertTrue(files.containsKey("app/build.gradle.kts"))
        assertTrue(
            files.containsKey(
                "app/src/main/java/com/example/demo/MainActivity.kt"
            )
        )
        assertTrue(
            files.getValue("app/build.gradle.kts")
                .contains("compileSdk = 34")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafePackageName() {
        AppScaffolder.files(
            AppSpec(
                name = "Bad",
                packageName = "../bad",
                goal = ""
            )
        )
    }

    @Test
    fun escapesProjectNameForSettings() {
        val files = AppScaffolder.files(
            AppSpec(
                name = "A \"quoted\" app",
                packageName = "com.example.quoted",
                goal = ""
            )
        )

        assertTrue(
            files.getValue("settings.gradle.kts")
                .contains("A \\\"quoted\\\" app")
        )
        // 12 project files + third_party_notices.txt + license-manifest.json
        // (Open Source Licenses v1 adds the two license files to every
        // generated project).
        assertEquals(14, files.size)
    }

    @Test
    fun redactsRecognizableSecretsFromGeneratedSource() {
        val secret = "sk-or-v1-abcdefghijklmnopqrstuvwxyz123456"
        val files = AppScaffolder.files(
            AppSpec(
                name = "Safe",
                packageName = "com.example.safe",
                goal = "Use key " + secret + " only locally"
            )
        )

        val combined = files.values.joinToString("\n")
        assertTrue(!combined.contains(secret))
        assertTrue(combined.contains("[REDACTED]"))
        assertTrue(
            files.getValue("app/src/main/AndroidManifest.xml")
                .contains("android:allowBackup=\"false\"")
        )
        assertTrue(
            files.getValue("README.md")
                .contains("/apkbuilder --debug")
        )
    }


    @Test
    fun scaffoldAppliesReviewedSdkPermissionsAndDependencies() {
        val files = AppScaffolder.files(
            AppSpec(
                name = "Configured",
                packageName = "com.example.configured",
                goal = "",
                minSdk = 26,
                targetSdk = 34,
                permissions = listOf(
                    "android.permission.INTERNET"
                ),
                dependencies = listOf(
                    "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7"
                )
            )
        )

        val gradle = files.getValue("app/build.gradle.kts")
        val manifest = files.getValue(
            "app/src/main/AndroidManifest.xml"
        )

        assertTrue(gradle.contains("minSdk = 26"))
        assertTrue(gradle.contains("targetSdk = 34"))
        assertTrue(
            gradle.contains(
                "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7"
            )
        )
        assertTrue(
            manifest.contains(
                "android.permission.INTERNET"
            )
        )
    }


    @Test
    fun roomCompilerUsesKaptInsteadOfRuntimeImplementation() {
        val files = AppScaffolder.files(
            AppSpec(
                name = "RoomNotes",
                packageName = "com.example.roomnotes",
                goal = "",
                dependencies = listOf(
                    "androidx.room:room-runtime:2.6.1",
                    "androidx.room:room-ktx:2.6.1",
                    "androidx.room:room-compiler:2.6.1"
                )
            )
        )

        val rootGradle = files.getValue("build.gradle.kts")
        val appGradle = files.getValue("app/build.gradle.kts")

        assertTrue(
            rootGradle.contains(
                "id(\"org.jetbrains.kotlin.kapt\") version \"1.9.24\" apply false"
            )
        )
        assertTrue(
            appGradle.contains(
                "id(\"org.jetbrains.kotlin.kapt\")"
            )
        )
        assertTrue(
            appGradle.contains(
                "kapt(\"androidx.room:room-compiler:2.6.1\")"
            )
        )
        assertTrue(
            !appGradle.contains(
                "implementation(\"androidx.room:room-compiler:2.6.1\")"
            )
        )
    }

}
