package com.zipbug.base.repair

import android.util.Base64
import com.zipbug.base.termux.TermuxBridge

object CompilerPatchTermux {
    private const val MAX_SNIPPET_CHARS = 2_500
    private const val MAX_ENCODED_ARG_CHARS = 3_800

    private const val PATCH_SCRIPT =
        "import base64,pathlib,sys\n" +
            "root=pathlib.Path(sys.argv[1]).resolve()\n" +
            "rel=sys.argv[2]\n" +
            "target=(root/rel).resolve()\n" +
            "if target==root or root not in target.parents: raise SystemExit('PATCH_PATH_OUTSIDE_ROOT')\n" +
            "if not target.is_file(): raise SystemExit('PATCH_TARGET_MISSING')\n" +
            "old=base64.b64decode(sys.argv[3]).decode('utf-8')\n" +
            "new=base64.b64decode(sys.argv[4]).decode('utf-8')\n" +
            "text=target.read_text(encoding='utf-8')\n" +
            "if old not in text: raise SystemExit('PATCH_ORIGINAL_NOT_FOUND')\n" +
            "backup=target.with_name(target.name+'.zipbug.bak')\n" +
            "backup.write_text(text,encoding='utf-8')\n" +
            "target.write_text(text.replace(old,new,1),encoding='utf-8')\n" +
            "print('ZIPBUG_PATCH_OK|'+rel)\n"

    fun buildRequest(
        projectRoot: String,
        patch: PatchProposal
    ): Result<TermuxBridge.Request> = runCatching {
        require(
            projectRoot.startsWith("/data/data/com.termux/files/")
        ) {
            "Reviewed patch project must be inside the Termux workspace."
        }

        val relative = normalizeRelativePath(patch.targetFile)
        require(patch.originalSnippet.isNotEmpty()) {
            "Original patch snippet cannot be empty."
        }
        require(patch.originalSnippet.length <= MAX_SNIPPET_CHARS) {
            "Original patch snippet is too large for the reviewed patch workflow."
        }
        require(patch.replacementSnippet.length <= MAX_SNIPPET_CHARS) {
            "Replacement patch snippet is too large for the reviewed patch workflow."
        }

        val originalEncoded = b64(patch.originalSnippet)
        val replacementEncoded = b64(patch.replacementSnippet)
        require(
            originalEncoded.length <= MAX_ENCODED_ARG_CHARS &&
                replacementEncoded.length <= MAX_ENCODED_ARG_CHARS
        ) {
            "Reviewed patch snippet is too large for a safe Termux argument."
        }

        TermuxBridge.Request(
            tool = "python",
            args = listOf(
                "-c",
                PATCH_SCRIPT,
                projectRoot,
                relative,
                originalEncoded,
                replacementEncoded
            ),
            workDir = projectRoot,
            label = "Zip_Bug Reviewed Compiler Patch",
            allowInlineCode = true
        )
    }

    private fun normalizeRelativePath(raw: String): String {
        val normalized = raw
            .trim()
            .replace('\\', '/')
            .trimStart('/')

        require(normalized.isNotBlank()) {
            "Patch target file cannot be blank."
        }
        require(!WINDOWS_DRIVE_PREFIX.containsMatchIn(normalized)) {
            "Absolute Windows patch path rejected."
        }
        require(
            normalized.split('/').none {
                it.isBlank() || it == ".." || it == "."
            }
        ) {
            "Patch target must be a clean relative path."
        }
        return normalized
    }

    private fun b64(value: String): String =
        Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

    private val WINDOWS_DRIVE_PREFIX =
        Regex("^[A-Za-z]:[/\\\\]")
}
