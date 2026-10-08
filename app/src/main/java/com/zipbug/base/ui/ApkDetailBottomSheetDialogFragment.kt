package com.zipbug.base.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.artifact.ApkArtifactInspector
import com.zipbug.base.artifact.ApkMetadata
import com.zipbug.base.artifact.InstallSafetyStatus
import com.zipbug.base.databinding.DialogApkDetailBinding
import java.io.File
import java.util.Locale

class ApkDetailBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogApkDetailBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "ApkDetailBottomSheet"
        private const val ARG_APK_PATH = "arg_apk_path"

        fun newInstance(apkPath: String): ApkDetailBottomSheetDialogFragment {
            return ApkDetailBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_APK_PATH, apkPath)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogApkDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val path = arguments?.getString(ARG_APK_PATH) ?: run {
            dismiss()
            return
        }

        val apkFile = File(path)
        if (!apkFile.exists()) {
            Snackbar.make(binding.root, "APK file not found: $path", Snackbar.LENGTH_LONG).show()
            dismiss()
            return
        }

        val inspectResult = ApkArtifactInspector.inspect(requireContext(), apkFile)
        inspectResult.onSuccess { metadata ->
            bindMetadata(metadata)
        }.onFailure { err ->
            binding.metadataText.text = "Failed to inspect APK:\n${err.message}"
            binding.safetyBadge.text = "ERROR"
            binding.safetyBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.z_red))
            binding.btnInstall.isEnabled = false
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun bindMetadata(metadata: ApkMetadata) {
        val context = requireContext()
        val status = metadata.safetyStatus

        binding.safetyBadge.text = status.label
        when (status) {
            InstallSafetyStatus.SAFE_UPDATE -> {
                binding.safetyBadge.setTextColor(ContextCompat.getColor(context, R.color.z_green))
                binding.safetyBadge.setBackgroundColor(0xFF1E3A24.toInt())
            }
            InstallSafetyStatus.SIGNER_CONFLICT -> {
                binding.safetyBadge.setTextColor(ContextCompat.getColor(context, R.color.z_red))
                binding.safetyBadge.setBackgroundColor(0xFF3D1818.toInt())
                binding.conflictWarning.visibility = View.VISIBLE
                binding.conflictWarning.text = metadata.conflictExplanation
                    ?: "Same package name is already installed but signed with a different key."
            }
            InstallSafetyStatus.NEW_INSTALL -> {
                binding.safetyBadge.setTextColor(ContextCompat.getColor(context, R.color.z_blue))
                binding.safetyBadge.setBackgroundColor(0xFF162D4A.toInt())
            }
            InstallSafetyStatus.UNKNOWN -> {
                binding.safetyBadge.setTextColor(ContextCompat.getColor(context, R.color.z_orange))
                binding.safetyBadge.setBackgroundColor(0xFF3E2D12.toInt())
            }
        }

        val sizeFormatted = formatFileSize(metadata.sizeBytes)
        val infoText = buildString {
            append("FILE: ").append(metadata.file.name).append("\n")
            append("PATH: ").append(metadata.file.absolutePath).append("\n")
            append("SIZE: ").append(sizeFormatted).append(" (").append(metadata.sizeBytes).append(" bytes)\n")
            append("SHA-256: ").append(metadata.sha256).append("\n\n")

            append("PACKAGE: ").append(metadata.packageName).append("\n")
            append("VERSION: ").append(metadata.versionName).append(" (code ").append(metadata.versionCode).append(")\n")
            append("SDK: min ").append(metadata.minSdk).append(" | target ").append(metadata.targetSdk).append("\n\n")

            append("APK SIGNER SHA-256:\n")
            append(metadata.signerCertificateSha256 ?: "(No signer certificate found)").append("\n\n")

            if (metadata.isInstalled) {
                append("INSTALLED APP ON DEVICE:\n")
                append("Version: ").append(metadata.installedVersionName ?: "unknown")
                    .append(" (code ").append(metadata.installedVersionCode ?: 0).append(")\n")
                append("Installed Signer SHA-256:\n")
                append(metadata.installedSignerSha256 ?: "(unknown)").append("\n")
            } else {
                append("INSTALLED APP: Not installed on device\n")
            }
        }
        binding.metadataText.text = infoText

        // App info button
        if (metadata.isInstalled) {
            binding.btnAppInfo.visibility = View.VISIBLE
            binding.btnAppInfo.setOnClickListener {
                runCatching {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", metadata.packageName, null)
                    }
                    startActivity(intent)
                }
            }

            binding.btnUninstall.visibility = View.VISIBLE
            binding.btnUninstall.setOnClickListener {
                runCatching {
                    @Suppress("DEPRECATION")
                    val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                        data = Uri.fromParts("package", metadata.packageName, null)
                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    }
                    startActivity(intent)
                }
            }
        } else {
            binding.btnAppInfo.visibility = View.GONE
            binding.btnUninstall.visibility = View.GONE
        }

        // Install button
        binding.btnInstall.setOnClickListener {
            launchInstaller(metadata.file)
        }
    }

    private fun launchInstaller(file: File) {
        val context = requireContext()
        runCatching {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(installIntent)
            dismiss()
        }.onFailure { e ->
            Snackbar.make(binding.root, "Failed to launch installer: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.2f %s", value, units[digitGroups.coerceAtMost(units.size - 1)])
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
