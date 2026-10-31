package com.zipbug.base.termux

import android.content.Context
import java.io.File
import org.json.JSONObject

enum class DiagnosticStatus {
    UNKNOWN,
    CHECKING,
    PASS,
    FAIL
}

data class DiagnosticCheck(
    val id: String,
    val name: String,
    var status: DiagnosticStatus = DiagnosticStatus.UNKNOWN,
    var detail: String = "",
    val isMandatory: Boolean = true,
    val fixInstruction: String = ""
)

data class DiagnosticsReport(
    val checks: List<DiagnosticCheck>,
    val allMandatoryPassed: Boolean,
    val summary: String
)

object EngineDiagnostics {

    fun createInitialChecks(): List<DiagnosticCheck> = listOf(
        DiagnosticCheck(
            id = "termux_installed",
            name = "1. Termux Installed",
            fixInstruction = "Install Termux from F-Droid or GitHub release (Google Play version is deprecated)."
        ),
        DiagnosticCheck(
            id = "permission_run_command",
            name = "2. RUN_COMMAND Permission",
            fixInstruction = "Grant 'Run commands in Termux environment' in Android Settings → Apps → Zip_Bug Studio → Permissions."
        ),
        DiagnosticCheck(
            id = "allow_external_apps",
            name = "3. allow-external-apps=true",
            fixInstruction = "In Termux run:\nmkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings"
        ),
        DiagnosticCheck(
            id = "java_available",
            name = "4. Java Available",
            fixInstruction = "In Termux run: pkg install openjdk-17"
        ),
        DiagnosticCheck(
            id = "java_version",
            name = "5. Java Version (17+)",
            fixInstruction = "Java 17 (openjdk-17) is required for Android SDK 34 Gradle builds."
        ),
        DiagnosticCheck(
            id = "gradle_available",
            name = "6. Gradle Available",
            fixInstruction = "In Termux run: pkg install gradle"
        ),
        DiagnosticCheck(
            id = "gradle_version",
            name = "7. Gradle Version (8.9+)",
            fixInstruction = "Gradle 8.9+ is expected for Gradle wrapper & build scripts."
        ),
        DiagnosticCheck(
            id = "python_available",
            name = "8. Python Available",
            fixInstruction = "In Termux run: pkg install python"
        ),
        DiagnosticCheck(
            id = "aapt2_available",
            name = "9. aapt2 Native Binary",
            fixInstruction = "In Termux run: pkg install aapt2\nAnd ensure ~/.gradle/gradle.properties has android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2"
        ),
        DiagnosticCheck(
            id = "sdk34_available",
            name = "10. Android SDK 34",
            fixInstruction = "Set export ANDROID_HOME=\$HOME/android-sdk in ~/.bashrc and ensure platforms/android-34/android.jar exists."
        ),
        DiagnosticCheck(
            id = "ffmpeg_available",
            name = "11. ffmpeg Available",
            isMandatory = false,
            fixInstruction = "In Termux run: pkg install ffmpeg"
        ),
        DiagnosticCheck(
            id = "yt_dlp_available",
            name = "12. yt-dlp Available",
            isMandatory = false,
            fixInstruction = "In Termux run: pip install --upgrade yt-dlp"
        ),
        DiagnosticCheck(
            id = "edge_tts_available",
            name = "13. edge-tts Available",
            isMandatory = false,
            fixInstruction = "In Termux run: pip install --upgrade edge-tts"
        ),
        DiagnosticCheck(
            id = "project_root_exists",
            name = "14. Project Root Exists",
            fixInstruction = "Configure or verify projectRoot in Settings (default: \$HOME/OpenDots/Zip_Bug_Antigravity)."
        ),
        DiagnosticCheck(
            id = "output_dir_writable",
            name = "15. Output Directory Writable",
            fixInstruction = "Ensure storage permission is granted (in Termux run: termux-setup-storage)."
        )
    )

