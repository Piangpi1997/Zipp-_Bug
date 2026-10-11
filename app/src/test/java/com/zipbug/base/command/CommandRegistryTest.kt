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
        assertEquals(CommandGroups.CODING, spec?.group)
    }

    @Test
    fun topLevelCategoriesResolveAndFilter() {
        val categoryCommands = listOf(
            "/ui" to CommandGroups.UI,
            "/engine" to CommandGroups.ENGINE,
            "/tools" to CommandGroups.TOOLS,
            "/coding" to CommandGroups.CODING,
            "/future" to CommandGroups.FUTURE
        )

        categoryCommands.forEach { (command, group) ->
            val spec = CommandRegistry.resolve(command)
            assertNotNull(spec)
            assertEquals(group, spec?.group)
            assertTrue(CommandRegistry.forGroup(group).contains(spec))
        }
    }

    @Test
    fun searchFindsDescriptionsExamplesAndGroups() {
        val buildResults = CommandRegistry.search("build")
        assertTrue(buildResults.any { it.command == "/apkbuilder" })

        val pythonResults = CommandRegistry.search("python")
        assertTrue(pythonResults.any { it.command == "/termux" })

        val futureResults = CommandRegistry.search("future")
        assertTrue(futureResults.any { it.command == "/future" })
        assertTrue(futureResults.any { it.command == "/aizipper" })
    }

    @Test
    fun legacyAiZipperIsVisibleButUnavailable() {
        val spec = CommandRegistry.resolve("/aizipper")
        assertNotNull(spec)
        assertFalse(spec!!.available)
        assertEquals(CommandGroups.FUTURE, spec.group)
        assertTrue(spec.unavailableReason.isNotBlank())
    }

    @Test
    fun unknownCommandDoesNotResolve() {
        assertEquals(null, CommandRegistry.resolve("/does-not-exist"))
    }
}
