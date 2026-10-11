package com.zipbug.base.command

data class CommandSpec(
    val command: String,
    val aliases: List<String> = emptyList(),
    val group: String,
    val description: String,
    val example: String,
    val available: Boolean = true,
    val unavailableReason: String = ""
) {
    fun matches(value: String): Boolean =
        command.equals(value, ignoreCase = true) ||
            aliases.any { it.equals(value, ignoreCase = true) }
}

object CommandGroups {
    const val UI = "UI"
    const val ENGINE = "ENGINE"
    const val TOOLS = "TOOLS"
    const val CODING = "CODING"
    const val FUTURE = "FUTURE"

    val ordered = listOf(
        UI,
        ENGINE,
        TOOLS,
        CODING,
        FUTURE
    )

    fun normalize(value: String): String? =
        ordered.firstOrNull {
            it.equals(
                value.trim().removePrefix("/"),
                ignoreCase = true
            )
        }
}

object CommandRegistry {
    val commands: List<CommandSpec> = listOf(
        // Top-level category commands.
        CommandSpec(
            command = "/ui",
            group = CommandGroups.UI,
            description = "Browse visual design and screen-building commands.",
            example = "/ui"
        ),
        CommandSpec(
            command = "/engine",
            group = CommandGroups.ENGINE,
            description = "Browse build/runtime engine commands.",
            example = "/engine"
        ),
        CommandSpec(
            command = "/tools",
            group = CommandGroups.TOOLS,
            description = "Browse utilities, media tools, project inspection, and help.",
            example = "/tools"
        ),
        CommandSpec(
            command = "/coding",
            group = CommandGroups.CODING,
            description = "Browse source-generation and AI coding commands.",
            example = "/coding"
        ),
        CommandSpec(
            command = "/future",
            group = CommandGroups.FUTURE,
            description = "Browse planned commands. Future items stay disabled until implemented.",
            example = "/future"
        ),

        // UI
        CommandSpec(
            command = "/likefigma",
            group = CommandGroups.UI,
            description = "Open the visual Screen Lab editor.",
            example = "/likefigma"
        ),

        // ENGINE
        CommandSpec(
            command = "/apkbuilder",
            group = CommandGroups.ENGINE,
            description = "Run the controlled debug APK workflow: validation, dependencies, Gradle build, packaging, and verification. Release signing is separate.",
            example = "/apkbuilder --debug"
        ),
        CommandSpec(
            command = "/termux",
            group = CommandGroups.ENGINE,
            description = "Run one allow-listed Termux tool with explicit arguments.",
            example = "/termux python --version"
        ),

        // TOOLS
        CommandSpec(
            command = "/projects",
            group = CommandGroups.TOOLS,
            description = "Open imported projects, runtimes, and APK artifacts.",
            example = "/projects"
        ),
        CommandSpec(
            command = "/tts",
            group = CommandGroups.TOOLS,
            description = "Open Myanmar Edge-TTS Studio.",
            example = "/tts"
        ),
        CommandSpec(
            command = "/socialrecap",
            group = CommandGroups.TOOLS,
            description = "Open the authorized media Social Recap workflow.",
            example = "/socialrecap"
        ),
        CommandSpec(
            command = "/help",
            group = CommandGroups.TOOLS,
            description = "Search supported commands, descriptions, aliases, and examples.",
            example = "/help"
        ),

        // CODING
        CommandSpec(
            command = "/appcreator",
            aliases = listOf("/apcreator"),
            group = CommandGroups.CODING,
            description = "Open the Kotlin/XML and mini-app source creator.",
            example = "/appcreator"
        ),
        CommandSpec(
            command = "/aicreator",
            group = CommandGroups.CODING,
            description = "Open AI in App Creator mode for a structured project spec.",
            example = "/aicreator"
        ),

        // FUTURE
        CommandSpec(
            command = "/aizipper",
            group = CommandGroups.FUTURE,
            description = "Legacy command reserved for a future AI ZIP workflow.",
            example = "/aizipper",
            available = false,
            unavailableReason = "AI ZIP generation is not implemented yet. Use /appcreator or /projects instead."
        )
    )

    fun resolve(value: String): CommandSpec? =
        commands.firstOrNull { it.matches(value.trim()) }

    fun forGroup(group: String): List<CommandSpec> {
        val normalized = CommandGroups.normalize(group)
            ?: return emptyList()

        return commands.filter {
            it.group.equals(
                normalized,
                ignoreCase = true
            )
        }
    }

    fun search(query: String): List<CommandSpec> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return orderedCommands()

        return orderedCommands().filter { spec ->
            buildString {
                append(spec.command)
                append(' ')
                append(spec.aliases.joinToString(" "))
                append(' ')
                append(spec.group)
                append(' ')
                append(spec.description)
                append(' ')
                append(spec.example)
            }.lowercase().contains(needle)
        }
    }

    fun canonical(value: String): String? =
        resolve(value)?.command

    private fun orderedCommands(): List<CommandSpec> =
        CommandGroups.ordered.flatMap { group ->
            commands.filter { it.group == group }
        }
}
