package com.zipbug.base.termux

object EngineDoctor {
    fun request(
        home: String
    ): TermuxBridge.Request {
        val script = """
import os
import shutil
import sys

home = os.path.expanduser("~")
sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""

checks = {
    "java": shutil.which("java"),
    "gradle": shutil.which("gradle"),
    "python": shutil.which("python"),
    "aapt2": shutil.which("aapt2"),
    "ffmpeg": shutil.which("ffmpeg"),
    "yt-dlp": shutil.which("yt-dlp"),
    "sdk34": os.path.isfile(os.path.join(sdk, "platforms", "android-34", "android.jar")) if sdk else False,
}

print("ZIP_BUG TERMUX DOCTOR")
print("ANDROID_HOME=" + (sdk or "unset"))
for key, value in checks.items():
    print(f"{key}: {value if value else 'NOT FOUND'}")

required = ["java", "gradle", "python", "aapt2", "sdk34"]
ok = all(bool(checks[key]) for key in required)
print("RESULT=" + ("READY" if ok else "NOT_READY"))
sys.exit(0 if ok else 2)
        """.trimIndent()

        return TermuxBridge.Request(
            tool = "python",
            args = listOf("-c", script),
            workDir = home,
            label = "Zip_Bug Termux Doctor"
        )
    }
}
