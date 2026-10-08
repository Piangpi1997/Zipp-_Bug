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

    fun parse(rawResponse: String): Result<GeneratedProjectSpec> {
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

            val root = JSONObject(cleaned)
            val projectObj = root.optJSONObject("project") ?: JSONObject()
            val name = projectObj.optString("name", "GeneratedApp")
            val packageName = projectObj.optString("packageName", "com.example.generatedapp")
            val target = projectObj.optString("target", "ANDROID_KOTLIN")
            val minSdk = projectObj.optInt("minSdk", 24)
            val targetSdk = projectObj.optInt("targetSdk", 34)

            val permissionsList = mutableListOf<String>()
            val permArr = root.optJSONArray("permissions") ?: JSONArray()
            for (i in 0 until permArr.length()) {
                permissionsList.add(permArr.getString(i))
            }

            val depList = mutableListOf<String>()
            val depArr = root.optJSONArray("dependencies") ?: JSONArray()
            for (i in 0 until depArr.length()) {
                depList.add(depArr.getString(i))
            }

            val filesList = mutableListOf<GeneratedFile>()
            val filesArr = root.optJSONArray("files") ?: JSONArray()
            for (i in 0 until filesArr.length()) {
                val fObj = filesArr.getJSONObject(i)
                val path = fObj.getString("path")
                val content = fObj.getString("content")
                filesList.add(GeneratedFile(path = path, content = content))
            }

            val buildObj = root.optJSONObject("build")
            val buildCommand = buildObj?.optString("command", "gradle assembleDebug") ?: "gradle assembleDebug"

            GeneratedProjectSpec(
                name = name,
                packageName = packageName,
                target = target,
                minSdk = minSdk,
                targetSdk = targetSdk,
                permissions = permissionsList,
                dependencies = depList,
                files = filesList,
                buildCommand = buildCommand
            )
        }
    }
}
