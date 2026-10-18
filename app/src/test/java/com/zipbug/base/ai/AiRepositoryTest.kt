package com.zipbug.base.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRepositoryTest {

    @Test
    fun testMaskApiKeyEmpty() {
        assertEquals("(None)", AiRepository.maskApiKey(""))
        assertEquals("(None)", AiRepository.maskApiKey("   "))
    }

    @Test
    fun testMaskApiKeyShort() {
        val shortKey = "abcdef"
        val masked = AiRepository.maskApiKey(shortKey)
        assertEquals("••••ef", masked)
    }

    @Test
    fun testMaskApiKeyStandard() {
        val longKey = "sk-or-v1-abcdef1234567890xyz"
        val masked = AiRepository.maskApiKey(longKey)
        assertTrue(masked.startsWith("sk-o"))
        assertTrue(masked.endsWith("0xyz"))
        assertTrue(masked.contains("••••••••"))
    }

    @Test
    fun testValidateRequestRejectsBlankKey() {
        val req = AiRequest(
            provider = AiProvider.OPENROUTER,
            apiKey = "",
            model = "meta-llama/llama-3-8b-instruct:free",
            system = "System prompt",
            prompt = "hello"
        )
        val result = AiRepository.validateRequest(req)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("empty") == true)
    }

    @Test
    fun testValidateRequestBlocksOpenRouterKeyToOpenAi() {
        val req = AiRequest(
            provider = AiProvider.OPENAI,
            apiKey = "sk-or-v1-mytestkey",
            model = "gpt-4o-mini",
            system = "System prompt",
            prompt = "hello"
        )
        val result = AiRepository.validateRequest(req)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("OpenRouter") == true)
    }

    @Test
    fun testValidateRequestBlocksOpenRouterKeyToAnthropic() {
        val req = AiRequest(
            provider = AiProvider.ANTHROPIC,
            apiKey = "sk-or-v1-mytestkey",
            model = "claude-3-5-sonnet",
            system = "System prompt",
            prompt = "hello"
        )
        val result = AiRepository.validateRequest(req)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("sk-or-") == true)
    }

    @Test
    fun testValidateRequestAllowsOpenRouterKeyToOpenRouter() {
        val req = AiRequest(
            provider = AiProvider.OPENROUTER,
            apiKey = "sk-or-v1-mytestkey",
            model = "meta-llama/llama-3-8b-instruct:free",
            system = "System prompt",
            prompt = "hello"
        )
        val result = AiRepository.validateRequest(req)
        assertTrue(result.isSuccess)
    }

    @Test
    fun testValidateRequestBlocksOpenRouterModelOnGemini() {
        val req = AiRequest(
            provider = AiProvider.GEMINI,
            apiKey = "AIzaSyFakeKey",
            model = "openrouter/auto",
            system = "System prompt",
            prompt = "hello"
        )
        val result = AiRepository.validateRequest(req)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("OpenRouter model") == true)
    }

    @Test
    fun testValidateRequestAcceptsValidGeminiRequest() {
        val req = AiRequest(
            provider = AiProvider.GEMINI,
            apiKey = "AIzaSyFakeKey12345",
            model = "gemini-1.5-flash",
            system = "System prompt",
            prompt = "hello"
        )
        val result = AiRepository.validateRequest(req)
        assertTrue(result.isSuccess)
    }

    @Test
    fun testValidateRequestBlocksAnthropicKeyToOpenAi() {
        val req = AiRequest(
            provider = AiProvider.OPENAI,
            apiKey = "sk-ant-api03-testkey",
            model = "gpt-4.1-mini",
            system = "System prompt",
            prompt = "hello"
        )
        val result = AiRepository.validateRequest(req)
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()
                ?.message
                ?.contains("Anthropic") == true
        )
    }

    @Test
    fun testValidateRequestBlocksGeminiKeyToOpenRouter() {
        val req = AiRequest(
            provider = AiProvider.OPENROUTER,
            apiKey = "AIzaSyFakeGeminiKey123456789",
            model = "openrouter/free",
            system = "System prompt",
            prompt = "hello"
        )
        val result = AiRepository.validateRequest(req)
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()
                ?.message
                ?.contains("Gemini") == true
        )
    }

    @Test
    fun testValidateRequestBlocksOpenAiProjectKeyToAnthropic() {
        val req = AiRequest(
            provider = AiProvider.ANTHROPIC,
            apiKey = "sk-proj-testkey123456789",
            model = "claude-sonnet-4-5",
            system = "System prompt",
            prompt = "hello"
        )
        val result = AiRepository.validateRequest(req)
        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()
                ?.message
                ?.contains("OpenAI") == true
        )
    }


    @Test
    fun testSafeErrorMessageRedactsProviderSecrets() {
        val error = IllegalStateException(
            "request failed: https://example.invalid?key=" +
                "AIzaAbCdEfGhIjKlMnOpQrStUvWxYz012345"
        )

        val safe = AiRepository.safeErrorMessage(error)

        assertFalse(safe.contains("AbCdEfGhIjKlMnOpQrStUvWxYz012345"))
        assertTrue(safe.contains("AIza[REDACTED]"))
    }

}
