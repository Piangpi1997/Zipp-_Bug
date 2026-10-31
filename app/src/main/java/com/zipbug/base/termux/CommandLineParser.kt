package com.zipbug.base.termux

object CommandLineParser {
    fun parse(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        fun flush() {
            if (current.isNotEmpty()) {
                result += current.toString()
                current.clear()
            }
        }

        input.forEach { ch ->
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' -> escaped = true
                quote != null && ch == quote -> quote = null
                quote != null -> current.append(ch)
                ch == '"' || ch == '\'' -> quote = ch
                ch.isWhitespace() -> flush()
                else -> current.append(ch)
            }
        }

        require(quote == null) { "Unclosed quote" }
        if (escaped) current.append('\\')
        flush()
        return result
    }
}
