package com.zipbug.base.ai

enum class AiProvider(val label: String) {
    OPENROUTER("OpenRouter • Free"),
    OPENAI("OpenAI"),
    GEMINI("Gemini"),
    ANTHROPIC("Claude")
}

data class AiRequest(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    val system: String,
    val prompt: String
)
