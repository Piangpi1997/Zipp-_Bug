package com.zipbug.base.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.BuildJobEntity
import com.zipbug.base.databinding.FragmentTerminalBinding
import com.zipbug.base.termux.CommandLineParser
import com.zipbug.base.termux.EngineDoctor
import com.zipbug.base.termux.TermuxBridge
import kotlinx.coroutines.launch

class TerminalFragment : Fragment() {
    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTerminalBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        binding.run.setOnClickListener {
            runCommand(binding.command.text.toString())
        }

        binding.ffmpeg.setOnClickListener {
            binding.command.setText("/termux ffmpeg -version")
        }

        binding.python.setOnClickListener {
            binding.command.setText("/termux python --version")
        }

        binding.gradle.setOnClickListener {
            binding.command.setText("/termux gradle --version")
        }

        binding.buildApk.setOnClickListener {
            binding.command.setText("/apkbuilder")
            runCommand("/apkbuilder")
        }

        binding.doctor.setOnClickListener {
            runDoctor()
        }

        observeLatestJob()
    }

    private fun observeLatestJob() {
        val app = requireActivity().application as ZipBugApp

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                app.database.buildJobDao()
                    .observeLatest()
                    .collect { job ->
                        if (job != null && _binding != null) {
                            render(job)
                        }
                    }
            }
        }
    }

    private fun render(job: BuildJobEntity) {
        val exit = job.exitCode?.let {
            "\nexitCode: $it"
        }.orEmpty()

        val err = job.errorCode?.let {
            "\ntermuxError: $it"
        }.orEmpty()

        binding.engineState.text =
            if (job.tool == "cp" && job.status == BuildJobEntity.SUCCESS) {
                "APK EXPORTED • COPY EXIT 0"
            } else {
                "ENGINE • ${job.status} • ${job.tool}"
            }

        binding.output.text = buildString {
            append("job: ${job.id}\n")
            append("tool: ${job.tool}\n")
            append("workdir: ${job.workDir}\n")
            append("status: ${job.status}")
            append(exit)
            append(err)

            if (job.stdout.isNotBlank()) {
                append("\n\nSTDOUT\n")
                append(job.stdout)
            }

            if (job.stderr.isNotBlank()) {
                append("\n\nSTDERR\n")
                append(job.stderr)
            }

            if (job.errorMessage.isNotBlank()) {
                append("\n\nERROR\n")
                append(job.errorMessage)
            }
        }
    }

    private fun runDoctor() {
        val home = requireContext()
            .getSharedPreferences("zipbug.settings", 0)
            .getString(
                "termuxHome",
                "/data/data/com.termux/files/home"
            )!!

        binding.engineState.text = "ENGINE • DOCTOR RUNNING"

        viewLifecycleOwner.lifecycleScope.launch {
            TermuxBridge.send(
                requireContext(),
                EngineDoctor.request(home)
            ).onFailure {
                binding.output.text =
                    "Doctor failed before execution:\n" +
                    (it.message ?: it.javaClass.simpleName)
            }
        }
    }

    private fun runCommand(raw: String) {
        val parts = runCatching {
            CommandLineParser.parse(raw)
        }.getOrElse {
            binding.output.text = "Parse error: ${it.message}"
            return
        }

        if (parts.isEmpty()) return

        val prefs = requireContext()
            .getSharedPreferences("zipbug.settings", 0)

        val home = prefs.getString(
            "termuxHome",
            "/data/data/com.termux/files/home"
        )!!

        val projectRoot = prefs.getString(
            "projectRoot",
            "$home/OpenDots/Zip_Bug"
        )!!

        val request = when (parts[0]) {
            "/termux" -> {
                if (parts.size < 2) {
                    binding.output.text =
                        "Use /termux <approved-tool> [args]"
                    return
                }

                TermuxBridge.Request(
                    tool = parts[1],
                    args = parts.drop(2),
                    workDir = home,
                    label = "Zip_Bug /termux"
                )
            }

            "/apkbuilder" -> {
                val workDir = parts.getOrNull(1)
                    ?.takeIf { it.startsWith("/") }
                    ?: projectRoot

                TermuxBridge.Request(
                    tool = "gradle",
                    args = listOf(
                        "--no-daemon",
                        "assembleDebug"
                    ),
                    workDir = workDir,
                    label = "Zip_Bug APK Builder"
                )
            }

            else -> {
                binding.output.text =
                    "Supported now: /termux, /apkbuilder\n" +
                    "Planned: /apcreator /likefigma /aizipper /aicreator"
                return
            }
        }

        binding.engineState.text = "ENGINE • QUEUING"

        viewLifecycleOwner.lifecycleScope.launch {
            val result = TermuxBridge.send(
                requireContext(),
                request
            )

            result.onSuccess { id ->
                binding.output.text =
                    "Queued real Termux job:\n$id\n\n" +
                    "Waiting for Termux result callback…"
            }.onFailure {
                binding.output.text =
                    "Command failed before execution:\n${it.message}"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
