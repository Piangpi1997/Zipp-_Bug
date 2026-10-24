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
    fun newAppRequestRoutesToCreator() {
        assertEquals(
            ZippyCommandAction.Create("Calculator app တည်ဆောက်ပေးပါ"),
            ZippyCommandRouter.route("Calculator app တည်ဆောက်ပေးပါ")
        )
        assertEquals(
            ZippyCommandAction.Create("Create a calculator app"),
            ZippyCommandRouter.route("Create a calculator app")
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
    fun undoRoutesToLastReviewedChange() {
        assertEquals(
            ZippyCommandAction.UndoLastChange,
            ZippyCommandRouter.route("/undo")
        )
        assertEquals(
            ZippyCommandAction.UndoLastChange,
            ZippyCommandRouter.route("/restore")
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
