package com.zipbug.base.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AnthropicEndpointTest {

    @Test
    fun blankUsesOfficialEndpoint() {
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            anthropicEndpoint("")
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            anthropicEndpoint("   ")
        )
    }

    @Test
    fun relayHostGetsV1Messages() {
        assertEquals(
            "https://space.stationine.com/v1/messages",
            anthropicEndpoint("https://space.stationine.com")
        )
    }

    @Test
    fun trailingSlashIsTrimmed() {
        assertEquals(
            "https://space.stationine.com/v1/messages",
            anthropicEndpoint("https://space.stationine.com/")
        )
    }

    @Test
    fun v1SuffixIsNotDoubled() {
        assertEquals(
            "https://space.stationine.com/v1/messages",
            anthropicEndpoint("https://space.stationine.com/v1")
        )
        assertEquals(
            "https://space.stationine.com/v1/messages",
            anthropicEndpoint(" https://space.stationine.com/v1/ ")
        )
    }
}
