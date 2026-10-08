package com.zipbug.base.repair

data class CompilerError(
    val filePath: String,
    val line: Int,
    val column: Int,
    val message: String,
    val raw: String
)

data class PatchProposal(
    val targetFile: String,
    val originalSnippet: String,
    val replacementSnippet: String,
    val explanation: String
)

object CompilerErrorParser {
    private val KOTLIN_ERROR_REGEX = Regex("""e:\s*([^\s:]+):\s*\((\d+),\s*(\d+)\):\s*(.+)""")
    private val JAVAC_ERROR_REGEX = Regex("""([^\s:]+):(\d+):\s*error:\s*(.+)""")

    fun parseErrors(rawLog: String): List<CompilerError> {
        val errors = mutableListOf<CompilerError>()
        for (line in rawLog.lines()) {
            val ktMatch = KOTLIN_ERROR_REGEX.find(line)
            if (ktMatch != null) {
                val (file, l, c, msg) = ktMatch.destructured
                errors.add(CompilerError(file, l.toIntOrNull() ?: 0, c.toIntOrNull() ?: 0, msg, line))
                continue
            }

            val jMatch = JAVAC_ERROR_REGEX.find(line)
            if (jMatch != null) {
                val (file, l, msg) = jMatch.destructured
                errors.add(CompilerError(file, l.toIntOrNull() ?: 0, 0, msg, line))
            }
        }
        return errors
    }

    fun buildAiRepairPrompt(projectName: String, errors: List<CompilerError>, sourceSnippet: String): String {
        val errorSummary = errors.take(10).joinToString("\n") { "- ${it.filePath}:${it.line} => ${it.message}" }
        return """
Build failed for project '$projectName'.
Compiler Errors:
$errorSummary

Relevant Source Context:
$sourceSnippet

Analyze the compiler errors and provide a targeted patch proposal in JSON format:
{
  "targetFile": "path/to/file",
  "originalSnippet": "exact code block to replace",
  "replacementSnippet": "fixed code block",
  "explanation": "why this fixes the compilation error"
}
""".trimIndent()
    }
}
