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
        assertEquals("gradle assembleDebug", spec.buildCommand)
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
}
