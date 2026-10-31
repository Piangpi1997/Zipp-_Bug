package com.zipbug.base.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRuntimeActivityTest {

    @Test
    fun runtimeUrlAllowsOnlyHttpLoopbackPort3131() {
        assertTrue(
            WebRuntimeActivity.isAllowedRuntimeUrl(
                "http://127.0.0.1:3131/index.html"
            )
        )
        assertTrue(
            WebRuntimeActivity.isAllowedRuntimeUrl(
                "http://localhost:3131/app/"
            )
        )

        assertFalse(
            WebRuntimeActivity.isAllowedRuntimeUrl(
                "https://127.0.0.1:3131/"
            )
        )
        assertFalse(
            WebRuntimeActivity.isAllowedRuntimeUrl(
                "http://127.0.0.1:3132/"
            )
        )
        assertFalse(
            WebRuntimeActivity.isAllowedRuntimeUrl(
                "http://127.0.0.1.evil.example:3131/"
            )
        )
        assertFalse(
            WebRuntimeActivity.isAllowedRuntimeUrl(
                "https://example.com/"
            )
        )
    }
}
