package com.zipbug.base.termux

object LogSanitizer {
    const val MAX_LOG_CHARS = 128 * 1024 // 128 KB characters

    private val SECRET_PATTERNS = listOf(
        Regex("""sk-or-[a-zA-Z0-9_\-]{16,}"""),
        Regex("""sk-[a-zA-Z0-9_\-]{16,}"""),
        Regex("""AIzaSy[a-zA-Z0-9_\-]{20,}"""),
        Regex("""(?i)(bearer\s+)[a-zA-Z0-9_\-\.]{16,}"""),
        Regex("""(?i)(api[_-]?key\s*[:=]\s*)[a-zA-Z0-9_\-\.]{16,}""")
    )

    fun containsRecognizableSecret(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return SECRET_PATTERNS.any { pattern ->
            pattern.containsMatchIn(raw)
        }
    }

    /**
     * Redacts known API keys and tokens, then bounds total text length to prevent
     * SQLite CursorWindow exceptions.
     */
    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text: String = raw

        // Redact secrets
        for (pattern in SECRET_PATTERNS) {
            text = text.replace(pattern) { matchResult ->
                val full = matchResult.value
                when {
                    full.startsWith("sk-or-", ignoreCase = true) -> "sk-or-[REDACTED]"
                    full.startsWith("sk-", ignoreCase = true) -> "sk-[REDACTED]"
                    full.startsWith("AIzaSy") -> "AIzaSy[REDACTED]"
                    full.startsWith("bearer ", ignoreCase = true) -> "Bearer [REDACTED]"
                    full.contains("key", ignoreCase = true) -> {
                        val colon = full.indexOf(':').takeIf { it >= 0 }
                        val equals = full.indexOf('=').takeIf { it >= 0 }
                        val separator = listOfNotNull(colon, equals).minOrNull()
                        val prefix = if (separator != null) {
                            full.substring(0, separator + 1)
                        } else {
                            "api_key:"
                        }
                        "$prefix[REDACTED]"
                    }
                    else -> "[REDACTED_SECRET]"
                }
            }
        }

        return boundLength(text)
    }

    /**
     * Bounds the string so that if it exceeds [maxChars], the middle portion is
     * replaced by an informative truncation note while preserving the head and tail.
     */
    fun boundLength(text: String, maxChars: Int = MAX_LOG_CHARS): String {
        if (text.length <= maxChars) return text
        val headChars = (maxChars / 4).coerceAtLeast(1)
        val tailChars = (maxChars - headChars).coerceAtLeast(1)
        val truncatedCount = text.length - headChars - tailChars
        if (truncatedCount <= 0) return text

        val head = text.substring(0, headChars)
        val tail = text.substring(text.length - tailChars)
        return "$head\n\n... [TRUNCATED $truncatedCount CHARACTERS TO PREVENT CURSOR WINDOW OVERFLOW] ...\n\n$tail"
    }
}
