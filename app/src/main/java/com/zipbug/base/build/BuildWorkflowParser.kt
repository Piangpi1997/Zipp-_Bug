package com.zipbug.base.build

data class BuildStageStatus(
    val name: String,
    val state: String,
    val detail: String
)

data class BuildArtifactProof(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val signatureVerification: String
)

data class BuildWorkflowSnapshot(
    val stages: List<BuildStageStatus>,
    val artifact: BuildArtifactProof?,
    val result: String?,
    val resultDetail: String?
) {
    fun stage(name: String): BuildStageStatus? =
        stages.lastOrNull {
            it.name.equals(
                name,
                ignoreCase = true
            )
        }
}

object BuildWorkflowParser {
    private const val STAGE_PREFIX =
        "ZIPBUG_STAGE|"
    private const val ARTIFACT_PREFIX =
        "ZIPBUG_ARTIFACT|"
    private const val RESULT_PREFIX =
        "ZIPBUG_RESULT|"

    fun parse(stdout: String): BuildWorkflowSnapshot {
        val stages = linkedMapOf<String, BuildStageStatus>()
        var artifact: BuildArtifactProof? = null
        var result: String? = null
        var resultDetail: String? = null

        stdout.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()

            when {
                line.startsWith(STAGE_PREFIX) -> {
                    val parts = line.split(
                        '|',
                        limit = 4
                    )

                    if (parts.size == 4) {
                        val stage = BuildStageStatus(
                            name = parts[1],
                            state = parts[2],
                            detail = parts[3]
                        )
                        stages[stage.name.uppercase()] =
                            stage
                    }
                }

                line.startsWith(ARTIFACT_PREFIX) -> {
                    val parts = line.split(
                        '|',
                        limit = 5
                    )

                    if (parts.size == 5) {
                        val size = parts[2].toLongOrNull()
                        if (
                            size != null &&
                            size > 0 &&
                            parts[1].isNotBlank() &&
                            parts[3].length == 64
                        ) {
                            artifact = BuildArtifactProof(
                                path = parts[1],
                                sizeBytes = size,
                                sha256 = parts[3],
                                signatureVerification = parts[4]
                            )
                        }
                    }
                }

                line.startsWith(RESULT_PREFIX) -> {
                    val parts = line.split(
                        '|',
                        limit = 3
                    )

                    if (parts.size >= 2) {
                        result = parts[1]
                        resultDetail =
                            parts.getOrNull(2)
                    }
                }
            }
        }

        return BuildWorkflowSnapshot(
            stages = stages.values.toList(),
            artifact = artifact,
            result = result,
            resultDetail = resultDetail
        )
    }
}
