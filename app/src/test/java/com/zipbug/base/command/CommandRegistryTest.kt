package com.zipbug.base.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRegistryTest {

    @Test
    fun appCreatorAliasResolvesToCanonicalCommand() {
        val spec = CommandRegistry.resolve("/apcreator")
        assertNotNull(spec)
        assertEquals("/appcreator", spec?.command)
        assertEquals("/appcreator", CommandRegistry.canonical("/apcreator"))
    }

    @Test
    fun searchFindsDescriptionsAndExamples() {
        val buildResults = CommandRegistry.search("build")
        assertTrue(buildResults.any { it.command == "/apkbuilder" })

        val pythonResults = CommandRegistry.search("python")
        assertTrue(pythonResults.any { it.command == "/termux" })
    }

    @Test
    fun legacyAiZipperIsVisibleButUnavailable() {
        val spec = CommandRegistry.resolve("/aizipper")
        assertNotNull(spec)
        assertFalse(spec!!.available)
        assertTrue(spec.unavailableReason.isNotBlank())
    }

    @Test
    fun unknownCommandDoesNotResolve() {
        assertEquals(null, CommandRegistry.resolve("/does-not-exist"))
    }
}
