package com.zipbug.base.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.artifact.ApkArtifactInspector
import com.zipbug.base.databinding.ActivitySigningDiagnosticsBinding
import kotlinx.coroutines.launch
import java.io.File

class SigningDiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySigningDiagnosticsBinding
    private var newestApkPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySigningDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadDiagnostics()

        binding.btnAppInfo.setOnClickListener {
            runCatching {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }

        binding.btnUninstall.setOnClickListener {
            runCatching {
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.fromParts("package", packageName, null)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
                startActivity(intent)
            }
        }

        binding.btnInspectArtifact.setOnClickListener {
            val path = newestApkPath
            if (path != null && File(path).exists()) {
                ApkDetailBottomSheetDialogFragment.newInstance(path)
                    .show(supportFragmentManager, ApkDetailBottomSheetDialogFragment.TAG)
            } else {
                Snackbar.make(binding.root, "No APK build artifact discovered yet. Build via Projects tab.", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun loadDiagnostics() {
        // 1. Get installed package signature
        var installedSignerHash: String? = null
        val pm = packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        try {
            val info = pm.getPackageInfo(packageName, flags)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo
                if (signingInfo != null) {
                    val sigs = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
                    if (!sigs.isNullOrEmpty()) {
                        installedSignerHash = ApkArtifactInspector.hashBytesSha256(sigs[0].toByteArray())
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val sigs = info.signatures
                if (!sigs.isNullOrEmpty()) {
                    installedSignerHash = ApkArtifactInspector.hashBytesSha256(sigs[0].toByteArray())
                }
            }
            binding.installedSignerText.text = "Package: $packageName\nVersion: ${info.versionName}\nSigner SHA-256:\n${installedSignerHash ?: "(None found)"}"
        } catch (e: Exception) {
            binding.installedSignerText.text = "Error reading installed package: ${e.message}"
        }

        // 2. Query latest APK from DB
        lifecycleScope.launch {
            val app = application as ZipBugApp
            val artifacts = app.database.apkArtifactDao().listAll()
            val latest = artifacts.firstOrNull()

            if (latest == null) {
                binding.artifactSignerText.text = "No discovered APK artifacts in database.\nBuild an APK to diagnose certificate parity."
                binding.matchBadge.text = "NO ARTIFACT"
                binding.matchBadge.setTextColor(ContextCompat.getColor(this@SigningDiagnosticsActivity, R.color.z_muted))
                return@launch
            }

            newestApkPath = latest.filePath
            binding.artifactSignerText.text = "File: ${latest.fileName}\nPath: ${latest.filePath}\nPackage: ${latest.packageName}\nSigner SHA-256:\n${latest.signerCertificateSha256 ?: "(None)"}"

            val apkSigner = latest.signerCertificateSha256
            if (installedSignerHash != null && apkSigner != null) {
                if (installedSignerHash == apkSigner) {
                    binding.matchBadge.text = "SIGNATURES MATCH"
                    binding.matchBadge.setTextColor(ContextCompat.getColor(this@SigningDiagnosticsActivity, R.color.z_green))
                    binding.conflictBanner.visibility = View.GONE
                } else {
                    binding.matchBadge.text = "SIGNER CONFLICT"
                    binding.matchBadge.setTextColor(ContextCompat.getColor(this@SigningDiagnosticsActivity, R.color.z_red))
                    binding.conflictBanner.visibility = View.VISIBLE
                    binding.conflictBanner.text = "SIGNER CONFLICT: The installed package and newly built APK were signed with different keys. Android prevents updates when keys differ."
                }
            } else {
                binding.matchBadge.text = "UNKNOWN"
                binding.matchBadge.setTextColor(ContextCompat.getColor(this@SigningDiagnosticsActivity, R.color.z_orange))
            }
        }
    }
}
