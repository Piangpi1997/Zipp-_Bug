package com.zipbug.base.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun complete(req: AiRequest): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(req.apiKey.isNotBlank()) { "API key is empty" }
            when (req.provider) {
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
                error("HTTP ${response.code}: ${text.take(800)}")
            }
            return JSONObject(text)
        }
    }

    private fun openAi(r: AiRequest): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", r.system))
            .put(JSONObject().put("role", "user").put("content", r.prompt))

        val json = post(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer ${r.apiKey}"),
            JSONObject()
                .put("model", r.model.ifBlank { "gpt-4.1-mini" })
                .put("messages", messages)
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
                        JSONObject().put("text", r.system + "\n\n" + r.prompt)
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
        val messages = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", r.prompt)
        )

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
                .put("messages", messages)
        )

        return json.getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
    }
}
