package com.zipbug.base.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioModelsTest {

    @Test
    fun exportToAndroidXmlGeneratesValidLayout() {
        val screen = StudioScreen(
            id = "screen_1",
            name = "Home",
            components = mutableListOf(
                StudioComponent(
                    id = "title_text",
                    type = "Text",
                    name = "Title",
                    text = "Welcome",
                    x = 16f,
                    y = 32f,
                    width = 200f,
                    height = 40f
                ),
                StudioComponent(
                    id = "submit_btn",
                    type = "Button",
                    name = "Submit",
                    text = "Submit Action",
                    x = 16f,
                    y = 80f,
                    width = 120f,
                    height = 48f
                )
            )
        )

        val project = StudioProject(
            id = "proj_1",
            name = "TestStudio",
            screens = mutableListOf(screen)
        )

        val xml = project.exportToAndroidXml(screen)
        assertTrue(xml.contains("ConstraintLayout"))
        assertTrue(xml.contains("@+id/title_text"))
        assertTrue(xml.contains("@+id/submit_btn"))
        assertTrue(xml.contains("Welcome"))
        assertTrue(xml.contains("Submit Action"))
    }

    @Test
    fun studioProjectToJsonSerializesCorrectly() {
        val screen = StudioScreen(id = "s1", name = "Main")
        val project = StudioProject(id = "p1", name = "TestProject", screens = mutableListOf(screen))

        val json = project.toJson()
        assertEquals("p1", json.getString("id"))
        assertEquals("TestProject", json.getString("name"))
        assertEquals(1, json.getJSONArray("screens").length())
    }
}
