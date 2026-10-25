package com.zipbug.base.creator

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class UiComponent(
    val id: String,
    val name: String,
    val category: String, // "Toggle Switch", "Glass Card", "Home Icon", "Neon Button", "Logo", "3D Background", "Button", "Background"
    val author: String,
    val sourceUrl: String,
    val license: String, // "MIT" or "Apache 2.0"
    val description: String,
    val htmlContent: String,
    val cssContent: String,
    val nativeXmlContent: String,
    val nativeResourceName: String,
    val previewColorHex: String,
    val textColorHex: String,
    val isDefault: Boolean = false,
    val isReplacement: Boolean = false
) {
    val xmlContent: String get() = nativeXmlContent
    val xmlDrawableName: String get() = nativeResourceName
}

data class PanelModel(
    val id: String = "pnl1",
    val name: String = "Panel 1",
    var switchId: String = "sweet-cow-44",
    var cardId: String = "lovely-bear-22",
    var homeIconId: String = "ic:baseline-home",
    var buttonId: String = "funny-dog-69",
    var logoId: String = "horrible-hound-96",
    var bgEffectId: String = "black-sheep-17",
    var cardHeading: String = "My App 23",
    var cardDescription: String = "Built with Zip Studio & Uiverse Components",
    var showOutline: Boolean = true,
    var ambientGlowEnabled: Boolean = true,
    var reducedMotion: Boolean = false
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("switchId", switchId)
            put("cardId", cardId)
            put("homeIconId", homeIconId)
            put("buttonId", buttonId)
            put("logoId", logoId)
            put("bgEffectId", bgEffectId)
            put("cardHeading", cardHeading)
            put("cardDescription", cardDescription)
            put("showOutline", showOutline)
            put("ambientGlowEnabled", ambientGlowEnabled)
            put("reducedMotion", reducedMotion)
        }.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): PanelModel {
            return runCatching {
                val obj = JSONObject(jsonStr)
                PanelModel(
                    id = obj.optString("id", "pnl1"),
                    name = obj.optString("name", "Panel 1"),
                    switchId = obj.optString("switchId", "sweet-cow-44"),
                    cardId = obj.optString("cardId", "lovely-bear-22"),
                    homeIconId = obj.optString("homeIconId", "ic:baseline-home"),
                    buttonId = obj.optString("buttonId", "funny-dog-69"),
                    logoId = obj.optString("logoId", "horrible-hound-96"),
                    bgEffectId = obj.optString("bgEffectId", "black-sheep-17"),
                    cardHeading = obj.optString("cardHeading", "My App 23"),
                    cardDescription = obj.optString("cardDescription", "Built with Zip Studio & Uiverse Components"),
                    showOutline = obj.optBoolean("showOutline", true),
                    ambientGlowEnabled = obj.optBoolean("ambientGlowEnabled", true),
                    reducedMotion = obj.optBoolean("reducedMotion", false)
                )
            }.getOrDefault(PanelModel())
        }
    }
}

object UiComponentGallery {

