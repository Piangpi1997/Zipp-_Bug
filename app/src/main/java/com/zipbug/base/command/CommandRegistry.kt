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

object CommandRegistry {
    val commands: List<CommandSpec> = listOf(
        CommandSpec(
            command = "/appcreator",
            aliases = listOf("/apcreator"),
            group = "Build",
            description = "Open the Kotlin/XML and mini-app source creator.",
            example = "/appcreator"
        ),
        CommandSpec(
            command = "/apkbuilder",
            group = "Build",
            description = "Run the controlled debug APK workflow: validation, dependencies, Gradle build, packaging, and verification. Release signing is separate.",
            example = "/apkbuilder --debug"
        ),
        CommandSpec(
            command = "/likefigma",
            group = "Design",
            description = "Open the visual Screen Lab editor.",
            example = "/likefigma"
        ),
        CommandSpec(
            command = "/aicreator",
            group = "Design",
            description = "Open AI in App Creator mode for a structured project spec.",
            example = "/aicreator"
        ),
        CommandSpec(
            command = "/tts",
            group = "Media",
            description = "Open Myanmar Edge-TTS Studio.",
            example = "/tts"
        ),
        CommandSpec(
            command = "/socialrecap",
            group = "Media",
            description = "Open the authorized media Social Recap workflow.",
            example = "/socialrecap"
        ),
        CommandSpec(
            command = "/termux",
            group = "Tools",
            description = "Run one allow-listed Termux tool with explicit arguments.",
            example = "/termux python --version"
        ),
        CommandSpec(
            command = "/projects",
            group = "Tools",
            description = "Open imported projects, runtimes, and APK artifacts.",
            example = "/projects"
        ),
        CommandSpec(
            command = "/help",
            group = "Tools",
            description = "Search supported commands, descriptions, aliases, and examples.",
            example = "/help"
        ),
        CommandSpec(
            command = "/aizipper",
            group = "Tools",
            description = "Legacy command reserved for a future AI ZIP workflow.",
            example = "/aizipper",
            available = false,
            unavailableReason = "AI ZIP generation is not implemented yet. Use /appcreator or /projects instead."
        )
    )

    fun resolve(value: String): CommandSpec? =
        commands.firstOrNull { it.matches(value.trim()) }

    fun search(query: String): List<CommandSpec> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return commands

        return commands.filter { spec ->
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
}
