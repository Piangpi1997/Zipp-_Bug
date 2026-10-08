package com.zipbug.base.repair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompilerErrorParserTest {

    @Test
    fun parseKotlinCompilerErrors() {
        val log = """
        > Task :app:compileDebugKotlin FAILED
        e: /data/data/com.termux/files/home/app/MainActivity.kt: (24, 18): Unresolved reference: unknownFunction
        e: /data/data/com.termux/files/home/app/MainActivity.kt: (30, 5): Type mismatch: inferred type is String but Int was expected
        """.trimIndent()

        val errors = CompilerErrorParser.parseErrors(log)
        assertEquals(2, errors.size)
        assertEquals("/data/data/com.termux/files/home/app/MainActivity.kt", errors[0].filePath)
        assertEquals(24, errors[0].line)
        assertEquals(18, errors[0].column)
        assertTrue(errors[0].message.contains("Unresolved reference"))
    }

    @Test
    fun parseJavaCompilerErrors() {
        val log = """
        > Task :app:compileDebugJavaWithJavac FAILED
        /data/data/com.termux/files/home/app/Util.java:15: error: cannot find symbol
        """.trimIndent()

        val errors = CompilerErrorParser.parseErrors(log)
        assertEquals(1, errors.size)
        assertEquals(15, errors[0].line)
        assertTrue(errors[0].message.contains("cannot find symbol"))
    }

    @Test
    fun buildAiRepairPromptContainsErrors() {
        val errors = listOf(
            CompilerError(
                filePath = "MainActivity.kt",
                line = 10,
                column = 5,
                message = "Unresolved reference",
                raw = "e: MainActivity.kt: (10, 5): Unresolved reference"
            )
        )
        val prompt = CompilerErrorParser.buildAiRepairPrompt("MyTestApp", errors, "val x = y")
        assertTrue(prompt.contains("MyTestApp"))
        assertTrue(prompt.contains("MainActivity.kt:10"))
        assertTrue(prompt.contains("Unresolved reference"))
    }

    @Test
    fun parseAndApplyPatchProposal() {
        val jsonPatch = """
        ```json
        {
          "targetFile": "src/Main.kt",
          "originalSnippet": "val a = 1",
          "replacementSnippet": "val a = 2",
          "explanation": "Update constant"
        }
        ```
        """.trimIndent()

        val patchResult = CompilerErrorParser.parsePatchProposal(jsonPatch)
        assertTrue(patchResult.isSuccess)
        val patch = patchResult.getOrThrow()
        assertEquals("src/Main.kt", patch.targetFile)
        assertEquals("val a = 1", patch.originalSnippet)
        assertEquals("val a = 2", patch.replacementSnippet)

        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "zipbug_test_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val srcDir = java.io.File(tempDir, "src").apply { mkdirs() }
            val mainFile = java.io.File(srcDir, "Main.kt")
            mainFile.writeText("fun test() {\n    val a = 1\n}\n")

            val applyResult = CompilerErrorParser.applyPatch(tempDir, patch)
            assertTrue(applyResult.isSuccess)
            val updated = mainFile.readText()
            assertTrue(updated.contains("val a = 2"))

            // Verify backup file was created
            val backupFiles = srcDir.listFiles { _, name -> name.startsWith("Main.kt.bak_") }
            assertTrue(backupFiles != null && backupFiles.isNotEmpty())
            assertTrue(backupFiles!![0].readText().contains("val a = 1"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
