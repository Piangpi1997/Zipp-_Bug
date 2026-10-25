package com.zipbug.base.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiComponentGalleryTest {

    @Test
    fun testCuratedComponentsExist() {
        val list = UiComponentGallery.curatedComponents
        assertTrue("Curated components should not be empty", list.isNotEmpty())
        val redButton = list.firstOrNull { it.id == "btn_red_glow" }
        assertNotNull("Red Glow button should exist", redButton)
        assertEquals("MIT", redButton?.license)
        assertTrue(redButton!!.xmlContent.contains("selector"))
        assertTrue(redButton.xmlContent.contains("#E53935"))
    }

    @Test
    fun testSearchByQueryAndCategory() {
        val redButtons = UiComponentGallery.search("red", "Button")
        assertTrue(redButtons.isNotEmpty())
        assertTrue(redButtons.all { it.category == "Button" })

        val backgrounds = UiComponentGallery.search("", "Background")
        assertTrue(backgrounds.isNotEmpty())
        assertTrue(backgrounds.all { it.category == "Background" })
    }

    @Test
    fun testApplyStyleToProjectDirectory() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_gallery_proj_" + System.currentTimeMillis())
        val layoutDir = File(tempDir, "app/src/main/res/layout").apply { mkdirs() }
        val layoutFile = File(layoutDir, "activity_main.xml")
        layoutFile.writeText("""<Button android:id="@+id/btnAction" android:background="@drawable/old_bg" />""")

        val item = UiComponentGallery.curatedComponents.first { it.id == "funny-dog-69" }
        val applied = UiComponentGallery.applyStyleToProject(tempDir, item)
        assertTrue("applyStyleToProject should succeed", applied)

        val drawableFile = File(tempDir, "app/src/main/res/drawable/${item.xmlDrawableName}.xml")
        assertTrue("Drawable XML should be written", drawableFile.exists())

        val updatedLayout = layoutFile.readText()
        assertTrue("Layout should reference new drawable", updatedLayout.contains("@drawable/${item.xmlDrawableName}"))

        tempDir.deleteRecursively()
    }
}