    fun evaluateLocalChecks(context: Context, checks: List<DiagnosticCheck>) {
        val checkMap = checks.associateBy { it.id }

        // 1. Termux Installed
        val installed = TermuxBridge.isTermuxInstalled(context)
        checkMap["termux_installed"]?.apply {
            status = if (installed) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
            detail = if (installed) "Package com.termux detected" else "Package com.termux not installed"
        }

        // 2. RUN_COMMAND Permission
        val hasPerm = TermuxBridge.hasPermission(context)
        checkMap["permission_run_command"]?.apply {
            status = if (hasPerm) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
            detail = if (hasPerm) "Permission GRANTED" else "Permission NOT GRANTED"
        }

        // 14. Project Root Exists
        val prefs = context.getSharedPreferences("zipbug.settings", Context.MODE_PRIVATE)
        val home = prefs.getString("termuxHome", "/data/data/com.termux/files/home")!!
        val projectRoot = prefs.getString("projectRoot", "$home/OpenDots/Zip_Bug_Antigravity")!!
        checkMap["project_root_exists"]?.apply {
            val rootFile = File(projectRoot)
            // If readable or exists
            val exists = rootFile.exists()
            status = if (exists) DiagnosticStatus.PASS else DiagnosticStatus.UNKNOWN
            detail = if (exists) "Found: $projectRoot" else "Configured: $projectRoot (verify inside Termux)"
        }

        // 15. Output Directory Writable
        val exportPath = prefs.getString("artifactExportPath", "/storage/emulated/0/Download/Zip_Bug-debug.apk")!!
        checkMap["output_dir_writable"]?.apply {
            val exportDir = File(exportPath).parentFile
            val writable = exportDir != null && exportDir.exists() && exportDir.canWrite()
            status = if (writable) DiagnosticStatus.PASS else DiagnosticStatus.UNKNOWN
            detail = if (writable) "Writable: ${exportDir?.absolutePath}" else "Target: $exportPath"
        }
    }

    fun buildDiagnosticScript(): String {
        return """
import json, os, shutil, sys

sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
java_path = shutil.which("java") or ""
gradle_path = shutil.which("gradle") or ""
python_path = shutil.which("python") or ""
aapt2_path = shutil.which("aapt2") or ""
ffmpeg_path = shutil.which("ffmpeg") or ""
ytdlp_path = shutil.which("yt-dlp") or ""
edgetts_path = shutil.which("edge-tts") or ""

java_ver = ""
if java_path:
    try:
        import subprocess
        res = subprocess.run([java_path, "-version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        java_ver = (res.stderr or res.stdout).splitlines()[0]
    except Exception as e:
        java_ver = str(e)

gradle_ver = ""
if gradle_path:
    try:
        import subprocess
        res = subprocess.run([gradle_path, "-v"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        for line in (res.stdout or "").splitlines():
            if "Gradle " in line:
                gradle_ver = line.strip()
                break
    except Exception as e:
        gradle_ver = str(e)

sdk34_ok = bool(sdk and os.path.isfile(os.path.join(sdk, "platforms", "android-34", "android.jar")))

ext_props = os.path.expanduser("~/.termux/termux.properties")
allow_ext = False
if os.path.isfile(ext_props):
    try:
        with open(ext_props, "r") as f:
            for l in f:
                if l.strip().startswith("#"): continue
                if "allow-external-apps=true" in l.replace(" ", ""):
                    allow_ext = True
                    break
    except Exception:
        pass

result = {
    "allow_external_apps": allow_ext,
    "java": bool(java_path),
    "java_detail": java_ver or ("Found at " + java_path if java_path else "Not found"),
    "java_17_plus": ("17" in java_ver or "18" in java_ver or "19" in java_ver or "21" in java_ver),
    "gradle": bool(gradle_path),
    "gradle_detail": gradle_ver or ("Found at " + gradle_path if gradle_path else "Not found"),
    "python": bool(python_path),
    "python_detail": python_path or "Not found",
    "aapt2": bool(aapt2_path),
    "aapt2_detail": aapt2_path or "Not found",
    "sdk34": sdk34_ok,
    "sdk34_detail": ("Platforms 34 found in " + sdk) if sdk34_ok else ("Missing in " + (sdk or "unset ANDROID_HOME")),
    "ffmpeg": bool(ffmpeg_path),
    "ffmpeg_detail": ffmpeg_path or "Not found",
    "yt_dlp": bool(ytdlp_path),
    "yt_dlp_detail": ytdlp_path or "Not found",
    "edge_tts": bool(edgetts_path),
    "edge_tts_detail": edgetts_path or "Not found"
}

print("JSON_START")
print(json.dumps(result))
print("JSON_END")
sys.exit(0)
""".trimIndent()
    }

