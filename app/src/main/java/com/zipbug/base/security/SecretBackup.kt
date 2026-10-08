package com.zipbug.base.security

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecretBackup {
    private const val FORMAT = "zipbug-api-key-backup"
    private const val VERSION = 1
    private const val ITERATIONS = 150_000
    private const val KEY_BITS = 256
    private val AAD = "ZIP_BUG_API_KEY_BACKUP_V1".toByteArray(Charsets.UTF_8)

    fun encrypt(keys: Map<String, String>, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 8) { "Backup passphrase must be at least 8 characters." }

        val cleanKeys = keys.filterValues { it.isNotBlank() }
        require(cleanKeys.isNotEmpty()) { "There are no API keys to back up." }

        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val kdf = preferredKdf()
        val key = deriveKey(passphrase, salt, kdf)

        val plaintext = JSONObject().apply {
            put("version", VERSION)
            val keyObject = JSONObject()
            cleanKeys.forEach { (name, value) -> keyObject.put(name, value) }
            put("keys", keyObject)
        }.toString().toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(plaintext)

        val envelope = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("kdf", kdf)
            put("iterations", ITERATIONS)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("ciphertext", b64(ciphertext))
        }

        plaintext.fill(0)
        return envelope.toString().toByteArray(Charsets.UTF_8)
    }

    fun decrypt(blob: ByteArray, passphrase: CharArray): Map<String, String> {
        require(passphrase.isNotEmpty()) { "Backup passphrase is required." }

        val envelope = JSONObject(String(blob, Charsets.UTF_8))
        require(envelope.optString("format") == FORMAT) { "This is not a Zip_Bug API key backup." }
        require(envelope.optInt("version") == VERSION) { "Unsupported backup version." }

        val kdf = envelope.getString("kdf")
        require(kdf == "PBKDF2WithHmacSHA256" || kdf == "PBKDF2WithHmacSHA1") {
            "Unsupported backup key derivation."
        }

        val iterations = envelope.getInt("iterations")
        require(iterations in 50_000..1_000_000) { "Invalid backup iteration count." }

        val salt = unb64(envelope.getString("salt"))
        val iv = unb64(envelope.getString("iv"))
        val ciphertext = unb64(envelope.getString("ciphertext"))

        require(salt.size == 16) { "Invalid backup salt." }
        require(iv.size == 12) { "Invalid backup IV." }

        val key = deriveKey(passphrase, salt, kdf, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)

        val plaintext = cipher.doFinal(ciphertext)
        return try {
            val payload = JSONObject(String(plaintext, Charsets.UTF_8))
            require(payload.optInt("version") == VERSION) { "Unsupported backup payload." }

            val keys = payload.getJSONObject("keys")
            val allowed = setOf("openrouter", "openai", "gemini", "anthropic")
            buildMap {
                allowed.forEach { name ->
                    val value = keys.optString(name, "")
                    if (value.isNotBlank()) put(name, value)
                }
            }
        } finally {
            plaintext.fill(0)
        }
    }

    private fun preferredKdf(): String =
        runCatching {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            "PBKDF2WithHmacSHA256"
        }.getOrDefault("PBKDF2WithHmacSHA1")

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        algorithm: String,
        iterations: Int = ITERATIONS
    ): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(value: ByteArray): String =
        Base64.encodeToString(value, Base64.NO_WRAP)

    private fun unb64(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)
}
