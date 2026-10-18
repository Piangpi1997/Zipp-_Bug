package com.zipbug.base.creator

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactViteScaffolderTest {

    @Test
    fun generatesReactViteSourceWithSafeManifest() {
        val files = ReactViteScaffolder.files(
            name = "My React App",
            goal = "A simple dashboard"
        )

        assertTrue(files.containsKey("package.json"))
        assertTrue(files.containsKey("vite.config.js"))
        assertTrue(files.containsKey("src/main.jsx"))
        assertTrue(files.containsKey("src/App.jsx"))

        assertTrue(files.containsKey("zipbug.json"))
        assertTrue(files.containsKey("app.json"))

        val manifest = JSONObject(files.getValue("zipbug.json"))
        val legacyManifest = JSONObject(files.getValue("app.json"))
        assertEquals("react-vite-source", manifest.getString("kind"))
        assertEquals("index.html", manifest.getString("entry"))
        assertEquals(
            manifest.getString("kind"),
            legacyManifest.getString("kind")
        )

        val packageJson = JSONObject(files.getValue("package.json"))
        assertTrue(
            packageJson.getJSONObject("dependencies")
                .has("react")
        )
        assertTrue(
            packageJson.getJSONObject("dependencies")
                .has("react-dom")
        )
        assertEquals(
            "vite build",
            packageJson.getJSONObject("scripts")
                .getString("build")
        )
    }

    @Test
    fun sanitizesGoalBeforeWritingReactSource() {
        val secret = "sk-or-abcdefghijklmnopqrstuvwxyz012345"
        val files = ReactViteScaffolder.files(
            name = "Safe React",
            goal = "Use $secret"
        )

        val appSource = files.getValue("src/App.jsx")
        assertTrue(!appSource.contains(secret))
        assertTrue(appSource.contains("[REDACTED]"))
    }
}
