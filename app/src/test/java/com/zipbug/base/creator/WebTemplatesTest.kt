package com.zipbug.base.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTemplatesTest {

    @Test
    fun catalogIdsAreUniqueAndComplete() {
        val ids = WebTemplates.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(WebTemplates.ALL.size >= 12)
        for (template in WebTemplates.ALL) {
            assertTrue(template.title.isNotBlank())
            assertTrue(template.description.isNotBlank())
            assertTrue(template.snippet.isNotBlank())
            assertTrue(template.html.contains("<html"))
            assertTrue(template.icon.isNotBlank())
            assertTrue(
                "bad colour for ${template.id}",
                template.colorHex.matches(Regex("^#[0-9a-fA-F]{6}$"))
            )
            assertEquals(template, WebTemplates.byId(template.id))
        }
    }

    @Test
    fun everyCategoryHasTemplates() {
        for (category in WebTemplateCategory.values()) {
            assertTrue(
                "empty category: $category",
                WebTemplates.byCategory(category).isNotEmpty()
            )
        }
    }

    @Test
    fun apiTemplatesExerciseTheirBridgeCall() {
        val expectations = mapOf(
            "vibrate" to "AndroidAPI.vibratePattern",
            "torch" to "AndroidAPI.torch",
            "notification" to "AndroidAPI.showNotification",
            "network-status" to "AndroidAPI.networkStatus",
            "keep-screen-on" to "AndroidAPI.setKeepScreenOn",
            "app-storage" to "AndroidAPI.storageSave",
            "clipboard" to "AndroidAPI.getClipboard",
            "share" to "AndroidAPI.shareText",
            "device-battery" to "AndroidAPI.getBattery"
        )
        for ((id, needle) in expectations) {
            val template = WebTemplates.byId(id)
            assertTrue("missing template: $id", template != null)
            assertTrue(
                "template $id does not call $needle",
                template!!.html.contains(needle)
            )
            assertTrue(
                "template $id snippet should show the API",
                template.snippet.contains("AndroidAPI") ||
                    template.snippet.contains("localStorage")
            )
        }
    }

    @Test
    fun asyncTemplatesRegisterTheEventCallback() {
        for (id in listOf(
            "vibrate", "torch", "notification", "network-status",
            "app-storage", "clipboard", "device-battery"
        )) {
            val template = WebTemplates.byId(id)!!
            assertTrue(
                "template $id must define window.onAndroidEvent",
                template.html.contains("window.onAndroidEvent")
            )
        }
    }

    @Test
    fun notificationTemplateRequiresNotificationHardware() {
        val template = WebTemplates.byId("notification")!!
        assertTrue(WebHardware.NOTIFICATIONS in template.hardware)
        // Templates that need no hardware must not request any.
        assertTrue(WebTemplates.byId("vibrate")!!.hardware.isEmpty())
        assertFalse(
            WebTemplates.byId("blank")!!.html.contains("AndroidAPI.showNotification")
        )
    }

    @Test
    fun libraryTemplatesLoadTheirCdn() {
        assertTrue(
            WebTemplates.byId("bootstrap")!!.html
                .contains("cdn.jsdelivr.net/npm/bootstrap@5.3.3")
        )
        assertTrue(
            WebTemplates.byId("tailwind")!!.html
                .contains("cdn.tailwindcss.com")
        )
    }
}
