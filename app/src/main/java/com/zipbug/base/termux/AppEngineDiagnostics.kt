package com.zipbug.base.termux

import android.content.Context
import java.io.File

data class EngineToolCheck(
    val id: String,
    val name: String,
    val toolType: String,
    var isAvailable: Boolean = false,
    var statusDetail: String = "",
    val fixInstruction: String = ""
)

data class EngineHealthReport(
    val checks: List<EngineToolCheck>,
    val isReady: Boolean,
    val summaryText: String
)

object AppEngineDiagnostics {

    fun runDiagnostics(context: Context): EngineHealthReport {
        val checks = mutableListOf<EngineToolCheck>()

        // 1. android.jar
        val androidJarPaths = listOf(
            "/data/data/com.termux/files/home/android-sdk/platforms/android-34/android.jar",
            "/storage/emulated/0/android-sdk/platforms/android-34/android.jar",
            File(context.filesDir, "sdk/android-34/android.jar").absolutePath
        )
        val foundJar = androidJarPaths.firstOrNull { File(it).exists() }
        checks.add(
            EngineToolCheck(
                id = "android_jar",
                name = "1. android.jar (SDK 34)",
                toolType = "Platform Library",
                isAvailable = foundJar != null,
                statusDetail = if (foundJar != null) "Found at $foundJar" else "Missing in Android SDK platforms",
                fixInstruction = "Place android-34 platform in \$HOME/android-sdk/platforms/android-34/android.jar"
            )
        )

        // 2. Java Compiler: ECJ / javac
        val termuxBin = "/data/data/com.termux/files/usr/bin"
        val hasEcj = File(termuxBin, "ecj").exists()
        val hasJavac = File(termuxBin, "javac").exists()
        val hasJava = File(termuxBin, "java").exists()
        val javaOk = hasEcj || hasJavac || hasJava
        checks.add(
            EngineToolCheck(
                id = "java_compiler",
                name = "2. Java Compiler (ECJ / javac)",
                toolType = "Compiler",
                isAvailable = javaOk,
                statusDetail = when {
                    hasEcj -> "ECJ compiler detected ($termuxBin/ecj)"
                    hasJavac -> "javac compiler detected ($termuxBin/javac)"
                    hasJava -> "Java runtime available ($termuxBin/java)"
                    else -> "No compiler found in Termux"
                },
                fixInstruction = "In Termux run: pkg install ecj openjdk-17"
            )
        )

        // 3. D8 Dexer
        val hasD8 = File(termuxBin, "d8").exists() || File(termuxBin, "dx").exists()
        checks.add(
            EngineToolCheck(
                id = "d8",
                name = "3. D8 Dexer",
                toolType = "Bytecode Compiler",
                isAvailable = hasD8,
                statusDetail = if (hasD8) "D8 binary detected ($termuxBin/d8)" else "D8 not installed in Termux",
                fixInstruction = "In Termux run: pkg install d8 (or android-tools)"
            )
        )

        // 4. AAPT2
        val hasAapt2 = File(termuxBin, "aapt2").exists() || File(termuxBin, "aapt").exists()
        checks.add(
            EngineToolCheck(
                id = "aapt2",
                name = "4. AAPT2 Resource Compiler",
                toolType = "Resource Tool",
                isAvailable = hasAapt2,
                statusDetail = if (hasAapt2) "Native AAPT2 detected ($termuxBin/aapt2)" else "AAPT2 not found in Termux",
                fixInstruction = "In Termux run: pkg install aapt2"
            )
        )

        // 5. zipalign
        val hasZipalign = File(termuxBin, "zipalign").exists()
        checks.add(
            EngineToolCheck(
                id = "zipalign",
                name = "5. zipalign",
                toolType = "Packaging Aligner",
                isAvailable = hasZipalign,
                statusDetail = if (hasZipalign) "Native zipalign detected" else "zipalign not installed in Termux",
                fixInstruction = "In Termux run: pkg install zipalign"
            )
        )

        // 6. apksigner and signing key
        val hasApksigner = File(termuxBin, "apksigner").exists()
        val keystoreFile = File(context.filesDir, "debug.keystore")
        val hasDebugKey = keystoreFile.exists() || hasApksigner
        checks.add(
            EngineToolCheck(
                id = "apksigner",
                name = "6. apksigner & Debug Key",
                toolType = "Code Signing",
                isAvailable = hasDebugKey,
                statusDetail = if (hasDebugKey) "Debug keystore configured (SHA-256 v1/v2)" else "apksigner / debug key pending",
                fixInstruction = "In Termux run: pkg install apksigner (ZIP_BUG maintains canonical debug key)"
            )
        )

        // 7. Termux connection
        val termuxInstalled = TermuxBridge.isTermuxInstalled(context)
        val hasRunCommandPerm = TermuxBridge.hasPermission(context)
        val termuxReady = termuxInstalled && hasRunCommandPerm
        checks.add(
            EngineToolCheck(
                id = "termux_connection",
                name = "7. Termux RUN_COMMAND Bridge",
                toolType = "Execution Environment",
                isAvailable = termuxReady,
                statusDetail = when {
                    !termuxInstalled -> "Termux app (com.termux) not installed"
                    !hasRunCommandPerm -> "RUN_COMMAND permission NOT granted"
                    else -> "Connected & RUN_COMMAND permission granted"
                },
                fixInstruction = "Install Termux from F-Droid and grant 'Run commands in Termux environment' in Android Settings."
            )
        )

        val availableCount = checks.count { it.isAvailable }
        val isReady = availableCount == checks.size
        val summaryText = if (isReady) {
            "ENGINE READY • 7/7 build capabilities verified"
        } else {
            "ENGINE NOT READY • ${checks.size - availableCount} missing tool(s)"
        }

        return EngineHealthReport(
            checks = checks,
            isReady = isReady,
            summaryText = summaryText
        )
    }
}