    fun parseDiagnosticOutput(output: String, checks: List<DiagnosticCheck>): DiagnosticsReport {
        val checkMap = checks.associateBy { it.id }

        if (output.contains("JSON_START") && output.contains("JSON_END")) {
            val jsonStr = output.substringAfter("JSON_START").substringBefore("JSON_END").trim()
            runCatching {
                val json = JSONObject(jsonStr)

                // 3. allow-external-apps
                val allowExt = json.optBoolean("allow_external_apps", false)
                checkMap["allow_external_apps"]?.apply {
                    status = if (allowExt) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = if (allowExt) "allow-external-apps=true verified" else "Missing in ~/.termux/termux.properties"
                }

                // 4. java available
                val javaOk = json.optBoolean("java", false)
                checkMap["java_available"]?.apply {
                    status = if (javaOk) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = json.optString("java_detail", if (javaOk) "Available" else "Not found")
                }

                // 5. java version
                val java17 = json.optBoolean("java_17_plus", false)
                checkMap["java_version"]?.apply {
                    status = if (java17) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = json.optString("java_detail", "")
                }

                // 6. gradle available
                val gradleOk = json.optBoolean("gradle", false)
                checkMap["gradle_available"]?.apply {
                    status = if (gradleOk) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = json.optString("gradle_detail", if (gradleOk) "Available" else "Not found")
                }

                // 7. gradle version
                checkMap["gradle_version"]?.apply {
                    status = if (gradleOk) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = json.optString("gradle_detail", "")
                }

                // 8. python available
                val pyOk = json.optBoolean("python", false)
                checkMap["python_available"]?.apply {
                    status = if (pyOk) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = json.optString("python_detail", if (pyOk) "Available" else "Not found")
                }

                // 9. aapt2
                val aapt2Ok = json.optBoolean("aapt2", false)
                checkMap["aapt2_available"]?.apply {
                    status = if (aapt2Ok) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = json.optString("aapt2_detail", if (aapt2Ok) "Available" else "Not found")
                }

                // 10. sdk34
                val sdkOk = json.optBoolean("sdk34", false)
                checkMap["sdk34_available"]?.apply {
                    status = if (sdkOk) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = json.optString("sdk34_detail", "")
                }

                // 11. ffmpeg
                val ffmpegOk = json.optBoolean("ffmpeg", false)
                checkMap["ffmpeg_available"]?.apply {
                    status = if (ffmpegOk) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = json.optString("ffmpeg_detail", "")
                }

                // 12. yt-dlp
                val ytdlpOk = json.optBoolean("yt_dlp", false)
                checkMap["yt_dlp_available"]?.apply {
                    status = if (ytdlpOk) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = json.optString("yt_dlp_detail", "")
                }

                // 13. edge-tts
                val ttsOk = json.optBoolean("edge_tts", false)
                checkMap["edge_tts_available"]?.apply {
                    status = if (ttsOk) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
                    detail = json.optString("edge_tts_detail", "")
                }
            }
        }

        val allMandatoryPassed = checks.filter { it.isMandatory }.all { it.status == DiagnosticStatus.PASS }
        val passedCount = checks.count { it.status == DiagnosticStatus.PASS }
        val failedCount = checks.count { it.status == DiagnosticStatus.FAIL }

        val summary = if (allMandatoryPassed) {
            "ENGINE READY • $passedCount/15 checks passed"
        } else {
            "ENGINE NOT READY • $failedCount failed mandatory checks"
        }

        return DiagnosticsReport(
            checks = checks,
            allMandatoryPassed = allMandatoryPassed,
            summary = summary
        )
    }
}
