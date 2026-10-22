package com.zipbug.base.command

sealed interface ZippyCommandAction {
    data class Ask(val prompt: String) : ZippyCommandAction
    data class Fix(val prompt: String) : ZippyCommandAction
    data object BuildDebug : ZippyCommandAction
    data object OpenStudio : ZippyCommandAction
    data object OpenProjects : ZippyCommandAction
    data object OpenAppCreator : ZippyCommandAction
    data object UndoLastChange : ZippyCommandAction
}

object ZippyCommandRouter {
    fun route(raw: String): ZippyCommandAction? {
        val input = raw.trim()
        if (input.isBlank()) return null

        fun remainder(command: String): String =
            input.removePrefix(command).trim()

        return when {
            input.equals("/undo", ignoreCase = true) ||
                input.equals("/restore", ignoreCase = true) ->
                ZippyCommandAction.UndoLastChange

            input.equals("/build", ignoreCase = true) ||
                input.equals("build apk", ignoreCase = true) ->
                ZippyCommandAction.BuildDebug

            input.equals("/studio", ignoreCase = true) ->
                ZippyCommandAction.OpenStudio

            input.equals("/projects", ignoreCase = true) ->
                ZippyCommandAction.OpenProjects

            input.equals("/appcreator", ignoreCase = true) ||
                input.equals("/aicreator", ignoreCase = true) ->
                ZippyCommandAction.OpenAppCreator

            input.startsWith("/fix ", ignoreCase = true) ->
                ZippyCommandAction.Fix(remainder(input.substringBefore(' ')))

            input.equals("/fix", ignoreCase = true) ->
                ZippyCommandAction.Fix(
                    "Analyze the current build or runtime failure and propose the smallest safe patch."
                )

            input.startsWith("fix:", ignoreCase = true) ->
                ZippyCommandAction.Fix(input.substringAfter(':').trim())

            input.startsWith("/askzippy ", ignoreCase = true) ->
                ZippyCommandAction.Ask(remainder(input.substringBefore(' ')))

            input.startsWith("/ask ", ignoreCase = true) ->
                ZippyCommandAction.Ask(remainder(input.substringBefore(' ')))

            else -> ZippyCommandAction.Ask(input)
        }
    }
}
