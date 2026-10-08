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
        val screen1 = StudioScreen(id = "s1", name = "Main")
        val screen2 = StudioScreen(id = "s2", name = "Settings")
        val project = StudioProject(id = "p1", name = "TestProject", screens = mutableListOf(screen1, screen2))

        val json = project.toJson()
        assertEquals("p1", json.getString("id"))
        assertEquals("TestProject", json.getString("name"))
        assertEquals(2, json.getJSONArray("screens").length())
        assertEquals("s1", json.getJSONArray("screens").getJSONObject(0).getString("id"))
        assertEquals("s2", json.getJSONArray("screens").getJSONObject(1).getString("id"))
    }

    @Test
    fun exportToJetpackComposeGeneratesValidComposable() {
        val screen = StudioScreen(
            id = "s1",
            name = "Dashboard",
            components = mutableListOf(
                StudioComponent(
                    id = "c1",
                    type = "Text",
                    name = "Label",
                    text = "Analytics",
                    x = 20f,
                    y = 40f,
                    width = 160f,
                    height = 36f
                ),
                StudioComponent(
                    id = "c2",
                    type = "Button",
                    name = "Action",
                    text = "Refresh",
                    x = 20f,
                    y = 90f,
                    width = 140f,
                    height = 48f
                )
            )
        )
        val project = StudioProject(id = "p1", name = "TestProject", screens = mutableListOf(screen))
        val composeCode = project.exportToJetpackCompose(screen)

        assertTrue(composeCode.contains("@Composable"))
        assertTrue(composeCode.contains("fun DashboardScreen()"))
        assertTrue(composeCode.contains("Analytics"))
        assertTrue(composeCode.contains("Refresh"))
        assertTrue(composeCode.contains("Button("))
    }

    @Test
    fun testMultiScreenLifecycleAndComponentMutation() {
        // 1. Create Screen A
        val screenA = StudioScreen(
            id = "screen_a",
            name = "WelcomeScreen",
            components = mutableListOf(
                StudioComponent(
                    id = "comp_hero",
                    type = "Image",
                    name = "HeroImage",
                    x = 0f,
                    y = 0f,
                    width = 320f,
                    height = 200f
                )
            )
        )

        // 2. Create Screen B
        val screenB = StudioScreen(
            id = "screen_b",
            name = "Profile",
            components = mutableListOf(
                StudioComponent(
                    id = "comp_avatar",
                    type = "Image",
                    name = "Avatar",
                    x = 16f,
                    y = 16f,
                    width = 80f,
                    height = 80f
                ),
                StudioComponent(
                    id = "comp_bio",
                    type = "Text",
                    name = "BioText",
                    text = "Android & Kotlin Architect",
                    x = 16f,
                    y = 110f,
                    width = 280f,
                    height = 60f,
                    textColor = "#39D98A"
                )
            )
        )

        val project = StudioProject(
            id = "proj_multi",
            name = "MultiScreenApp",
            screens = mutableListOf(screenA, screenB)
        )

        // 3. Verify Project JSON Serialization
        val json = project.toJson()
        assertEquals(2, json.getJSONArray("screens").length())
        val screenBJson = json.getJSONArray("screens").getJSONObject(1)
        assertEquals("Profile", screenBJson.getString("name"))
        assertEquals(2, screenBJson.getJSONArray("components").length())

        // 4. Verify Layout Code Generation for Screen B
        val xmlB = project.exportToAndroidXml(screenB)
        assertTrue(xmlB.contains("@+id/comp_avatar"))
        assertTrue(xmlB.contains("@+id/comp_bio"))
        assertTrue(xmlB.contains("Android & Kotlin Architect"))

        val composeB = project.exportToJetpackCompose(screenB)
        assertTrue(composeB.contains("fun ProfileScreen()"))
        assertTrue(composeB.contains("Android & Kotlin Architect"))
    }
}
