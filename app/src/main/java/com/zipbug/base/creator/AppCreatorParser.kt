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
    val activities: List<String> = emptyList(),
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


            val activities = mutableListOf<String>()
            val declaredLayouts = mutableListOf<String>()
            val screensArr = root.optJSONArray("screens") ?: JSONArray()
            require(screensArr.length() <= 32) {
                "Too many declared screens"
            }
            for (i in 0 until screensArr.length()) {
                val screen = screensArr.getJSONObject(i)
                val activity = screen.optString("activity").trim()
                if (activity.isNotBlank()) {
                    require(
                        ACTIVITY_CLASS_REGEX.matches(activity) &&
                            activity.startsWith(packageName + ".")
                    ) {
                        "screens[$i].activity must be a class inside $packageName"
                    }
                    activities.add(activity)
                }

                val layout = screen.optString("layout").trim()
                if (layout.isNotBlank()) {
                    val normalizedLayout = normalizePath(layout)
                    require(
                        normalizedLayout.startsWith("app/src/main/res/layout/") &&
                            normalizedLayout.endsWith(".xml", ignoreCase = true)
                    ) {
                        "screens[$i].layout must point inside app/src/main/res/layout"
                    }
                    declaredLayouts.add(normalizedLayout)
                }
            }

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

                validateGeneratedFileQuality(path, content)

                filesList.add(
                    GeneratedFile(
                        path = path,
                        content = content
                    )
                )
            }

            validateGeneratedProjectCoherence(
                packageName = packageName,
                dependencies = depList,
                files = filesList,
                activities = activities,
                declaredLayouts = declaredLayouts
            )

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
                activities = activities.distinct(),
                files = filesList,
                buildCommand = CONTROLLED_BUILD_COMMAND
            )
        }
    }

    private val ACTIVITY_CLASS_REGEX =
        Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$")

    private val CONTROLLED_PROJECT_PATHS = setOf(
        "settings.gradle",
        "settings.gradle.kts",
        "build.gradle",
        "build.gradle.kts",
        "gradle.properties",
        "app/build.gradle",
        "app/build.gradle.kts",
        "app/src/main/AndroidManifest.xml"
    )

    private fun normalizePath(path: String): String =
        path.trim().replace('\\', '/').trimStart('/')

    private fun validateGeneratedProjectCoherence(
        packageName: String,
        dependencies: List<String>,
        files: List<GeneratedFile>,
        activities: List<String>,
        declaredLayouts: List<String>
    ) {
        val normalizedFiles = files.associateBy {
            normalizePath(it.path)
        }
        require(normalizedFiles.size == files.size) {
            "Generated project contains duplicate file paths"
        }

        for ((path, file) in normalizedFiles) {
            require(path !in CONTROLLED_PROJECT_PATHS) {
                "AI must not replace controlled build or manifest file: $path. " +
                    "Use project dependencies, permissions, and screens instead."
            }
            require(
                !path.equals("AndroidManifest.xml", ignoreCase = true)
            ) {
                "AndroidManifest.xml must not be generated at project root. " +
                    "Zip_Bug creates the controlled app manifest from screens and permissions."
            }

            if (
                path.startsWith("app/src/main/res/layout/") &&
                path.endsWith(".xml", ignoreCase = true)
            ) {
                require(
                    file.content.contains(
                        "xmlns:android=\"http://schemas.android.com/apk/res/android\""
                    )
                ) {
                    "Android layout uses a missing or invalid android namespace: $path"
                }
            }

            if (
                path.startsWith("app/src/main/java/") &&
                path.endsWith(".kt", ignoreCase = true)
            ) {
                val packageMatch =
                    Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)")
                        .find(file.content)
                val sourcePackage = packageMatch?.groupValues?.getOrNull(1)
                require(
                    sourcePackage == packageName ||
                        sourcePackage?.startsWith(packageName + ".") == true
                ) {
                    "Kotlin package must stay inside $packageName: $path"
                }

                val source = file.content
                require(!source.contains("android.R.layout.simple_text_view")) {
                    "Unknown Android layout constant simple_text_view in $path"
                }
                require(!source.contains(".getChildListeners()")) {
                    "ViewGroup.getChildListeners() is not an Android API: $path"
                }
                require(
                    !(source.contains("TextWatcher()") &&
                        source.contains("override fun onSubmitEvent("))
                ) {
                    "TextWatcher has no onSubmitEvent callback: $path"
                }
                require(
                    !Regex("""extras\?\.getLong\(\s*\d+\s*\)""")
                        .containsMatchIn(source)
                ) {
                    "Bundle.getLong requires a String key, not a numeric index: $path"
                }
                require(
                    !(source.contains("ArrayAdapter<") &&
                        source.contains(".submitList("))
                ) {
                    "ArrayAdapter does not provide submitList(): $path"
                }
            }
        }

        require(
            dependencies.none {
                it.startsWith("room:room-", ignoreCase = true)
            }
        ) {
            "Room dependencies must use androidx.room:room-* coordinates"
        }

        val roomEnabled = dependencies.any {
            it.startsWith("androidx.room:room-runtime:")
        }
        if (roomEnabled) {
            val databaseSources = files.filter {
                it.content.contains("@Database")
            }
            for (databaseSource in databaseSources) {
                val entities = Regex(
                    """entities\s*=\s*\[([^]]+)]"""
                ).find(databaseSource.content)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()

                Regex("""([A-Za-z_][A-Za-z0-9_]*)::class""")
                    .findAll(entities)
                    .map { it.groupValues[1] }
                    .forEach { entityClass ->
                        val entityFile = files.firstOrNull {
                            normalizePath(it.path)
                                .endsWith("/$entityClass.kt")
                        }
                        require(
                            entityFile != null &&
                                entityFile.content.contains("@Entity")
                        ) {
                            "Room entity $entityClass must be declared with @Entity"
                        }
                    }
            }
        }

        for (activity in activities.distinct()) {
            val expected =
                "app/src/main/java/" +
                    activity.replace('.', '/') +
                    ".kt"
            require(
                normalizedFiles.containsKey(expected) ||
                    activity == packageName + ".MainActivity"
            ) {
                "Screen activity source is missing: $expected"
            }
        }

        for (layout in declaredLayouts.distinct()) {
            require(normalizedFiles.containsKey(layout)) {
                "Declared screen layout is missing: $layout"
            }
        }
    }

    private fun validateGeneratedFileQuality(
        path: String,
        content: String
    ) {
        val trimmed = content.trim()
        val lower = trimmed.lowercase()

        require(trimmed.isNotBlank()) {
            "Generated file is empty: $path"
        }

        require(
            !lower.contains("todo()") &&
                !lower.contains("notimplementederror") &&
                !lower.contains("// todo") &&
                !lower.contains("/* todo") &&
                trimmed != "..." &&
                trimmed != "// ..."
        ) {
            "Generated file is a placeholder and must be regenerated: $path"
        }

        if (path.endsWith(".kt", ignoreCase = true)) {
            require(trimmed.length >= 40) {
                "Generated Kotlin file is too small to be complete: $path"
            }

            if (path.startsWith("app/src/main/java/")) {
                require(
                    Regex("(?m)^\\s*package\\s+[A-Za-z_][A-Za-z0-9_.]*")
                        .containsMatchIn(trimmed)
                ) {
                    "Generated Kotlin source is missing a package declaration: $path"
                }
            }

            val implementationFile = path.substringAfterLast('/')
                .removeSuffix(".kt")
                .let { name ->
                    name.endsWith("Activity") ||
                        name.endsWith("Screen") ||
                        name.endsWith("ViewModel") ||
                        name.endsWith("Repository") ||
                        name.endsWith("Database")
                }

            if (implementationFile) {
                require(trimmed.length >= 120) {
                    "Generated implementation is only a stub: $path"
                }
            }
        }

        if (path.endsWith(".xml", ignoreCase = true)) {
            require(trimmed.startsWith("<")) {
                "Generated XML is not valid XML text: $path"
            }
            require(trimmed.length >= 80) {
                "Generated XML file is too small to be complete: $path"
            }
        }
    }

}
