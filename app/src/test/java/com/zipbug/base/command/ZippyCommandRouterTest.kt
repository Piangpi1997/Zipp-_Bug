package com.zipbug.base.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZippyCommandRouterTest {

    @Test
    fun blankInputReturnsNull() {
        assertNull(ZippyCommandRouter.route("   "))
    }

    @Test
    fun plainLanguageRoutesToAsk() {
        assertEquals(
            ZippyCommandAction.Ask("add a login screen"),
            ZippyCommandRouter.route("add a login screen")
        )
    }

    @Test
    fun fixShortcutRoutesToRepair() {
        assertEquals(
            ZippyCommandAction.Fix("Unresolved reference binding"),
            ZippyCommandRouter.route("/fix Unresolved reference binding")
        )
    }

    @Test
    fun buildShortcutRoutesToDebugBuild() {
        assertEquals(
            ZippyCommandAction.BuildDebug,
            ZippyCommandRouter.route("/build")
        )
    }

    @Test
    fun navigationShortcutsRouteLocally() {
        assertEquals(
            ZippyCommandAction.OpenStudio,
            ZippyCommandRouter.route("/studio")
        )
        assertEquals(
            ZippyCommandAction.OpenProjects,
            ZippyCommandRouter.route("/projects")
        )
    }
}
