package com.zipbug.base.artifact

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

enum class InstallSafetyStatus(val label: String) {
    SAFE_UPDATE("SAFE UPDATE"),
    SIGNER_CONFLICT("SIGNER CONFLICT"),
    NEW_INSTALL("NEW INSTALL"),
    UNKNOWN("UNKNOWN")
}

data class ApkMetadata(
    val file: File,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sizeBytes: Long,
    val sha256: String,
    val signerCertificateSha256: String? = null,
    val isInstalled: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    val installedSignerSha256: String? = null,
    val signerConflict: Boolean = false
) {
    val safetyStatus: InstallSafetyStatus
        get() = when {
            !isInstalled -> InstallSafetyStatus.NEW_INSTALL
            signerConflict -> InstallSafetyStatus.SIGNER_CONFLICT
            signerCertificateSha256 != null && !signerConflict -> InstallSafetyStatus.SAFE_UPDATE
            else -> InstallSafetyStatus.UNKNOWN
        }

    val conflictExplanation: String?
        get() = if (safetyStatus == InstallSafetyStatus.SIGNER_CONFLICT) {
            "Same package name is already installed but signed with a different key."
        } else null
}

object ApkArtifactInspector {

    fun inspect(context: Context, apkFile: File): Result<ApkMetadata> {
        return runCatching {
            if (!apkFile.exists()) {
                throw IllegalArgumentException("APK file not found: ${apkFile.absolutePath}")
            }

            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
                ?: throw IllegalStateException("Could not parse APK archive info: ${apkFile.name}")

            val pkgName = archiveInfo.packageName
            val versionName = archiveInfo.versionName ?: "1.0.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }
            val minSdk = archiveInfo.applicationInfo?.minSdkVersion ?: 24
            val targetSdk = archiveInfo.applicationInfo?.targetSdkVersion ?: 34

            // Calculate file SHA-256
            val fileSha256 = calculateSha256(apkFile)

            // Extract signer certificate hash
            var apkSignerHash: String? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = archiveInfo.signingInfo
                if (signingInfo != null) {
                    val sigs = if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                    if (!sigs.isNullOrEmpty()) {
                        apkSignerHash = hashBytesSha256(sigs[0].toByteArray())
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val sigs = archiveInfo.signatures
                if (!sigs.isNullOrEmpty()) {
                    apkSignerHash = hashBytesSha256(sigs[0].toByteArray())
                }
            }

            // Check installed version & compare signers
            var isInstalled: Boolean
            var installedVerName: String? = null
            var installedVerCode: Long? = null
            var installedSignerHash: String? = null
            var signerConflict = false

            try {
                val installedInfo: PackageInfo = pm.getPackageInfo(pkgName, flags)
                isInstalled = true
                installedVerName = installedInfo.versionName
                installedVerCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    installedInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    installedInfo.versionCode.toLong()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val installedSigning = installedInfo.signingInfo
                    if (installedSigning != null) {
                        val installedSigs = if (installedSigning.hasMultipleSigners()) {
                            installedSigning.apkContentsSigners
                        } else {
                            installedSigning.signingCertificateHistory
                        }
                        if (!installedSigs.isNullOrEmpty()) {
                            installedSignerHash = hashBytesSha256(installedSigs[0].toByteArray())
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val installedSigs = installedInfo.signatures
                    if (!installedSigs.isNullOrEmpty()) {
                        installedSignerHash = hashBytesSha256(installedSigs[0].toByteArray())
                    }
                }

                if (apkSignerHash != null && installedSignerHash != null) {
                    if (apkSignerHash != installedSignerHash) {
                        signerConflict = true
                    }
                }
            } catch (_: PackageManager.NameNotFoundException) {
                isInstalled = false
            }

            ApkMetadata(
                file = apkFile,
                packageName = pkgName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                sizeBytes = apkFile.length(),
                sha256 = fileSha256,
                signerCertificateSha256 = apkSignerHash,
                isInstalled = isInstalled,
                installedVersionName = installedVerName,
                installedVersionCode = installedVerCode,
                installedSignerSha256 = installedSignerHash,
                signerConflict = signerConflict
            )
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun hashBytesSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

