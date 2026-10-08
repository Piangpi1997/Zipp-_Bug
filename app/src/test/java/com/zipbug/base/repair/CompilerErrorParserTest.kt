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
}
