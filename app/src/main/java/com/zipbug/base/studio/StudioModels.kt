package com.zipbug.base.studio

import org.json.JSONArray
import org.json.JSONObject

data class StudioComponent(
    val id: String,
    val type: String, // "Text", "Button", "Input", "Card", "Image"
    var name: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = 120f,
    var height: Float = 48f,
    var text: String = "",
    var fontSize: Float = 14f,
    var fontWeight: String = "normal",
    var textColor: String = "#FFFFFF",
    var fillColor: String = "#1E1E1E",
    var borderColor: String = "#333333",
    var borderWidth: Float = 1f,
    var cornerRadius: Float = 8f,
    var opacity: Float = 1f,
    var isVisible: Boolean = true,
    var zIndex: Int = 0
)

data class StudioScreen(
    val id: String,
    var name: String,
    val components: MutableList<StudioComponent> = mutableListOf()
)

data class StudioProject(
    val id: String,
    var name: String,
    val screens: MutableList<StudioScreen> = mutableListOf()
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("id", id)
        root.put("name", name)
        val screensArr = JSONArray()
        for (screen in screens) {
            val sObj = JSONObject()
            sObj.put("id", screen.id)
            sObj.put("name", screen.name)
            val compsArr = JSONArray()
            for (c in screen.components) {
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("type", c.type)
                cObj.put("name", c.name)
                cObj.put("x", c.x)
                cObj.put("y", c.y)
                cObj.put("width", c.width)
                cObj.put("height", c.height)
                cObj.put("text", c.text)
                cObj.put("fontSize", c.fontSize)
                cObj.put("fontWeight", c.fontWeight)
                cObj.put("textColor", c.textColor)
                cObj.put("fillColor", c.fillColor)
                cObj.put("borderColor", c.borderColor)
                cObj.put("borderWidth", c.borderWidth)
                cObj.put("cornerRadius", c.cornerRadius)
                cObj.put("opacity", c.opacity)
                cObj.put("isVisible", c.isVisible)
                cObj.put("zIndex", c.zIndex)
                compsArr.put(cObj)
            }
            sObj.put("components", compsArr)
            screensArr.put(sObj)
        }
        root.put("screens", screensArr)
        return root
    }

    fun exportToAndroidXml(screen: StudioScreen): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<androidx.constraintlayout.widget.ConstraintLayout\n")
        sb.append("    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
        sb.append("    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n")
        sb.append("    android:layout_width=\"match_parent\"\n")
        sb.append("    android:layout_height=\"match_parent\"\n")
        sb.append("    android:background=\"#121212\">\n\n")

        for (c in screen.components) {
            if (!c.isVisible) continue
            when (c.type) {
                "Text" -> {
                    sb.append("    <TextView\n")
                    sb.append("        android:id=\"@+id/${c.id}\"\n")
                    sb.append("        android:layout_width=\"${c.width.toInt()}dp\"\n")
                    sb.append("        android:layout_height=\"${c.height.toInt()}dp\"\n")
                    sb.append("        android:text=\"${c.text}\"\n")
                    sb.append("        android:textColor=\"${c.textColor}\"\n")
                    sb.append("        android:textSize=\"${c.fontSize.toInt()}sp\"\n")
                    sb.append("        android:layout_marginStart=\"${c.x.toInt()}dp\"\n")
                    sb.append("        android:layout_marginTop=\"${c.y.toInt()}dp\"\n")
                    sb.append("        app:layout_constraintStart_toStartOf=\"parent\"\n")
                    sb.append("        app:layout_constraintTop_toTopOf=\"parent\" />\n\n")
                }
                "Button" -> {
                    sb.append("    <com.google.android.material.button.MaterialButton\n")
                    sb.append("        android:id=\"@+id/${c.id}\"\n")
                    sb.append("        android:layout_width=\"${c.width.toInt()}dp\"\n")
                    sb.append("        android:layout_height=\"${c.height.toInt()}dp\"\n")
                    sb.append("        android:text=\"${c.text}\"\n")
                    sb.append("        app:cornerRadius=\"${c.cornerRadius.toInt()}dp\"\n")
                    sb.append("        android:layout_marginStart=\"${c.x.toInt()}dp\"\n")
                    sb.append("        android:layout_marginTop=\"${c.y.toInt()}dp\"\n")
                    sb.append("        app:layout_constraintStart_toStartOf=\"parent\"\n")
                    sb.append("        app:layout_constraintTop_toTopOf=\"parent\" />\n\n")
                }
                "Input" -> {
                    sb.append("    <EditText\n")
                    sb.append("        android:id=\"@+id/${c.id}\"\n")
                    sb.append("        android:layout_width=\"${c.width.toInt()}dp\"\n")
                    sb.append("        android:layout_height=\"${c.height.toInt()}dp\"\n")
                    sb.append("        android:hint=\"${c.text}\"\n")
                    sb.append("        android:textColor=\"${c.textColor}\"\n")
                    sb.append("        android:layout_marginStart=\"${c.x.toInt()}dp\"\n")
                    sb.append("        android:layout_marginTop=\"${c.y.toInt()}dp\"\n")
                    sb.append("        app:layout_constraintStart_toStartOf=\"parent\"\n")
                    sb.append("        app:layout_constraintTop_toTopOf=\"parent\" />\n\n")
                }
                else -> {
                    sb.append("    <View\n")
                    sb.append("        android:id=\"@+id/${c.id}\"\n")
                    sb.append("        android:layout_width=\"${c.width.toInt()}dp\"\n")
                    sb.append("        android:layout_height=\"${c.height.toInt()}dp\"\n")
                    sb.append("        android:background=\"${c.fillColor}\"\n")
                    sb.append("        android:layout_marginStart=\"${c.x.toInt()}dp\"\n")
                    sb.append("        android:layout_marginTop=\"${c.y.toInt()}dp\"\n")
                    sb.append("        app:layout_constraintStart_toStartOf=\"parent\"\n")
                    sb.append("        app:layout_constraintTop_toTopOf=\"parent\" />\n\n")
                }
            }
        }
        sb.append("</androidx.constraintlayout.widget.ConstraintLayout>\n")
        return sb.toString()
    }
}