    val curatedComponents: List<UiComponent> = listOf(
        // 1. PRIMARY REFERENCE: Black Sheep 17 (3D Background / Loader)
        UiComponent(
            id = "black-sheep-17",
            name = "Black Sheep 3D Cubes",
            category = "3D Background",
            author = "Admin12121",
            sourceUrl = "https://uiverse.io/Admin12121/black-sheep-17",
            license = "MIT",
            description = "Blue isometric 3D cubes with luminous accents and rotating matrix perspective.",
            htmlContent = """<div class="loader">
  <div class="box box0"><div></div></div>
  <div class="box box1"><div></div></div>
  <div class="box box2"><div></div></div>
  <div class="box box3"><div></div></div>
  <div class="box box4"><div></div></div>
  <div class="box box5"><div></div></div>
  <div class="box box6"><div></div></div>
  <div class="box box7"><div></div></div>
  <div class="ground"><div></div></div>
</div>""".trimIndent(),
            cssContent = """/* From Uiverse.io by Admin12121 - MIT License */
.loader {
  --size: 24px;
  --duration: 2s;
  width: 96px;
  height: 96px;
  position: relative;
  transform-style: preserve-3d;
  transform: rotateX(60deg) rotateZ(45deg);
}
.loader .box {
  position: absolute;
  width: var(--size);
  height: var(--size);
  background: #3b82f6;
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.6);
  border: 1px solid #60a5fa;
}""".trimIndent(),
            nativeXmlContent = """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:startColor="#0D1B2A"
        android:endColor="#0A0A0C"
        android:angle="135" />
    <stroke android:width="1.5dp" android:color="#2563EB" />
</shape>""".trimIndent(),
            nativeResourceName = "bg_black_sheep_cubes",
            previewColorHex = "#1D4ED8",
            textColorHex = "#93C5FD",
            isDefault = true
        ),

        // 2. TOGGLE SWITCH: Sweet Cow (Default)
        UiComponent(
            id = "sweet-cow-44",
            name = "Sweet Cow Liquid Switch",
            category = "Toggle Switch",
            author = "vikramsinghnegi",
            sourceUrl = "https://uiverse.io/vikramsinghnegi/sweet-cow-44",
            license = "MIT",
            description = "Dynamic liquid toggle switch with organic bubble effect and luminous glow.",
            htmlContent = """<div class="checkbox-wrap">
  <input name="slide" id="slide" type="checkbox" checked />
  <label for="slide">
    <div id="pinkliquid">
      <div id="shine-one" class="shine"></div>
      <div id="shine-two" class="shine"></div>
    </div>
    <div id="circle"></div>
  </label>
</div>""".trimIndent(),
            cssContent = """/* From Uiverse.io by vikramsinghnegi - MIT License */
.checkbox-wrap {
  width: 76px;
  height: 38px;
  position: relative;
}
.checkbox-wrap input { display: none; }
.checkbox-wrap label {
  display: block;
  width: 100%;
  height: 100%;
  background: #18181b;
  border-radius: 20px;
  border: 2px solid #ec4899;
  cursor: pointer;
  position: relative;
  box-shadow: 0 0 10px rgba(236, 72, 153, 0.4);
}
.checkbox-wrap #circle {
  width: 26px;
  height: 26px;
  background: #ffffff;
  border-radius: 50%;
  position: absolute;
  top: 4px;
  left: 6px;
  transition: transform 0.3s ease;
}
.checkbox-wrap input:checked + label #circle {
  transform: translateX(36px);
  background: #f472b6;
}""".trimIndent(),
            nativeXmlContent = """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_checked="true">
        <shape android:shape="rectangle">
            <solid android:color="#EC4899" />
            <corners android:radius="14dp" />
            <stroke android:width="1.5dp" android:color="#F472B6" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#27272A" />
            <corners android:radius="14dp" />
            <stroke android:width="1.5dp" android:color="#3F3F46" />
        </shape>
    </item>
</selector>""".trimIndent(),
            nativeResourceName = "bg_switch_track",
            previewColorHex = "#EC4899",
            textColorHex = "#FFFFFF",
            isDefault = true
        ),

        // 3. TOGGLE SWITCH: Terrible Zebra (Alternative)
        UiComponent(
            id = "terrible-zebra-56",
            name = "Terrible Zebra Clean Switch",
            category = "Toggle Switch",
            author = "Creatlydev",
            sourceUrl = "https://uiverse.io/Creatlydev/terrible-zebra-56",
            license = "MIT",
            description = "Clean modern toggle switch with ON/OFF indicators and tactile feedback.",
            htmlContent = """<div class="toggle-container">
  <input hidden="" id="check" name="check" type="checkbox" checked>
  <label class="toggle" for="check">
    <div class="toggle__circle"></div>
  </label>
</div>""".trimIndent(),
            cssContent = """/* From Uiverse.io by Creatlydev - MIT License */
.toggle-container {
  display: flex;
  align-items: center;
}
.toggle {
  position: relative;
  display: inline-block;
  width: 60px;
  height: 32px;
  background-color: #27272a;
  border-radius: 30px;
  cursor: pointer;
  border: 1px solid #3f3f46;
  transition: all 0.3s ease;
}
.toggle__circle {
  position: absolute;
  top: 3px;
  left: 4px;
  width: 24px;
  height: 24px;
  background-color: #ffffff;
  border-radius: 50%;
  transition: all 0.3s ease;
}
#check:checked + .toggle {
  background-color: #10b981;
  border-color: #34d399;
}
#check:checked + .toggle .toggle__circle {
  transform: translateX(28px);
}""".trimIndent(),
            nativeXmlContent = """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_checked="true">
        <shape android:shape="rectangle">
            <solid android:color="#10B981" />
            <corners android:radius="14dp" />
            <stroke android:width="1.5dp" android:color="#34D399" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#27272A" />
            <corners android:radius="14dp" />
            <stroke android:width="1.5dp" android:color="#3F3F46" />
        </shape>
    </item>
</selector>""".trimIndent(),
            nativeResourceName = "bg_switch_track_zebra",
            previewColorHex = "#10B981",
            textColorHex = "#FFFFFF"
        ),

        // 4. GLASS CARD: Lovely Bear (Default)
        UiComponent(
            id = "lovely-bear-22",
            name = "Lovely Bear Frosted Glass",
            category = "Glass Card",
            author = "Tsiangana",
            sourceUrl = "https://uiverse.io/Tsiangana/lovely-bear-22",
            license = "MIT",
            description = "Frosted glassmorphic card with backdrop blur and refined translucent borders.",
            htmlContent = """<div class="card lovely-bear-card">
  <div class="heading">My App 23</div>
  <p class="description">Built with Zip Studio & Uiverse Components</p>
  <div class="status-indicator">Ready</div>
</div>""".trimIndent(),
            cssContent = """/* From Uiverse.io by Tsiangana - MIT License */
.lovely-bear-card {
  width: 100%;
  padding: 20px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 16px;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
  transition: all 0.3s ease;
}
.lovely-bear-card:hover {
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 8px 32px 0 rgba(59, 130, 246, 0.2);
}""".trimIndent(),
            nativeXmlContent = """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#1A1D24" />
    <corners android:radius="16dp" />
    <stroke android:width="1.2dp" android:color="#3B82F6" />
</shape>""".trimIndent(),
            nativeResourceName = "bg_glass_card",
            previewColorHex = "#1E293B",
            textColorHex = "#F8FAFC",
            isDefault = true
        ),

        // 5. GLASS CARD: Curvy Gecko (Alternative)
        UiComponent(
            id = "curvy-gecko-61",
            name = "Curvy Gecko Cyber Glass",
            category = "Glass Card",
            author = "StealthWorm",
            sourceUrl = "https://uiverse.io/StealthWorm/curvy-gecko-61",
            license = "MIT",
            description = "Luminous cyber card container with neon cyan gradient border.",
            htmlContent = """<div class="card-border curvy-gecko-card">
  <div class="card-bg">
    <div class="card-content">
      <div class="heading">My App 23</div>
      <p class="description">Cybernetic Glass Interface</p>
    </div>
  </div>
</div>""".trimIndent(),
            cssContent = """/* From Uiverse.io by StealthWorm - MIT License */
.curvy-gecko-card {
  width: 100%;
  background: linear-gradient(135deg, #00f2fe 0%, #4facfe 100%);
  padding: 1.5px;
  border-radius: 18px;
  box-shadow: 0 0 20px rgba(79, 172, 254, 0.3);
}
.curvy-gecko-card .card-bg {
  background: #0d1117;
  border-radius: 17px;
  padding: 18px;
}""".trimIndent(),
            nativeXmlContent = """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0F172A" />
    <corners android:radius="18dp" />
    <stroke android:width="2dp" android:color="#38BDF8" />
</shape>""".trimIndent(),
            nativeResourceName = "bg_curvy_gecko_card",
            previewColorHex = "#0F172A",
            textColorHex = "#38BDF8"
        ),

        // 6. HOME ICON: Iconify ic:baseline-home (Default)
        UiComponent(
            id = "ic:baseline-home",
            name = "Baseline Home Icon",
            category = "Home Icon",
            author = "Google / Iconify",
            sourceUrl = "https://icon-sets.iconify.design/ic/baseline-home/",
            license = "Apache 2.0 / MIT",
            description = "Clean vector home glyph for instant state reset and primary navigation.",
            htmlContent = """<svg class="home-icon" xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
  <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/>
</svg>""".trimIndent(),
            cssContent = """/* From Iconify ic:baseline-home - Apache 2.0 / MIT */
.home-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #1e293b;
  border: 1px solid #334155;
  color: #38bdf8;
  cursor: pointer;
  transition: all 0.2s ease;
}
.home-icon-btn:hover {
  background: #0284c7;
  color: #ffffff;
  transform: scale(1.08);
}""".trimIndent(),
            nativeXmlContent = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <path
      android:fillColor="#FFFFFFFF"
      android:pathData="M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z"/>
</vector>""".trimIndent(),
            nativeResourceName = "ic_baseline_home",
            previewColorHex = "#0284C7",
            textColorHex = "#FFFFFF",
            isDefault = true
        ),

        // 7. NEON BUTTON: Funny Dog (Default)
        UiComponent(
            id = "funny-dog-69",
            name = "Funny Dog Neon Glow Button",
            category = "Neon Button",
            author = "cssbuttons-io",
            sourceUrl = "https://uiverse.io/cssbuttons-io/funny-dog-69",
            license = "MIT",
            description = "Cyberpunk neon button with double glow drop-shadow, active bounce, and luminous border.",
            htmlContent = """<button class="neon-btn-funny-dog" id="btnNeonAction">Click Me</button>""".trimIndent(),
            cssContent = """/* From Uiverse.io by cssbuttons-io - MIT License */
.neon-btn-funny-dog {
  --glow-color: rgb(217, 176, 255);
  --glow-spread-color: rgba(191, 123, 255, 0.781);
  --enhanced-glow-color: rgb(231, 206, 255);
  --btn-color: rgb(100, 61, 136);
  border: 0.2em solid var(--glow-color);
  padding: 0.7em 2em;
  color: var(--glow-color);
  font-size: 14px;
  font-weight: bold;
  background-color: var(--btn-color);
  border-radius: 1em;
  outline: none;
  box-shadow: 0 0 1em 0.25em var(--glow-color),
              0 0 3em 1em var(--glow-spread-color),
              inset 0 0 0.5em 0.25em var(--glow-color);
  text-shadow: 0 0 0.5em var(--glow-color);
  cursor: pointer;
  transition: all 0.25s ease;
}
.neon-btn-funny-dog:active {
  box-shadow: 0 0 0.6em 0.25em var(--glow-color),
              0 0 2em 1.5em var(--glow-spread-color),
              inset 0 0 0.5em 0.25em var(--glow-color);
  transform: scale(0.95);
}""".trimIndent(),
            nativeXmlContent = """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="#4A148C" />
            <corners android:radius="14dp" />
            <stroke android:width="2.5dp" android:color="#E1BEE7" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <gradient
                android:startColor="#6A1B9A"
                android:endColor="#4A148C"
                android:angle="45" />
            <corners android:radius="14dp" />
            <stroke android:width="2dp" android:color="#D9B0FF" />
            <padding
                android:left="20dp"
                android:top="10dp"
                android:right="20dp"
                android:bottom="10dp" />
        </shape>
    </item>
</selector>""".trimIndent(),
            nativeResourceName = "btn_neon_funny_dog",
            previewColorHex = "#6A1B9A",
            textColorHex = "#D9B0FF",
            isDefault = true
        ),

        // 8. LOGO: Horrible Hound (Default)
        UiComponent(
            id = "horrible-hound-96",
            name = "Horrible Hound Cube Logo",
            category = "Logo",
            author = "Admin12121",
            sourceUrl = "https://uiverse.io/Admin12121/horrible-hound-96",
            license = "MIT",
            description = "Isometric folding geometry logo with rotating geometric sides.",
            htmlContent = """<div class="con-logo-hound">
  <div class="side left"></div>
  <div class="side right"></div>
  <div class="side rightAbove"></div>
  <div class="side bottom"></div>
</div>""".trimIndent(),
            cssContent = """/* From Uiverse.io by Admin12121 - MIT License */
.con-logo-hound {
  width: 40px;
  height: 40px;
  position: relative;
  display: inline-block;
}
.con-logo-hound .side {
  width: 14px;
  height: 14px;
  position: absolute;
  background: #3b82f6;
  border: 1px solid #60a5fa;
}
.con-logo-hound .left { top: 0; left: 0; }
.con-logo-hound .right { top: 0; right: 0; background: #2563eb; }
.con-logo-hound .rightAbove { bottom: 0; left: 0; background: #1d4ed8; }
.con-logo-hound .bottom { bottom: 0; right: 0; background: #1e40af; }""".trimIndent(),
            nativeXmlContent = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp"
    android:height="32dp"
    android:viewportWidth="32"
    android:viewportHeight="32">
  <path
      android:fillColor="#3B82F6"
      android:pathData="M16,3 L29,10.5 L16,18 L3,10.5 Z" />
  <path
      android:fillColor="#1D4ED8"
      android:pathData="M3,10.5 L16,18 L16,29 L3,21.5 Z" />
  <path
      android:fillColor="#2563EB"
      android:pathData="M16,18 L29,10.5 L29,21.5 L16,29 Z" />
</vector>""".trimIndent(),
            nativeResourceName = "logo_horrible_hound",
            previewColorHex = "#3B82F6",
            textColorHex = "#FFFFFF",
            isDefault = true
        ),

        // 9. LOGO: Selfish Starfish (Alternative)
        UiComponent(
            id = "selfish-starfish-81",
            name = "Selfish Starfish Flame Logo",
            category = "Logo",
            author = "Admin12121",
            sourceUrl = "https://uiverse.io/Admin12121/selfish-starfish-81",
            license = "MIT",
            description = "Luminous particle flame logo with radiant ambient gradient.",
            htmlContent = """<div class="fire-starfish-logo">
  <div class="main-fire"></div>
</div>""".trimIndent(),
            cssContent = """/* From Uiverse.io by Admin12121 - MIT License */
.fire-starfish-logo {
  width: 38px;
  height: 38px;
  background: radial-gradient(circle, #ff6d00 0%, #d50000 70%);
  border-radius: 50%;
  display: inline-block;
  box-shadow: 0 0 14px rgba(255, 109, 0, 0.7);
}""".trimIndent(),
            nativeXmlContent = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="32dp"
    android:height="32dp"
    android:viewportWidth="32"
    android:viewportHeight="32">
  <path
      android:fillColor="#FF6D00"
      android:pathData="M16,2 C16,2 18,8 21,11 C24,14 28,15 28,19 C28,25 22.5,29 16,29 C9.5,29 4,25 4,19 C4,15 8,14 11,11 C14,8 16,2 16,2 Z" />
  <path
      android:fillColor="#FFD600"
      android:pathData="M16,10 C16,10 17,14 19,16 C21,18 23,19 23,22 C23,26 19.5,28 16,28 C12.5,28 9,26 9,22 C9,19 11,18 13,16 C15,14 16,10 16,10 Z" />
</vector>""".trimIndent(),
            nativeResourceName = "logo_selfish_starfish",
            previewColorHex = "#FF6D00",
            textColorHex = "#FFD600"
        ),

        // 10. Button: Red Glow Pulse
        UiComponent(
            id = "btn_red_glow",
            name = "Red Glow Pulse",
            category = "Button",
            author = "Uiverse Community",
            sourceUrl = "https://uiverse.io/elements/red-glow-btn",
            license = "MIT",
            description = "Deep ruby red with active pressed state and smooth 16dp rounded corners.",
            htmlContent = """<button class="btn-red-glow">Click Me</button>""".trimIndent(),
            cssContent = """/* MIT License */
.btn-red-glow {
  background: linear-gradient(45deg, #e53935, #d32f2f);
  color: white;
  border: 2px solid #ff8a80;
  border-radius: 16px;
  padding: 10px 24px;
  font-weight: bold;
  cursor: pointer;
  box-shadow: 0 0 12px rgba(229, 57, 53, 0.5);
}""".trimIndent(),
            nativeXmlContent = """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="#B71C1C" />
            <corners android:radius="16dp" />
            <stroke android:width="2dp" android:color="#FF5252" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <gradient
                android:startColor="#E53935"
                android:endColor="#D32F2F"
                android:angle="45" />
            <corners android:radius="16dp" />
            <stroke android:width="2dp" android:color="#FF8A80" />
            <padding
                android:left="24dp"
                android:top="12dp"
                android:right="24dp"
                android:bottom="12dp" />
        </shape>
    </item>
</selector>""".trimIndent(),
            nativeResourceName = "btn_red_style",
            previewColorHex = "#E53935",
            textColorHex = "#FFFFFF"
        ),

        // 11. Background: Dark Charcoal Canvas
        UiComponent(
            id = "bg_charcoal_surface",
            name = "Dark Charcoal Canvas",
            category = "Background",
            author = "ZipBug Design",
            sourceUrl = "https://zipbug.dev/patterns/charcoal",
            license = "MIT",
            description = "Sophisticated near-black surface with subtle borders for OLED displays.",
            htmlContent = """<div class="charcoal-canvas"></div>""".trimIndent(),
            cssContent = """/* MIT License */
.charcoal-canvas {
  background: #121214;
  border: 1px solid #27272a;
}""".trimIndent(),
            nativeXmlContent = """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#121214" />
    <stroke android:width="1dp" android:color="#27272A" />
</shape>""".trimIndent(),
            nativeResourceName = "bg_charcoal_surface",
            previewColorHex = "#121214",
            textColorHex = "#A1A1AA"
        )
    )

    fun search(query: String, category: String? = null): List<UiComponent> {
        val q = query.trim().lowercase()
        return curatedComponents.filter { item ->
            val matchCategory = category == null || category == "All" || item.category.equals(category, ignoreCase = true)
            val matchQuery = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.description.lowercase().contains(q) ||
                item.author.lowercase().contains(q) ||
                item.category.lowercase().contains(q) ||
                (q.contains("switch") && item.category == "Toggle Switch") ||
                (q.contains("card") && item.category == "Glass Card") ||
                (q.contains("home") && item.category == "Home Icon") ||
                (q.contains("neon") && item.category == "Neon Button") ||
                (q.contains("logo") && item.category == "Logo") ||
                (q.contains("blue") && item.id.contains("black-sheep"))
            matchCategory && matchQuery
        }
    }

    fun getComponentById(id: String): UiComponent? {
        return curatedComponents.firstOrNull { it.id == id }
    }

    fun loadPanel(projectDir: File): PanelModel {
        val configFile = File(projectDir, "panel_pnl1.json")
        if (configFile.exists()) {
            val content = configFile.readText(Charsets.UTF_8)
            return PanelModel.fromJson(content)
        }
        return PanelModel()
    }

    fun savePanel(projectDir: File, panel: PanelModel): Boolean {
        return runCatching {
            val configFile = File(projectDir, "panel_pnl1.json")
            configFile.writeText(panel.toJson(), Charsets.UTF_8)
            applyPanelToNativeProject(projectDir, panel)
            true
        }.getOrDefault(false)
    }

    fun applyPanelToNativeProject(projectDir: File, panel: PanelModel): Boolean {
        return runCatching {
            val resDir = File(projectDir, "app/src/main/res").apply { mkdirs() }
            val drawableDir = File(resDir, "drawable").apply { mkdirs() }
            val animDir = File(resDir, "anim").apply { mkdirs() }
            val layoutDir = File(resDir, "layout").apply { mkdirs() }
            val valuesDir = File(resDir, "values").apply { mkdirs() }

            // 1. Write Native Drawables
            val componentsToSync = listOfNotNull(
                getComponentById(panel.switchId),
                getComponentById(panel.cardId),
                getComponentById(panel.homeIconId),
                getComponentById(panel.buttonId),
                getComponentById(panel.logoId),
                getComponentById(panel.bgEffectId)
            )
            for (comp in componentsToSync) {
                if (comp.nativeXmlContent.isNotBlank() && comp.nativeResourceName.isNotBlank()) {
                    File(drawableDir, "${comp.nativeResourceName}.xml").writeText(comp.nativeXmlContent, Charsets.UTF_8)
                }
            }

            // 2. Write Animations
            File(animDir, "fade_in.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:interpolator="@android:anim/decelerate_interpolator"
    android:fromAlpha="0.0"
    android:toAlpha="1.0"
    android:duration="400" />""".trimIndent(), Charsets.UTF_8)

            File(animDir, "bounce.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <scale
        android:interpolator="@android:anim/bounce_interpolator"
        android:fromXScale="0.9"
        android:toXScale="1.0"
        android:fromYScale="0.9"
        android:toYScale="1.0"
        android:pivotX="50%"
        android:pivotY="50%"
        android:duration="350" />
</set>""".trimIndent(), Charsets.UTF_8)

            // 3. Write activity_main.xml with pnl1 layout
            val logoRes = getComponentById(panel.logoId)?.nativeResourceName ?: "logo_horrible_hound"
            val buttonRes = getComponentById(panel.buttonId)?.nativeResourceName ?: "btn_neon_funny_dog"
            val cardBgRes = getComponentById(panel.cardId)?.nativeResourceName ?: "bg_glass_card"
            val containerBg = if (panel.showOutline) "@drawable/bg_panel_outline" else "@drawable/bg_panel_no_outline"

            val layoutXml = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#0A0A0C"
    android:gravity="center"
    android:orientation="vertical"
    android:padding="16dp">

    <!-- pnl1 / Panel 1 Assembly Container -->
    <LinearLayout
        android:id="@+id/pnl1Container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="$containerBg"
        android:orientation="vertical"
        android:padding="20dp">

        <!-- TOP: Logo + Title -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageView
                android:id="@+id/ivLogo"
                android:layout_width="36dp"
                android:layout_height="36dp"
                android:contentDescription="App Logo"
                android:src="@drawable/$logoRes" />

            <TextView
                android:id="@+id/tvAppTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:text="${panel.cardHeading}"
                android:textColor="#FFFFFF"
                android:textSize="18sp"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- MIDDLE: Glass Card with Backdrop Effect -->
        <LinearLayout
            android:id="@+id/cardGlass"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:background="@drawable/$cardBgRes"
            android:orientation="vertical"
            android:padding="16dp">

            <TextView
                android:id="@+id/tvCardHeading"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="${panel.cardHeading}"
                android:textColor="#FFFFFF"
                android:textSize="15sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tvCardDesc"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:text="${panel.cardDescription}"
                android:textColor="#94A3B8"
                android:textSize="12sp" />

            <TextView
                android:id="@+id/tvCardStatus"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:background="#27272A"
                android:paddingHorizontal="10dp"
                android:paddingVertical="4dp"
                android:text="Ready"
                android:textColor="#38BDF8"
                android:textSize="11sp"
                android:textStyle="bold" />
        </LinearLayout>

        <!-- BOTTOM: Toggle Switch + Home Icon + Neon Button -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <androidx.appcompat.widget.SwitchCompat
                android:id="@+id/switchAmbientGlow"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:checked="${panel.ambientGlowEnabled}"
                android:thumb="@drawable/bg_switch_thumb"
                android:track="@drawable/bg_switch_track" />

            <View
                android:layout_width="0dp"
                android:layout_height="1dp"
                android:layout_weight="1" />

            <ImageView
                android:id="@+id/btnHome"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="#1E293B"
                android:clickable="true"
                android:contentDescription="Home Reset"
                android:focusable="true"
                android:padding="8dp"
                android:src="@drawable/ic_baseline_home" />

            <View
                android:layout_width="0dp"
                android:layout_height="1dp"
                android:layout_weight="1" />

            <Button
                android:id="@+id/btnNeonAction"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="@drawable/$buttonRes"
                android:paddingHorizontal="20dp"
                android:paddingVertical="10dp"
                android:text="Click Me"
                android:textColor="#D9B0FF"
                android:textSize="13sp"
                android:textStyle="bold" />
        </LinearLayout>

    </LinearLayout>

</LinearLayout>""".trimIndent()
            File(layoutDir, "activity_main.xml").writeText(layoutXml, Charsets.UTF_8)

            // 4. Update MainActivity.java to wire interactions
            val mainActivityFile = findMainActivityFile(projectDir)
            if (mainActivityFile != null) {
                val pkgName = extractPackageName(mainActivityFile)
                val javaCode = """package $pkgName;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final View pnl1Container = findViewById(R.id.pnl1Container);
        final View cardGlass = findViewById(R.id.cardGlass);
        final TextView tvCardStatus = findViewById(R.id.tvCardStatus);
        final Button btnNeonAction = findViewById(R.id.btnNeonAction);
        final ImageView btnHome = findViewById(R.id.btnHome);
        final SwitchCompat switchAmbientGlow = findViewById(R.id.switchAmbientGlow);

        // 1. Fade-in animation on glass card
        if (cardGlass != null) {
            Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
            cardGlass.startAnimation(fadeIn);
        }

        // 2. Neon Button: Bounce animation + Hello status + Toast
        if (btnNeonAction != null) {
            btnNeonAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Animation bounce = AnimationUtils.loadAnimation(MainActivity.this, R.anim.bounce);
                    btnNeonAction.startAnimation(bounce);
                    if (tvCardStatus != null) {
                        tvCardStatus.setText("Hello");
                    }
                    Toast.makeText(MainActivity.this, "Hello", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 3. Switch: ambient glow toggle
        if (switchAmbientGlow != null && pnl1Container != null) {
            switchAmbientGlow.setOnCheckedChangeListener((buttonView, isChecked) -> {
                pnl1Container.setBackgroundResource(
                    isChecked ? R.drawable.bg_panel_outline : R.drawable.bg_panel_no_outline
                );
            });
        }

        // 4. Home: reset preview
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                if (tvCardStatus != null) {
                    tvCardStatus.setText("Ready");
                }
                if (switchAmbientGlow != null) {
                    switchAmbientGlow.setChecked(true);
                }
                Toast.makeText(MainActivity.this, "Reset to Home", Toast.LENGTH_SHORT).show();
            });
        }
    }
}""".trimIndent()
                mainActivityFile.writeText(javaCode, Charsets.UTF_8)
            }
            true
        }.getOrDefault(false)
    }

    private fun findMainActivityFile(projectDir: File): File? {
        val srcDir = File(projectDir, "app/src/main/java")
        if (!srcDir.exists()) return null
        return srcDir.walkTopDown().firstOrNull { it.isFile && it.name == "MainActivity.java" }
    }

    private fun extractPackageName(mainActivityFile: File): String {
        val firstLine = mainActivityFile.readLines().firstOrNull { it.startsWith("package ") }
        return firstLine?.removePrefix("package ")?.removeSuffix(";")?.trim() ?: "com.example.myapp23"
    }

    fun generatePanelHtml(panel: PanelModel): String {
        val switchComp = getComponentById(panel.switchId)
        val cardComp = getComponentById(panel.cardId)
        val homeComp = getComponentById(panel.homeIconId)
        val buttonComp = getComponentById(panel.buttonId)
        val logoComp = getComponentById(panel.logoId)
        val bgComp = getComponentById(panel.bgEffectId)

        val outlineClass = if (panel.showOutline) "show-outline" else ""
        val glowClass = if (panel.ambientGlowEnabled) "ambient-glow" else ""

        return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${panel.cardHeading} - Panel Assembly</title>
  <link rel="stylesheet" href="pnl1.css">
</head>
<body class="pnl1-body">
  <div id="pnl1Container" class="pnl1-container $outlineClass $glowClass">
    <!-- TOP ROW: LOGO + APP TITLE -->
    <div class="pnl1-top">
      <div class="pnl1-logo-slot">
        ${logoComp?.htmlContent ?: ""}
      </div>
      <h2 class="pnl1-app-title">${panel.cardHeading}</h2>
    </div>

    <!-- MIDDLE: GLASS CARD -->
    <div id="pnl1Card" class="pnl1-middle fade-in">
      <div class="pnl1-card-content">
        <h3 id="pnl1Heading" class="pnl1-card-heading">${panel.cardHeading}</h3>
        <p id="pnl1Desc" class="pnl1-card-desc">${panel.cardDescription}</p>
        <div class="pnl1-status-pill">Status: <span id="pnl1Status">Ready</span></div>
        <div class="pnl1-bg-cube-visual">
          ${bgComp?.htmlContent ?: ""}
        </div>
      </div>
    </div>

    <!-- BOTTOM ROW: SWITCH + HOME + NEON BUTTON -->
    <div class="pnl1-bottom">
      <div class="pnl1-switch-slot">
        ${switchComp?.htmlContent ?: ""}
      </div>
      <div class="pnl1-home-slot">
        <button id="btnHomeReset" class="home-icon-btn" title="Home Reset">
          ${homeComp?.htmlContent ?: ""}
        </button>
      </div>
      <div class="pnl1-button-slot">
        ${buttonComp?.htmlContent ?: ""}
      </div>
    </div>
  </div>

  <script src="pnl1.js"></script>
</body>
</html>""".trimIndent()
    }

    fun generatePanelCss(panel: PanelModel): String {
        val switchComp = getComponentById(panel.switchId)
        val cardComp = getComponentById(panel.cardId)
        val homeComp = getComponentById(panel.homeIconId)
        val buttonComp = getComponentById(panel.buttonId)
        val logoComp = getComponentById(panel.logoId)
        val bgComp = getComponentById(panel.bgEffectId)

        return """/* ZIP STUDIO — pnl1 Scoped Assembly Styles */
/* Uiverse References:
   Primary: ${bgComp?.sourceUrl ?: "https://uiverse.io"}
   Switch: ${switchComp?.sourceUrl ?: ""}
   Card: ${cardComp?.sourceUrl ?: ""}
   Button: ${buttonComp?.sourceUrl ?: ""}
   Logo: ${logoComp?.sourceUrl ?: ""}
*/

* { box-sizing: border-box; margin: 0; padding: 0; }

body.pnl1-body {
  background: #0a0a0c;
  color: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 16px;
}

.pnl1-container {
  width: 100%;
  max-width: 420px;
  background: #0d1117;
  border-radius: 20px;
  padding: 22px;
  position: relative;
  transition: all 0.3s ease;
  overflow: hidden;
}

.pnl1-container.show-outline {
  border: 2px dashed #3b82f6;
}

.pnl1-container.ambient-glow {
  box-shadow: 0 0 35px rgba(59, 130, 246, 0.4), inset 0 0 20px rgba(59, 130, 246, 0.15);
}

/* TOP */
.pnl1-top {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}
.pnl1-app-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: #ffffff;
}

/* MIDDLE CARD */
.pnl1-middle {
  margin-bottom: 22px;
  position: relative;
}
.pnl1-card-heading {
  font-size: 1.05rem;
  font-weight: 600;
  margin-bottom: 4px;
}
.pnl1-card-desc {
  font-size: 0.85rem;
  color: #94a3b8;
  margin-bottom: 12px;
}
.pnl1-status-pill {
  display: inline-block;
  background: #27272a;
  color: #38bdf8;
  font-size: 0.75rem;
  font-weight: bold;
  padding: 4px 10px;
  border-radius: 9999px;
  margin-bottom: 12px;
}
.pnl1-bg-cube-visual {
  display: flex;
  justify-content: center;
  padding: 10px 0;
}

/* BOTTOM ROW */
.pnl1-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

/* Animations */
.fade-in {
  animation: fadeIn 0.4s ease forwards;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.bounce {
  animation: bounceAnim 0.35s ease;
}
@keyframes bounceAnim {
  0% { transform: scale(1); }
  40% { transform: scale(0.92); }
  75% { transform: scale(1.06); }
  100% { transform: scale(1); }
}

@media (prefers-reduced-motion: reduce) {
  .fade-in, .bounce, .loader, .con-logo-hound {
    animation: none !important;
    transition: none !important;
  }
}

/* Individual Component Styles */
${logoComp?.cssContent ?: ""}
${bgComp?.cssContent ?: ""}
${cardComp?.cssContent ?: ""}
${switchComp?.cssContent ?: ""}
${homeComp?.cssContent ?: ""}
${buttonComp?.cssContent ?: ""}
""".trimIndent()
    }

    fun generatePanelJs(panel: PanelModel): String {
        return """// ZIP STUDIO — pnl1 Interactive Runtime
document.addEventListener("DOMContentLoaded", function() {
  const container = document.getElementById("pnl1Container");
  const card = document.getElementById("pnl1Card");
  const statusSpan = document.getElementById("pnl1Status");
  const btnAction = document.getElementById("btnNeonAction");
  const btnHome = document.getElementById("btnHomeReset");
  const toggleInputs = document.querySelectorAll('.checkbox-wrap input, .toggle-container input');

  // 1. Neon Button click -> Bounce animation + Hello message
  if (btnAction) {
    btnAction.addEventListener("click", function() {
      btnAction.classList.remove("bounce");
      void btnAction.offsetWidth; // re-flow
      btnAction.classList.add("bounce");
      if (statusSpan) {
        statusSpan.innerText = "Hello";
      }
    });
  }

  // 2. Toggle Switch -> ambient glow
  toggleInputs.forEach(input => {
    input.addEventListener("change", function(e) {
      if (e.target.checked) {
        container.classList.add("ambient-glow");
      } else {
        container.classList.remove("ambient-glow");
      }
    });
  });

  // 3. Home Reset -> restores initial status and glow
  if (btnHome) {
    btnHome.addEventListener("click", function() {
      if (statusSpan) statusSpan.innerText = "Ready";
      container.classList.add("ambient-glow");
      toggleInputs.forEach(input => input.checked = true);
      if (card) {
        card.classList.remove("fade-in");
        void card.offsetWidth;
        card.classList.add("fade-in");
      }
    });
  }
});
""".trimIndent()
    }

    fun exportUiZip(projectDir: File, panel: PanelModel, outZipFile: File): Boolean {
        return runCatching {
            outZipFile.parentFile?.mkdirs()
            ZipOutputStream(FileOutputStream(outZipFile)).use { zip ->
                fun addEntry(name: String, content: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }

                // 1. pnl1.html
                addEntry("pnl1.html", generatePanelHtml(panel).toByteArray(Charsets.UTF_8))

                // 2. pnl1.css
                addEntry("pnl1.css", generatePanelCss(panel).toByteArray(Charsets.UTF_8))

                // 3. pnl1.js
                addEntry("pnl1.js", generatePanelJs(panel).toByteArray(Charsets.UTF_8))

                // 4. METADATA.json
                val metadataJson = JSONObject().apply {
                    put("appName", panel.cardHeading)
                    put("panelId", panel.id)
                    put("components", JSONObject().apply {
                        put("switch", panel.switchId)
                        put("card", panel.cardId)
                        put("home", panel.homeIconId)
                        put("button", panel.buttonId)
                        put("logo", panel.logoId)
                        put("background", panel.bgEffectId)
                    })
                    put("licenses", JSONObject().apply {
                        put("black-sheep-17", "MIT - Admin12121 (https://uiverse.io/Admin12121/black-sheep-17)")
                        put("sweet-cow-44", "MIT - vikramsinghnegi (https://uiverse.io/vikramsinghnegi/sweet-cow-44)")
                        put("terrible-zebra-56", "MIT - Creatlydev (https://uiverse.io/Creatlydev/terrible-zebra-56)")
                        put("lovely-bear-22", "MIT - Tsiangana (https://uiverse.io/Tsiangana/lovely-bear-22)")
                        put("curvy-gecko-61", "MIT - StealthWorm (https://uiverse.io/StealthWorm/curvy-gecko-61)")
                        put("funny-dog-69", "MIT - cssbuttons-io (https://uiverse.io/cssbuttons-io/funny-dog-69)")
                        put("horrible-hound-96", "MIT - Admin12121 (https://uiverse.io/Admin12121/horrible-hound-96)")
                        put("selfish-starfish-81", "MIT - Admin12121 (https://uiverse.io/Admin12121/selfish-starfish-81)")
                        put("ic:baseline-home", "Apache 2.0 / MIT - Iconify / Google Material")
                    })
                }.toString(2)
                addEntry("METADATA.json", metadataJson.toByteArray(Charsets.UTF_8))

                // 5. README.md
                val readme = """# My App 23 — UI Component Bundle (pnl1 Assembly)

Generated by **Zip Studio** using Uiverse Community Components.

## Components Included:
- **Primary 3D Background**: Black Sheep 17 by Admin12121 (MIT)
- **Toggle Switch**: Sweet Cow 44 by vikramsinghnegi (MIT)
- **Glass Card**: Lovely Bear 22 by Tsiangana (MIT)
- **Home Icon**: ic:baseline-home by Iconify (Apache 2.0)
- **Neon Button**: Funny Dog 69 by cssbuttons-io (MIT)
- **Logo**: Horrible Hound 96 by Admin12121 (MIT)

## Usage:
Open `pnl1.html` in any web browser to view the interactive assembly.
For native Android integration, examine the `native/` folder inside this archive.
""".trimIndent()
                addEntry("README.md", readme.toByteArray(Charsets.UTF_8))

                // 6. Native source files if project exists
                val nativeResDir = File(projectDir, "app/src/main/res")
                if (nativeResDir.exists()) {
                    nativeResDir.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = f.relativeTo(nativeResDir).path.replace('\\', '/')
                        addEntry("native/res/$rel", f.readBytes())
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    fun applyStyleToProject(projectDir: File, component: UiComponent): Boolean {
        val panel = loadPanel(projectDir)
        when (component.category) {
            "Toggle Switch" -> panel.switchId = component.id
            "Glass Card" -> panel.cardId = component.id
            "Home Icon" -> panel.homeIconId = component.id
            "Neon Button", "Button" -> panel.buttonId = component.id
            "Logo" -> panel.logoId = component.id
            "3D Background", "Background" -> panel.bgEffectId = component.id
        }
        return savePanel(projectDir, panel)
    }
}
