package com.zipbug.base.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class HealthStatus(val label: String) {
    NOT_CONFIGURED("NOT CONFIGURED"),
    CONNECTING("CONNECTING"),
    READY("READY"),
    AUTH_ERROR("AUTH ERROR"),
    RATE_LIMITED("RATE LIMITED"),
    MODEL_ERROR("MODEL ERROR"),
    NETWORK_ERROR("NETWORK ERROR")
}

data class ProviderHealthResult(
    val provider: AiProvider,
    val status: HealthStatus,
    val message: String,
    val maskedKey: String
)

class AiRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        fun maskApiKey(key: String): String {
            if (key.isBlank()) return "(None)"
            if (key.length <= 8) return "••••" + key.takeLast(2)
            return key.take(4) + "••••••••" + key.takeLast(4)
        }

        fun validateRequest(req: AiRequest): Result<Unit> {
            if (req.apiKey.isBlank()) {
                return Result.failure(IllegalArgumentException("API key is empty"))
            }
            if ((req.provider == AiProvider.OPENAI || req.provider == AiProvider.ANTHROPIC) && req.apiKey.startsWith("sk-or-")) {
                return Result.failure(IllegalArgumentException("Keys starting with 'sk-or-' must not be sent to ${req.provider.label}."))
            }
            if (req.provider != AiProvider.OPENROUTER && req.model.startsWith("openrouter/")) {
                return Result.failure(IllegalArgumentException("OpenRouter model '${req.model}' cannot be used with provider ${req.provider.label}."))
            }
            return Result.success(Unit)
        }
    }

    suspend fun checkHealth(
        provider: AiProvider,
        apiKey: String,
        model: String = ""
    ): ProviderHealthResult = withContext(Dispatchers.IO) {
        val masked = maskApiKey(apiKey)
        if (apiKey.isBlank()) {
            return@withContext ProviderHealthResult(
                provider = provider,
                status = HealthStatus.NOT_CONFIGURED,
                message = "No API key configured for ${provider.label}",
                maskedKey = masked
            )
        }

        val effectiveModel = model.ifBlank { provider.defaultModel }
        val testReq = AiRequest(
            provider = provider,
            apiKey = apiKey,
            model = effectiveModel,
            system = "Health check. Respond with 'PONG'.",
            prompt = "PING",
            mode = AiMode.CHAT
        )

        val validation = validateRequest(testReq)
        if (validation.isFailure) {
            val err = validation.exceptionOrNull()?.message ?: "Validation failed"
            return@withContext ProviderHealthResult(
                provider = provider,
                status = HealthStatus.MODEL_ERROR,
                message = err,
                maskedKey = masked
            )
        }

        try {
            complete(testReq).fold(
                onSuccess = {
                    ProviderHealthResult(
                        provider = provider,
                        status = HealthStatus.READY,
                        message = "Connected to ${provider.label} ($effectiveModel)",
                        maskedKey = masked
                    )
                },
                onFailure = { ex ->
                    val msg = ex.message.orEmpty()
                    val status = when {
                        msg.contains("HTTP 401") || msg.contains("HTTP 403") -> HealthStatus.AUTH_ERROR
                        msg.contains("HTTP 429") -> HealthStatus.RATE_LIMITED
                        msg.contains("HTTP 404") || msg.contains("model", ignoreCase = true) -> HealthStatus.MODEL_ERROR
                        else -> HealthStatus.NETWORK_ERROR
                    }
                    ProviderHealthResult(
                        provider = provider,
                        status = status,
                        message = msg,
                        maskedKey = masked
                    )
                }
            )
        } catch (e: Exception) {
            ProviderHealthResult(
                provider = provider,
                status = HealthStatus.NETWORK_ERROR,
                message = e.message ?: "Network error",
                maskedKey = masked
            )
        }
    }

    suspend fun complete(req: AiRequest): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            validateRequest(req).getOrThrow()

            when (req.provider) {
                AiProvider.OPENROUTER -> openRouter(req)
                AiProvider.OPENAI -> openAi(req)
                AiProvider.GEMINI -> gemini(req)
                AiProvider.ANTHROPIC -> anthropic(req)
            }
        }
    }

    private fun post(
        url: String,
        headers: Map<String, String>,
        json: JSONObject
    ): JSONObject {
        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val clean = runCatching {
                    JSONObject(text)
                        .optJSONObject("error")
                        ?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()

                error(
                    clean?.let { "HTTP ${response.code}: $it" }
                        ?: "HTTP ${response.code}: request failed"
                )
            }
            return JSONObject(text)
        }
    }

    private fun messages(r: AiRequest): JSONArray =
        JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put("content", r.system)
            )
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", r.prompt)
            )

    private fun openRouter(r: AiRequest): String {
        val json = post(
            "https://openrouter.ai/api/v1/chat/completions",
            mapOf(
                "Authorization" to "Bearer ${r.apiKey}",
                "HTTP-Referer" to "https://github.com/Piangpi1997/Zipp-_Bug",
                "X-Title" to "Zip_Bug Antigravity"
            ),
            JSONObject()
                .put(
                    "model",
                    r.model.ifBlank { "openrouter/free" }
                )
                .put("messages", messages(r))
        )

        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun openAi(r: AiRequest): String {
        val json = post(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer ${r.apiKey}"),
            JSONObject()
                .put("model", r.model.ifBlank { "gpt-4.1-mini" })
                .put("messages", messages(r))
        )

        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun gemini(r: AiRequest): String {
        val model = r.model.ifBlank { "gemini-2.5-flash" }
        val contents = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            r.system + "\n\n" + r.prompt
                        )
                    )
                )
        )

        val json = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${r.apiKey}",
            emptyMap(),
            JSONObject().put("contents", contents)
        )

        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    private fun anthropic(r: AiRequest): String {
        val json = post(
            "https://api.anthropic.com/v1/messages",
            mapOf(
                "x-api-key" to r.apiKey,
                "anthropic-version" to "2023-06-01"
            ),
            JSONObject()
                .put("model", r.model.ifBlank { "claude-sonnet-4-5" })
                .put("max_tokens", 4096)
                .put("system", r.system)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", r.prompt)
                    )
                )
        )

        return json.getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
    }
}
