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

    fun parsePatchProposal(rawResponse: String): Result<PatchProposal> {
        return runCatching {
            var cleaned = rawResponse.trim()
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.removePrefix("```json")
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.removePrefix("```")
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```")
            }
            cleaned = cleaned.trim()

            val firstBrace = cleaned.indexOf('{')
            val lastBrace = cleaned.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleaned = cleaned.substring(firstBrace, lastBrace + 1)
            }

            val obj = org.json.JSONObject(cleaned)
            PatchProposal(
                targetFile = obj.getString("targetFile"),
                originalSnippet = obj.getString("originalSnippet"),
                replacementSnippet = obj.getString("replacementSnippet"),
                explanation = obj.optString("explanation", "")
            )
        }
    }

    fun applyPatch(projectRoot: java.io.File, patch: PatchProposal): Result<java.io.File> {
        return runCatching {
            val cleanRelPath = patch.targetFile.trimStart('/', '\\')
            require(!cleanRelPath.contains("..")) { "Path traversal rejected: ${patch.targetFile}" }

            val targetFile = java.io.File(projectRoot, cleanRelPath)
            if (!targetFile.exists()) {
                throw java.io.FileNotFoundException("Target file not found: ${targetFile.absolutePath}")
            }

            val content = targetFile.readText(Charsets.UTF_8)
            if (!content.contains(patch.originalSnippet)) {
                throw IllegalStateException("Original snippet not found in ${targetFile.name}")
            }

            // Create automatic backup before modifying
            val backupFile = java.io.File(targetFile.parentFile, "${targetFile.name}.bak_${System.currentTimeMillis()}")
            targetFile.copyTo(backupFile, overwrite = true)

            val newContent = content.replace(patch.originalSnippet, patch.replacementSnippet)
            targetFile.writeText(newContent, Charsets.UTF_8)
            targetFile
        }
    }
}
