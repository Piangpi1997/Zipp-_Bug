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
        "gemini-3.8-flash"
    ),
    ANTHROPIC(
        "Claude",
        "claude-sonnet-4-5"
    )
}

data class ChatHistoryTurn(
    val role: String,
    val content: String
)

data class AiRequest(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    val system: String,
    val prompt: String,
    val mode: AiMode = AiMode.CHAT,
    val history: List<ChatHistoryTurn> = emptyList(),
    val baseUrl: String = ""
)

/**
 * Resolves the Anthropic Messages endpoint for an optional custom
 * base URL (an Anthropic-compatible relay). A blank base URL uses
 * the official Anthropic endpoint.
 */
fun anthropicEndpoint(baseUrl: String): String {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isBlank()) return "https://api.anthropic.com/v1/messages"
    return if (base.endsWith("/v1")) {
        "$base/messages"
    } else {
        "$base/v1/messages"
    }
}
