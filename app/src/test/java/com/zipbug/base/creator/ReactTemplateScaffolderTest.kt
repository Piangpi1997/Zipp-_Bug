package com.zipbug.base.creator

import org.junit.Assert.assertTrue
import org.junit.Test

class ReactTemplateScaffolderTest {

    @Test
    fun `ReactTemplateScaffolder generates valid Vite and React starter files`() {
        val files = ReactTemplateScaffolder.files("My React Dashboard", "Interactive analytics starter")
        assertTrue(files.containsKey("package.json"))
        assertTrue(files.containsKey("vite.config.js"))
        assertTrue(files.containsKey("index.html"))
        assertTrue(files.containsKey("src/main.jsx"))
        assertTrue(files.containsKey("src/App.jsx"))
        assertTrue(files.containsKey("src/styles.css"))
        assertTrue(files.containsKey("app.json"))
        assertTrue(files["package.json"]!!.contains("\"vite\""))
        assertTrue(files["app.json"]!!.contains("\"react-web\""))
    }
}
