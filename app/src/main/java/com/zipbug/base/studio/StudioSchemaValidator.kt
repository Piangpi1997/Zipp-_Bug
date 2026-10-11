package com.zipbug.base.studio

import org.json.JSONArray
import org.json.JSONObject

data class StudioSchemaIssue(
    val path: String,
    val message: String
) {
    fun display(): String = "$path: $message"
}

object StudioSchemaValidator {
    const val MAX_SCHEMA_CHARS = 1_000_000
    private val allowedTypes = setOf("Text", "Button", "Input", "Card", "Image")
    private val androidId = Regex("^[a-z][a-z0-9_]*$")
    private val color = Regex("^#[0-9A-Fa-f]{6}$")

    fun validate(schema: String, requireComposeNames: Boolean = false): List<StudioSchemaIssue> {
        if (schema.length > MAX_SCHEMA_CHARS) {
            return listOf(StudioSchemaIssue("$", "schema exceeds ${MAX_SCHEMA_CHARS} characters"))
        }

        val root = runCatching { JSONObject(schema) }.getOrElse {
            return listOf(StudioSchemaIssue("$", "invalid JSON: ${it.message ?: "parse failed"}"))
        }
        val screens = root.optJSONArray("screens")
            ?: return listOf(StudioSchemaIssue("screens", "must be an array"))
        if (screens.length() == 0) {
            return listOf(StudioSchemaIssue("screens", "must contain at least one screen"))
        }

        val issues = mutableListOf<StudioSchemaIssue>()
        val screenIds = mutableSetOf<String>()
        val composeFunctions = mutableSetOf<String>()

        for (screenIndex in 0 until screens.length()) {
            val path = "screens[$screenIndex]"
            val screen = screens.optJSONObject(screenIndex)
            if (screen == null) {
                issues += StudioSchemaIssue(path, "must be an object")
                continue
            }

            val screenId = screen.optString("id", "").trim()
            if (screenId.isBlank()) {
                issues += StudioSchemaIssue("$path.id", "must be a non-empty string")
            } else if (!screenIds.add(screenId)) {
                issues += StudioSchemaIssue("$path.id", "duplicate screen id \"$screenId\"")
            }

            val screenName = screen.optString("name", "").trim()
            if (screenName.isBlank()) {
                issues += StudioSchemaIssue("$path.name", "must be a non-empty string")
            } else if (requireComposeNames) {
                val functionName = composeFunctionName(screenName)
                if (!composeFunctions.add(functionName)) {
                    issues += StudioSchemaIssue("$path.name", "produces duplicate Compose function $functionName")
                }
            }

            val nodes = screen.optJSONArray("nodes") ?: screen.optJSONArray("components")
            if (nodes == null) {
                issues += StudioSchemaIssue("$path.nodes", "must be an array")
                continue
            }
            validateNodes(nodes, path, issues)
        }
        return issues
    }

    private fun validateNodes(
        nodes: JSONArray,
        screenPath: String,
        issues: MutableList<StudioSchemaIssue>
    ) {
        val ids = mutableSetOf<String>()
        for (nodeIndex in 0 until nodes.length()) {
            val path = "$screenPath.nodes[$nodeIndex]"
            val node = nodes.optJSONObject(nodeIndex)
            if (node == null) {
                issues += StudioSchemaIssue(path, "must be an object")
                continue
            }

            val id = node.optString("id", "")
            if (!androidId.matches(id)) {
                issues += StudioSchemaIssue("$path.id", "must match Android id pattern [a-z][a-z0-9_]*")
            } else if (!ids.add(id)) {
                issues += StudioSchemaIssue("$path.id", "duplicate id \"$id\" in this screen")
            }

            val type = node.optString("type", "")
            if (type !in allowedTypes) {
                issues += StudioSchemaIssue("$path.type", "unsupported type \"$type\"")
            }

            val x = finiteNumber(node, "x", "$path.x", issues)
            val y = finiteNumber(node, "y", "$path.y", issues)
            val width = finiteNumber(node, "width", "$path.width", issues)
            val height = finiteNumber(node, "height", "$path.height", issues)
            if (x != null && x < 0.0) issues += StudioSchemaIssue("$path.x", "must be >= 0")
            if (y != null && y < 0.0) issues += StudioSchemaIssue("$path.y", "must be >= 0")
            if (width != null && width <= 0.0) issues += StudioSchemaIssue("$path.width", "must be > 0")
            if (height != null && height <= 0.0) issues += StudioSchemaIssue("$path.height", "must be > 0")

            optionalPositiveNumber(node, listOf("fontSize"), "$path.fontSize", 1.0, 200.0, issues)
            optionalPositiveNumber(node, listOf("radius", "cornerRadius"), "$path.radius", 0.0, Double.MAX_VALUE, issues)
            optionalColor(node, listOf("color", "fillColor"), "$path.color", issues)
            optionalColor(node, listOf("textColor"), "$path.textColor", issues)
        }
    }

    private fun finiteNumber(
        obj: JSONObject,
        key: String,
        path: String,
        issues: MutableList<StudioSchemaIssue>
    ): Double? {
        if (!obj.has(key)) {
            issues += StudioSchemaIssue(path, "is required and must be a finite number")
            return null
        }
        val value = numberValue(obj.opt(key))
        if (value == null || !value.isFinite()) {
            issues += StudioSchemaIssue(path, "must be a finite number")
            return null
        }
        return value
    }

    private fun optionalPositiveNumber(
        obj: JSONObject,
        keys: List<String>,
        path: String,
        min: Double,
        max: Double,
        issues: MutableList<StudioSchemaIssue>
    ) {
        val key = keys.firstOrNull { obj.has(it) } ?: return
        val value = numberValue(obj.opt(key))
        if (value == null || !value.isFinite() || value < min || value > max) {
            issues += StudioSchemaIssue(path, "must be a number from $min to $max")
        }
    }

    private fun optionalColor(
        obj: JSONObject,
        keys: List<String>,
        path: String,
        issues: MutableList<StudioSchemaIssue>
    ) {
        val key = keys.firstOrNull { obj.has(it) } ?: return
        val value = obj.optString(key, "")
        if (!color.matches(value)) {
            issues += StudioSchemaIssue(path, "must be #RRGGBB")
        }
    }

    private fun numberValue(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private fun composeFunctionName(name: String): String {
        var base = name.replace(Regex("[^A-Za-z0-9_]"), "")
        if (base.isBlank()) base = "Screen"
        if (base.firstOrNull()?.isDigit() == true) base = "Screen$base"
        return "${base}Screen"
    }
}
