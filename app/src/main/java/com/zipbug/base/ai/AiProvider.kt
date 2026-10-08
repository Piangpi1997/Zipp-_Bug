package com.zipbug.base.ai

enum class AiProvider(
    val label: String,
    val defaultModel: String
) {
    OPENROUTER(
        "OpenRouter • Free",
        "openrouter/free"
    ),
    OPENAI(
        "OpenAI",
        "gpt-4.1-mini"
    ),
    GEMINI(
        "Gemini",
        "gemini-2.5-flash"
    ),
    ANTHROPIC(
        "Claude",
        "claude-sonnet-4-5"
    )
}

data class AiRequest(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    val system: String,
    val prompt: String
)
