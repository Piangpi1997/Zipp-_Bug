package com.zipbug.base.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun testRedactsOpenRouterKey() {
        val raw = "Authorization: Bearer sk-or-v1-abcdef1234567890abcdef123456"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse(sanitized.contains("sk-or-v1-abcdef"))
        assertTrue(sanitized.contains("sk-or-[REDACTED]"))
    }

    @Test
    fun testRedactsGeminiApiKey() {
        val raw = "Using AIzaSyAbCdEfGhIjKlMnOpQrStUvWxYz01234 for Gemini call"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse(sanitized.contains("AbCdEfGhIjKlMnOpQrStUvWxYz01234"))
        assertTrue(sanitized.contains("AIza[REDACTED]"))
    }

    @Test
    fun testRedactsGenericBearerToken() {
        val raw = "header Bearer 1234567890abcdef1234567890abcdef"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse(sanitized.contains("1234567890abcdef"))
        assertTrue(sanitized.contains("Bearer [REDACTED]"))
    }

    @Test
    fun testBoundsExcessiveLogLength() {
        val sb = StringBuilder()
        for (i in 0 until 200_000) {
            sb.append("A")
        }
        val huge = sb.toString()
        val bounded = LogSanitizer.boundLength(huge, 10_000)

        assertTrue(bounded.length < 20_000)
        assertTrue(bounded.contains("[TRUNCATED"))
        assertTrue(bounded.startsWith("AAAA"))
        assertTrue(bounded.endsWith("AAAA"))
    }

    @Test
    fun testShortLogUnmodified() {
        val short = "Build successful. Generated APK in 4.2s."
        val sanitized = LogSanitizer.sanitize(short)
        assertEquals(short, sanitized)
    }

    @Test
    fun testTermuxBridgeValidateArgumentsRejectsShellInjection() {
        val safeArgs = listOf("script.sh", "--verbose")
        TermuxBridge.validateArguments("sh", safeArgs) // Should not throw

        val chainedArgs = listOf("script.sh; rm -rf /")
        var thrown = false
        try {
            TermuxBridge.validateArguments("sh", chainedArgs)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("Should reject semicolon chaining in sh arguments", thrown)
    }

    @Test
    fun testTermuxBridgeValidateArgumentsRejectsDestructiveCommands() {
        val dangerousArgs = listOf("rm -rf /storage/emulated/0")
        var thrown = false
        try {
            TermuxBridge.validateArguments("bash", dangerousArgs)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("Should reject rm -rf in bash arguments", thrown)
    }

    @Test
    fun testRedactsOpenAiStyleKeyWithoutBearerPrefix() {
        val raw = "provider key sk-proj-abcdefghijklmnopqrstuvwxyz012345"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse(sanitized.contains("abcdefghijklmnopqrstuvwxyz012345"))
        assertTrue(sanitized.contains("sk-proj-[REDACTED]"))
    }

    @Test
    fun testGenericApiKeyEqualsRedactionDoesNotLeakSecretInPrefix() {
        val raw = "api_key=abcdefghijklmnopqrstuvwxyz012345"
        val sanitized = LogSanitizer.sanitize(raw)
        assertFalse(sanitized.contains("abcdefghijklmnopqrstuvwxyz012345"))
        assertTrue(sanitized.contains("api_key=[REDACTED]"))
    }

    @Test
    fun testTermuxBridgeRejectsShellCommandStringMode() {
        for (flag in listOf("-c", "--command")) {
            var thrown = false
            try {
                TermuxBridge.validateArguments("bash", listOf(flag, "echo hello"))
            } catch (e: IllegalArgumentException) {
                thrown = true
            }
            assertTrue("Should reject shell command-string flag $flag", thrown)
        }
    }

    @Test
    fun testTermuxBridgeRejectsRedirectAndNewlineOperators() {
        for (arg in listOf("script.sh>out.txt", "script.sh\necho injected")) {
            var thrown = false
            try {
                TermuxBridge.validateArguments("sh", listOf(arg))
            } catch (e: IllegalArgumentException) {
                thrown = true
            }
            assertTrue("Should reject shell operator in $arg", thrown)
        }
    }


    @Test
    fun testTermuxBridgeRejectsUserInlinePython() {
        var thrown = false
        try {
            TermuxBridge.validateArguments(
                "python",
                listOf("-c", "print('unsafe')")
            )
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("User /termux Python -c must be blocked", thrown)
    }

    @Test
    fun testTermuxBridgeAllowsFixedInternalInlinePython() {
        TermuxBridge.validateArguments(
            "python",
            listOf("-c", "print('internal')"),
            allowInlineCode = true
        )
    }


    @Test
    fun testRedactsAnthropicAndOpenAiProjectKeys() {
        val raw =
            "anthropic=sk-ant-abcdefghijklmnopqrstuvwxyz012345 " +
                "openai=sk-proj-abcdefghijklmnopqrstuvwxyz012345"
        val sanitized = LogSanitizer.sanitize(raw)

        assertFalse(sanitized.contains("abcdefghijklmnopqrstuvwxyz012345"))
        assertTrue(sanitized.contains("sk-ant-[REDACTED]"))
        assertTrue(sanitized.contains("sk-proj-[REDACTED]"))
    }

    @Test
    fun testRedactsGeminiKeyInsideUrl() {
        val raw =
            "https://generativelanguage.googleapis.com/v1beta/models/x?key=" +
                "AIzaAbCdEfGhIjKlMnOpQrStUvWxYz012345"
        val sanitized = LogSanitizer.sanitize(raw)

        assertFalse(sanitized.contains("AbCdEfGhIjKlMnOpQrStUvWxYz012345"))
        assertTrue(sanitized.contains("key=AIza[REDACTED]"))
    }

}
