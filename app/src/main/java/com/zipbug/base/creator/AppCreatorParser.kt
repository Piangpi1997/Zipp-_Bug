package com.zipbug.base.creator

import org.json.JSONArray
import org.json.JSONObject

data class GeneratedFile(
    val path: String,
    val content: String
)

data class GeneratedProjectSpec(
    val name: String,
    val packageName: String,
    val target: String,
    val minSdk: Int = 24,
    val targetSdk: Int = 34,
    val permissions: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val files: List<GeneratedFile> = emptyList(),
    val buildCommand: String = "gradle assembleDebug"
)

object AppCreatorParser {
    private const val MAX_RESPONSE_CHARS = 2_000_000
    private const val MAX_FILES = 120
    private const val MAX_FILE_CHARS = 500_000
    private const val MAX_TOTAL_FILE_CHARS = 5_000_000
    private const val CONTROLLED_BUILD_COMMAND =
        "gradle --no-daemon assembleDebug"

    fun parse(rawResponse: String): Result<GeneratedProjectSpec> {
        return runCatching {
            require(rawResponse.length <= MAX_RESPONSE_CHARS) {
                "AI App Creator response is too large"
            }

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

            val root = JSONObject(cleaned)
            val projectObj = root.optJSONObject("project") ?: JSONObject()
            val name = projectObj
                .optString("name", "GeneratedApp")
                .trim()
            val packageName = projectObj
                .optString(
                    "packageName",
                    "com.example.generatedapp"
                )
                .trim()
            val target = projectObj
                .optString("target", "ANDROID_KOTLIN")
                .trim()
                .uppercase()
            val minSdk = projectObj.optInt("minSdk", 24)
            val targetSdk = projectObj.optInt("targetSdk", 34)

            require(target == "ANDROID_KOTLIN") {
                "AI App Creator currently supports ANDROID_KOTLIN projects only"
            }

            val permissionsList = mutableListOf<String>()
            val permArr = root.optJSONArray("permissions") ?: JSONArray()
            require(permArr.length() <= 32) {
                "Too many requested permissions"
            }
            for (i in 0 until permArr.length()) {
                permissionsList.add(
                    permArr.getString(i).trim()
                )
            }

            val depList = mutableListOf<String>()
            val depArr = root.optJSONArray("dependencies") ?: JSONArray()
            require(depArr.length() <= 64) {
                "Too many requested dependencies"
            }
            for (i in 0 until depArr.length()) {
                depList.add(
                    depArr.getString(i).trim()
                )
            }

            val filesList = mutableListOf<GeneratedFile>()
            val filesArr = root.optJSONArray("files") ?: JSONArray()
            require(filesArr.length() <= MAX_FILES) {
                "AI project contains too many generated files"
            }

            var totalFileChars = 0
            for (i in 0 until filesArr.length()) {
                val fObj = filesArr.getJSONObject(i)
                val path = fObj.getString("path").trim()
                val content = fObj.getString("content")

                require(path.isNotBlank() && path.length <= 240) {
                    "Generated file path is blank or too long"
                }
                require(content.length <= MAX_FILE_CHARS) {
                    "Generated file is too large: " + path
                }

                totalFileChars += content.length
                require(totalFileChars <= MAX_TOTAL_FILE_CHARS) {
                    "Generated source exceeds the project size limit"
                }

                filesList.add(
                    GeneratedFile(
                        path = path,
                        content = content
                    )
                )
            }

            AppScaffolder.validate(
                AppSpec(
                    name = name,
                    packageName = packageName,
                    goal = "",
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    permissions = permissionsList,
                    dependencies = depList
                )
            )

            GeneratedProjectSpec(
                name = name,
                packageName = packageName,
                target = target,
                minSdk = minSdk,
                targetSdk = targetSdk,
                permissions = permissionsList.distinct(),
                dependencies = depList.distinct(),
                files = filesList,
                buildCommand = CONTROLLED_BUILD_COMMAND
            )
        }
    }
}
