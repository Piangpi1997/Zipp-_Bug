package com.zipbug.base.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRecapActivityTest {

    @Test
    fun testValidPublicMediaUrlsPass() {
        val validUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://vimeo.com/123456789",
            "http://example.com/media/sample.mp4"
        )
        for (url in validUrls) {
            val result = MediaRecapActivity.validateMediaUrl(url)
            assertTrue("Expected $url to be valid", result.isSuccess)
        }
    }

    @Test
    fun testRejectsNonHttpSchemes() {
        val invalid = listOf(
            "ftp://example.com/file.mp3",
            "file:///sdcard/test.mp3",
            "content://media/external/audio/1"
        )
        for (url in invalid) {
            val result = MediaRecapActivity.validateMediaUrl(url)
            assertFalse("Expected $url to be rejected", result.isSuccess)
        }
    }

    @Test
    fun testRejectsLocalAndPrivateNetworkUrls() {
        val forbidden = listOf(
            "http://localhost:8080/audio.mp3",
            "http://127.0.0.1:3000/video",
            "http://192.168.1.100/stream",
            "http://10.0.0.1/media",
            "http://myserver/video" // Missing TLD
        )
        for (url in forbidden) {
            val result = MediaRecapActivity.validateMediaUrl(url)
            assertFalse("Expected private/local URL $url to be rejected", result.isSuccess)
        }
    }
}
