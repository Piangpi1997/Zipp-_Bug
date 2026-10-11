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
        assertEquals(12, files.size)
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

}
