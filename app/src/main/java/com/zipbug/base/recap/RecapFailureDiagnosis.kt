package com.zipbug.base.recap

data class RecapFailureDiagnosis(
    val whatFailed: String,
    val why: String,
    val safeNextStep: String,
    val canRetry: Boolean = true,
    val retryNote: String = "Available"
) {
    fun format(): String = buildString {
        append("WHAT FAILED: ").append(whatFailed).append("\n")
        append("WHY: ").append(why).append("\n")
        append("SAFE NEXT STEP: ").append(safeNextStep).append("\n")
        append("RETRY: ").append(if (canRetry) "Available ($retryNote)" else "Blocked ($retryNote)")
    }

    companion object {
        fun missingApiKey(providerName: String): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "AI Script Generation",
                why = "No API key configured for $providerName.",
                safeNextStep = "Open Settings and configure a valid API key for $providerName.",
                canRetry = true,
                retryNote = "Configure API key in Settings and retry"
            )

        fun missingMediaFile(path: String?): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "Media Input Verification",
                why = "Source file ${path ?: "unspecified"} cannot be read or does not exist.",
                safeNextStep = "Reselect a valid video or audio file using the Storage Access Framework.",
                canRetry = true,
                retryNote = "Reselect source file and retry"
            )

        fun mediaStagingFailed(
            uri: String,
            reason: String
        ): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "Media Staging",
                why =
                    "The selected media could not be copied into shared storage " +
                        "for Termux/FFmpeg. Source: $uri. Reason: ${reason.take(180)}",
                safeNextStep =
                    "Reselect the media. Zip_Bug will copy it into " +
                        "Download/Zip_Bug/Recap before rendering.",
                canRetry = true,
                retryNote = "Reselect source media and retry"
            )

        fun invalidClipCount(count: Int): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "Timeline Validation",
                why = "Project contains $count clips. Allowed range is 1 to 18 clips.",
                safeNextStep = "Adjust the project timeline to have between 1 and 18 clips.",
                canRetry = true,
                retryNote = "Adjust clip count and retry"
            )

        fun invalidTrimRange(clipIndex: Int, startMs: Long, endMs: Long, durationMs: Long): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "Clip Trim Validation (Clip #${clipIndex + 1})",
                why = "Invalid trim range: startMs ($startMs) must be < endMs ($endMs), and endMs must be <= duration ($durationMs).",
                safeNextStep = "Adjust trim markers so start timestamp is strictly less than end timestamp.",
                canRetry = true,
                retryNote = "Fix trim range and retry"
            )

        fun missingVoiceover(partNumber: Int, reason: String): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "Myanmar TTS Voiceover (Part $partNumber)",
                why = "Voiceover generation failed: $reason",
                safeNextStep = "Ensure Edge-TTS is installed in Termux and check internet connectivity.",
                canRetry = true,
                retryNote = "Retry voiceover generation"
            )

        fun ffmpegProcessFailed(exitCode: Int?, errorText: String): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "FFmpeg Video Render",
                why = "FFmpeg process failed with exit code ${exitCode ?: "unknown"}. ${errorText.take(200)}",
                safeNextStep = "Check Termux execution logs and verify all source media codecs are supported.",
                canRetry = true,
                retryNote = "Retry render"
            )

        fun outputVerificationFailed(path: String, exists: Boolean, sizeBytes: Long): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "Output Verification",
                why = if (!exists) {
                    "Output file does not exist at $path."
                } else {
                    "Output file size is $sizeBytes bytes (empty/corrupted file)."
                },
                safeNextStep = "Check render logs for FFmpeg codec/filter errors and re-render.",
                canRetry = true,
                retryNote = "Re-render after verifying media inputs"
            )

        fun renderCancelled(): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "Render Execution",
                why = "Render job was cancelled by the user.",
                safeNextStep = "Restart the render when ready.",
                canRetry = true,
                retryNote = "Restart render"
            )

        fun renderTimeout(timeoutMs: Long): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "Render Pipeline Timeout",
                why = "Render processing exceeded timeout threshold of ${timeoutMs / 1000}s.",
                safeNextStep = "Reduce the number or length of clips, or check Termux background process limits.",
                canRetry = true,
                retryNote = "Retry render"
            )

        fun interrupted(): RecapFailureDiagnosis =
            RecapFailureDiagnosis(
                whatFailed = "Render Process Interrupted",
                why = "The application process was restarted or terminated while render was running.",
                safeNextStep = "Reopen the project and restart the render job.",
                canRetry = true,
                retryNote = "Restart render"
            )
    }
}
