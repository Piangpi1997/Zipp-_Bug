package com.zipbug.base.creator

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactNativeExpoScaffolderTest {

    @Test
    fun generatesExpoTypeScriptSourceWithSafeManifest() {
        val files = ReactNativeExpoScaffolder.files(
            name = "My Native App",
            packageName = "com.example.nativeapp",
            goal = "A simple mobile dashboard"
        )

        assertTrue(files.containsKey("package.json"))
        assertTrue(files.containsKey("app.json"))
        assertTrue(files.containsKey("App.tsx"))
        assertTrue(files.containsKey("tsconfig.json"))

        val manifest = JSONObject(files.getValue("zipbug.json"))
        assertEquals(
            "react-native-expo-source",
            manifest.getString("kind")
        )
        assertEquals("App.tsx", manifest.getString("entry"))

        val expoConfig = JSONObject(files.getValue("app.json"))
        assertTrue(expoConfig.has("expo"))
        assertEquals(
            "com.example.nativeapp",
            expoConfig.getJSONObject("expo")
                .getJSONObject("android")
                .getString("package")
        )

        val packageJson = JSONObject(files.getValue("package.json"))
        assertTrue(
            packageJson.getJSONObject("dependencies")
                .has("expo")
        )
        assertTrue(
            packageJson.getJSONObject("dependencies")
                .has("react-native")
        )
        assertEquals(
            "expo start",
            packageJson.getJSONObject("scripts")
                .getString("start")
        )
    }

    @Test
    fun sanitizesGoalBeforeWritingNativeSource() {
        val secret = "sk-or-abcdefghijklmnopqrstuvwxyz012345"
        val files = ReactNativeExpoScaffolder.files(
            name = "Safe Native",
            packageName = "com.example.safenative",
            goal = "Use $secret"
        )

        val appSource = files.getValue("App.tsx")
        assertTrue(!appSource.contains(secret))
        assertTrue(appSource.contains("[REDACTED]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidAndroidPackage() {
        ReactNativeExpoScaffolder.files(
            name = "Bad Package",
            packageName = "not a package",
            goal = ""
        )
    }

}
