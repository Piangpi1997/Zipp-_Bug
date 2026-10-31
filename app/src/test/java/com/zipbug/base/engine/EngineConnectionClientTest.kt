package com.zipbug.base.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineConnectionClientTest {

    @Test
    fun `inspectEndpoint distinguishes loopback from private LAN IP`() {
        val loopback = EngineConnectionClient.inspectEndpoint("http://127.0.0.1:8090")
        assertTrue(loopback.isSuccess)
        assertTrue(loopback.getOrThrow().isLoopback)
        assertFalse(loopback.getOrThrow().isPrivateLan)
        assertEquals(8090, loopback.getOrThrow().port)

        val lan = EngineConnectionClient.inspectEndpoint("http://192.168.1.42:8090")
        assertTrue(lan.isSuccess)
        assertFalse(lan.getOrThrow().isLoopback)
        assertTrue(lan.getOrThrow().isPrivateLan)
    }

    @Test
    fun `inspectEndpoint rejects non-http schemes`() {
        val bad = EngineConnectionClient.inspectEndpoint("ftp://127.0.0.1:8090")
        assertTrue(bad.isFailure)
    }

    @Test
    fun `parseHealthJson extracts version and capabilities`() {
        val raw = """{"status":"ok","version":"2.1.0","capabilities":["node","npm","gradle","ffmpeg"]}"""
        val health = EngineConnectionClient.parseHealthJson(
            endpointUrl = "http://127.0.0.1:8090",
            isLoopback = true,
            latencyMs = 12L,
            rawBody = raw
        )
        assertTrue(health.connected)
        assertEquals("2.1.0", health.version)
        assertEquals(listOf("node", "npm", "gradle", "ffmpeg"), health.capabilities)
    }
}
