package com.zipbug.base.engine

import android.content.Context
import com.zipbug.base.security.SecretStore
import com.zipbug.base.termux.LogSanitizer
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client for connecting to a local Termux HTTP engine (`http://127.0.0.1:8090`)
 * or a PC/LAN companion engine (`http://<pc-lan-ip>:8090`).
 *
 * Explain clearly to users:
 * - `127.0.0.1` / `localhost` refers to the Android device itself (e.g. a local server inside Termux
 *   or forwarded via `adb reverse tcp:8090 tcp:8090`).
 * - Connecting over Wi-Fi to a PC on the same LAN requires the PC's LAN IP address
 *   (e.g. `http://192.168.1.50:8090`).
 */
object EngineConnectionClient {

    const val PREFS_NAME = "zipbug.settings"
    const val KEY_ENGINE_URL = "engineHttpUrl"
    const val SECRET_KEY_ENGINE_TOKEN = "engine_token"
    const val DEFAULT_ENGINE_URL = "http://127.0.0.1:8090"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun clientWithTimeout(timeoutMs: Long): OkHttpClient {
        if (timeoutMs <= 0L) return httpClient
        return httpClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    data class EndpointInfo(
        val normalizedUrl: String,
        val host: String,
        val port: Int,
        val isLoopback: Boolean,
        val isPrivateLan: Boolean,
        val isHttps: Boolean,
        val guidance: String
    )

    data class EngineHealthResult(
        val connected: Boolean,
        val endpointUrl: String,
        val isLoopback: Boolean,
        val version: String,
        val capabilities: List<String>,
        val latencyMs: Long,
        val diagnosticMessage: String,
        val httpStatusCode: Int? = null
    )

    data class RemoteJobResult(
        val accepted: Boolean,
        val jobId: String?,
        val status: String,
        val stdout: String,
        val stderr: String,
        val exitCode: Int?,
        val message: String,
        val artifacts: List<String> = emptyList()
    )

    fun getSavedUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ENGINE_URL, DEFAULT_ENGINE_URL)?.trim().orEmpty()
        return raw.ifBlank { DEFAULT_ENGINE_URL }.trimEnd('/')
    }

    fun saveUrl(context: Context, url: String) {
        val normalized = url.trim().ifBlank { DEFAULT_ENGINE_URL }.trimEnd('/')
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENGINE_URL, normalized)
            .commit()
    }

    fun getSavedToken(context: Context): String {
        return runCatching {
            SecretStore(context).get(SECRET_KEY_ENGINE_TOKEN).orEmpty()
        }.getOrDefault("")
    }

    fun saveToken(context: Context, token: String) {
        runCatching {
            val store = SecretStore(context)
            val trimmed = token.trim()
            if (trimmed.isBlank()) {
                store.delete(SECRET_KEY_ENGINE_TOKEN)
            } else {
                store.put(SECRET_KEY_ENGINE_TOKEN, trimmed)
            }
        }
    }

    fun clearToken(context: Context) {
        runCatching {
            SecretStore(context).delete(SECRET_KEY_ENGINE_TOKEN)
        }
    }

    fun inspectEndpoint(rawUrl: String): Result<EndpointInfo> = runCatching {
        val trimmed = rawUrl.trim().trimEnd('/')
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "Engine URL must start with http:// or https://"
        }
        val uri = URI(trimmed)
        val host = uri.host?.lowercase() ?: error("Engine URL is missing a valid hostname or IP")
        val scheme = uri.scheme?.lowercase() ?: "http"
        val isHttps = scheme == "https"
        val port = if (uri.port != -1) uri.port else if (isHttps) 443 else 8090
        val isLoopback = host == "127.0.0.1" || host == "localhost" || host == "::1"
        val isPrivateLan = isPrivateLanHost(host)

        val guidance = when {
            isLoopback ->
                "Loopback (127.0.0.1): connects to an engine running on this Android device (Termux) or forwarded via `adb reverse tcp:$port tcp:$port`."
            isPrivateLan ->
                "LAN IP ($host): connects over Wi-Fi to a PC or companion server on the same local network."
            isHttps ->
                "Remote HTTPS endpoint ($host): encrypted remote engine connection."
            else ->
                "Public non-HTTPS endpoint ($host): use HTTPS or a private LAN/loopback address for security."
        }

        EndpointInfo(
            normalizedUrl = trimmed,
            host = host,
            port = port,
            isLoopback = isLoopback,
            isPrivateLan = isPrivateLan,
            isHttps = isHttps,
            guidance = guidance
        )
    }

    fun isPrivateLanHost(host: String): Boolean {
        if (host == "10.0.2.2") return true
        val parts = host.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return when {
            octets[0] == 10 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            else -> false
        }
    }

    fun parseHealthJson(
        endpointUrl: String,
        isLoopback: Boolean,
        latencyMs: Long,
        rawBody: String,
        httpStatusCode: Int? = 200
    ): EngineHealthResult {
        return runCatching {
            val json = JSONObject(rawBody)
            val version = json.optString("version", json.optString("engineVersion", "1.0.0"))
            val caps = mutableListOf<String>()
            val capsArray = json.optJSONArray("capabilities") ?: json.optJSONArray("tools")
            if (capsArray != null) {
                for (i in 0 until capsArray.length()) {
                    val item = capsArray.optString(i).trim()
                    if (item.isNotEmpty()) caps.add(item)
                }
            }
            val statusStr = json.optString("status", "ok")
            val connected = statusStr.equals("ok", ignoreCase = true) ||
                statusStr.equals("healthy", ignoreCase = true) ||
                statusStr.equals("ready", ignoreCase = true) ||
                json.optBoolean("ok", true)

            EngineHealthResult(
                connected = connected,
                endpointUrl = endpointUrl,
                isLoopback = isLoopback,
                version = version,
                capabilities = caps,
                latencyMs = latencyMs,
                diagnosticMessage = if (connected) {
                    "Connected to $endpointUrl (v$version, ${caps.size} capabilities, ${latencyMs}ms)"
                } else {
                    "Engine responded with non-ready status: $statusStr"
                },
                httpStatusCode = httpStatusCode
            )
        }.getOrElse {
            EngineHealthResult(
                connected = true,
                endpointUrl = endpointUrl,
                isLoopback = isLoopback,
                version = "unknown",
                capabilities = emptyList(),
                latencyMs = latencyMs,
                diagnosticMessage = "Engine reachable at $endpointUrl (${latencyMs}ms), non-JSON health response",
                httpStatusCode = httpStatusCode
            )
        }
    }

    suspend fun checkHealth(
        rawUrl: String,
        token: String = "",
        timeoutMs: Long = 5000L
    ): EngineHealthResult = withContext(Dispatchers.IO) {
        val infoResult = inspectEndpoint(rawUrl)
        if (infoResult.isFailure) {
            return@withContext EngineHealthResult(
                connected = false,
                endpointUrl = rawUrl.trim(),
                isLoopback = false,
                version = "none",
                capabilities = emptyList(),
                latencyMs = 0L,
                diagnosticMessage = infoResult.exceptionOrNull()?.message ?: "Invalid endpoint URL"
            )
        }
        val info = infoResult.getOrThrow()
        val startMs = System.currentTimeMillis()
        runCatching {
            val reqBuilder = Request.Builder()
                .url("${info.normalizedUrl}/health")
                .get()
                .header("Accept", "application/json")
            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${token.trim()}")
            }
            clientWithTimeout(timeoutMs).newCall(reqBuilder.build()).execute().use { response ->
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val authHint = if (response.code == 401 || response.code == 403) {
                        "Invalid or missing Bearer token (HTTP ${response.code})."
                    } else {
                        "HTTP ${response.code} from ${info.normalizedUrl}/health."
                    }
                    return@withContext EngineHealthResult(
                        connected = false,
                        endpointUrl = info.normalizedUrl,
                        isLoopback = info.isLoopback,
                        version = "none",
                        capabilities = emptyList(),
                        latencyMs = elapsed,
                        diagnosticMessage = "$authHint ${info.guidance}",
                        httpStatusCode = response.code
                    )
                }
                parseHealthJson(info.normalizedUrl, info.isLoopback, elapsed, body, response.code)
            }
        }.getOrElse { err ->
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val safeError = LogSanitizer.sanitize(err.message ?: err.javaClass.simpleName)
            EngineHealthResult(
                connected = false,
                endpointUrl = info.normalizedUrl,
                isLoopback = info.isLoopback,
                version = "none",
                capabilities = emptyList(),
                latencyMs = elapsed,
                diagnosticMessage = "Unreachable ($safeError). ${info.guidance}"
            )
        }
    }

    suspend fun submitJob(
        rawUrl: String,
        token: String,
        tool: String,
        args: List<String>,
        workDir: String,
        asyncMode: Boolean = false,
        timeoutMs: Long = 15000L
    ): Result<RemoteJobResult> = withContext(Dispatchers.IO) {
        runCatching {
            val info = inspectEndpoint(rawUrl).getOrThrow()
            require(TermuxBridge.isAllowedTool(tool)) {
                "Tool not in allow-list: $tool"
            }
            TermuxBridge.validateArguments(tool, args)

            val payload = JSONObject().apply {
                put("tool", tool)
                put("args", JSONArray(args))
                put("workDir", workDir)
                put("async", asyncMode)
            }
            val reqBuilder = Request.Builder()
                .url("${info.normalizedUrl}/api/jobs")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${token.trim()}")
            }

            clientWithTimeout(timeoutMs).newCall(reqBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Engine HTTP ${response.code}: ${LogSanitizer.sanitize(rawBody.take(300))}")
                }
                parseJobResponse(rawBody)
            }
        }
    }

    suspend fun getJobLogs(
        rawUrl: String,
        token: String,
        jobId: String,
        timeoutMs: Long = 5000L
    ): Result<RemoteJobResult> = withContext(Dispatchers.IO) {
        runCatching {
            val info = inspectEndpoint(rawUrl).getOrThrow()
            require(jobId.isNotBlank()) { "jobId is required" }
            val reqBuilder = Request.Builder()
                .url("${info.normalizedUrl}/api/jobs/$jobId/logs")
                .get()
                .header("Accept", "application/json")
            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${token.trim()}")
            }
            clientWithTimeout(timeoutMs).newCall(reqBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Engine HTTP ${response.code}: ${LogSanitizer.sanitize(rawBody.take(300))}")
                }
                parseJobResponse(rawBody)
            }
        }
    }

    suspend fun cancelJob(
        rawUrl: String,
        token: String,
        jobId: String,
        timeoutMs: Long = 5000L
    ): Result<RemoteJobResult> = withContext(Dispatchers.IO) {
        runCatching {
            val info = inspectEndpoint(rawUrl).getOrThrow()
            require(jobId.isNotBlank()) { "jobId is required" }
            val reqBuilder = Request.Builder()
                .url("${info.normalizedUrl}/api/jobs/$jobId/cancel")
                .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${token.trim()}")
            }
            clientWithTimeout(timeoutMs).newCall(reqBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Engine HTTP ${response.code}: ${LogSanitizer.sanitize(rawBody.take(300))}")
                }
                parseJobResponse(rawBody)
            }
        }
    }

    suspend fun fetchArtifact(
        rawUrl: String,
        token: String,
        jobId: String,
        relativePath: String,
        timeoutMs: Long = 15000L
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val info = inspectEndpoint(rawUrl).getOrThrow()
            require(jobId.isNotBlank()) { "jobId is required" }
            require(relativePath.isNotBlank() && !relativePath.contains("..")) {
                "Invalid artifact relative path"
            }
            val encodedPath = URLEncoder.encode(relativePath, "UTF-8")
            val reqBuilder = Request.Builder()
                .url("${info.normalizedUrl}/api/jobs/$jobId/artifact?path=$encodedPath")
                .get()
            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${token.trim()}")
            }
            clientWithTimeout(timeoutMs).newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errText = response.body?.string().orEmpty().take(200)
                    error("Engine HTTP ${response.code}: ${LogSanitizer.sanitize(errText)}")
                }
                response.body?.bytes() ?: error("Empty artifact response")
            }
        }
    }

    private fun parseJobResponse(rawBody: String): RemoteJobResult {
        val json = JSONObject(rawBody)
        val artifacts = mutableListOf<String>()
        val artArray = json.optJSONArray("artifacts")
        if (artArray != null) {
            for (i in 0 until artArray.length()) {
                val item = artArray.optString(i).trim()
                if (item.isNotEmpty()) artifacts.add(item)
            }
        }
        return RemoteJobResult(
            accepted = json.optBoolean("accepted", true),
            jobId = json.optString("jobId", "").ifBlank { null },
            status = json.optString("status", "COMPLETED"),
            stdout = LogSanitizer.sanitize(json.optString("stdout", "")),
            stderr = LogSanitizer.sanitize(json.optString("stderr", "")),
            exitCode = if (json.has("exitCode") && !json.isNull("exitCode")) json.optInt("exitCode") else null,
            message = LogSanitizer.sanitize(json.optString("message", "Job processed")),
            artifacts = artifacts
        )
    }
}
