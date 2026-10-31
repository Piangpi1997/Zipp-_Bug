package com.zipbug.base.media

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode

/**
 * In-app FFmpeg engine backed by the bundled FFmpegKit (full) native
 * libraries — the same FFmpeg argument lists [com.zipbug.base.zippy.ZippyCutPlan]
 * builds for the Termux engine run here, inside the app process, so no
 * Termux round-trip is needed. Blocking: call on a background dispatcher.
 * Results are the real FFmpegKit session return code and log tail —
 * nothing is simulated.
 */
object InAppFfmpegEngine {

    data class Outcome(
        val success: Boolean,
        val returnCode: Int,
        val logTail: String
    )

    fun execute(args: List<String>): Outcome {
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        val rc = session.returnCode
        val output = session.allLogsAsString ?: ""
        return Outcome(
            success = ReturnCode.isSuccess(rc),
            returnCode = rc?.value ?: -1,
            logTail = output.takeLast(600)
        )
    }

    /** FFmpegKit version string (e.g. "6.0"), or "unavailable". */
    fun version(): String =
        runCatching { FFmpegKitConfig.getVersion() }.getOrDefault("unavailable")
}
