package com.zipbug.base.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCreatorParserTest {

    @Test
    fun parseValidJsonSpec() {
        val json = """
        {
          "project": {
            "name": "NoteApp",
            "packageName": "com.zipbug.noteapp",
            "target": "ANDROID_KOTLIN",
            "minSdk": 24,
            "targetSdk": 34
          },
          "permissions": ["android.permission.INTERNET"],
          "dependencies": ["androidx.core:core-ktx:1.13.1"],
          "files": [
            {
              "path": "app/src/main/java/com/zipbug/noteapp/MainActivity.kt",
              "content": "package com.zipbug.noteapp\nclass MainActivity"
            }
          ],
          "build": {
            "command": "gradle assembleDebug"
          }
        }
        """.trimIndent()

        val result = AppCreatorParser.parse(json)
        assertTrue(result.isSuccess)
        val spec = result.getOrThrow()
        assertEquals("NoteApp", spec.name)
        assertEquals("com.zipbug.noteapp", spec.packageName)
        assertEquals(1, spec.permissions.size)
        assertEquals(1, spec.files.size)
        assertEquals("gradle --no-daemon assembleDebug", spec.buildCommand)
    }

    @Test
    fun parseMarkdownWrappedJsonSpec() {
        val markdown = """
        ```json
        {
          "project": {
            "name": "MarkdownApp",
            "packageName": "com.zipbug.test",
            "target": "ANDROID_KOTLIN"
          },
          "files": []
        }
        ```
        """.trimIndent()

        val result = AppCreatorParser.parse(markdown)
        assertTrue(result.isSuccess)
        assertEquals("MarkdownApp", result.getOrThrow().name)
    }

    @Test
    fun parseIgnoresAiRequestedArbitraryBuildCommand() {
        val json = """
        {
          "project": {
            "name": "SafeBuild",
            "packageName": "com.zipbug.safebuild",
            "target": "ANDROID_KOTLIN"
          },
          "files": [],
          "build": {
            "command": "bash -c rm -rf /"
          }
        }
        """.trimIndent()

        val spec = AppCreatorParser.parse(json).getOrThrow()
        assertEquals(
            "gradle --no-daemon assembleDebug",
            spec.buildCommand
        )
    }

    @Test
    fun parseRejectsUnsupportedTarget() {
        val json = """
        {
          "project": {
            "name": "WrongTarget",
            "packageName": "com.zipbug.wrong",
            "target": "SHELL_APP"
          },
          "files": []
        }
        """.trimIndent()

        assertTrue(AppCreatorParser.parse(json).isFailure)
    }

    @Test
    fun parseRejectsUnsupportedSdkRange() {
        val json = """
        {
          "project": {
            "name": "BadSdk",
            "packageName": "com.zipbug.badsdk",
            "target": "ANDROID_KOTLIN",
            "minSdk": 23,
            "targetSdk": 35
          },
          "files": []
        }
        """.trimIndent()

        assertTrue(AppCreatorParser.parse(json).isFailure)
    }

}
