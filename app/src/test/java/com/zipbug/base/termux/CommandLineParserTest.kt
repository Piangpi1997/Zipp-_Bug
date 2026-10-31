package com.zipbug.base.termux

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandLineParserTest {
    @Test
    fun parsesQuotedArguments() {
        assertEquals(
            listOf(
                "/termux",
                "ffmpeg",
                "-i",
                "my video.mp4",
                "out.mp3"
            ),
            CommandLineParser.parse(
                "/termux ffmpeg -i \"my video.mp4\" out.mp3"
            )
        )
    }

    @Test
    fun parsesSingleQuotes() {
        assertEquals(
            listOf("python", "-c", "print('ok')"),
            CommandLineParser.parse(
                "python -c \"print('ok')\""
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnclosedQuote() {
        CommandLineParser.parse(
            "/termux python \"unterminated"
        )
    }
}
