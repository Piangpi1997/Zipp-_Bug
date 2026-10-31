package com.zipbug.base.ui.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.zipbug.base.R
import com.zipbug.base.ZipBugApp
import com.zipbug.base.databinding.FragmentHomeBinding
import com.zipbug.base.engine.EngineManager
import com.zipbug.base.ui.AppCreatorActivity
import com.zipbug.base.ui.DeveloperToolsActivity
import com.zipbug.base.ui.MainActivity
import com.zipbug.base.ui.RecapStudioActivity
import com.zipbug.base.ui.ZippyCutStudioActivity
import com.zipbug.base.ui.SettingsActivity
import com.zipbug.base.ui.TtsStudioActivity
import com.zipbug.base.ui.WebRuntimeActivity
import com.zipbug.base.ui.WorkspaceActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeFragment : Fragment() {
    companion object {
        private const val ARG_FOCUS_SEARCH = "arg_focus_search"
        private const val PREF_PINNED_TOOLS = "pinned_tools"

        fun newInstance(focusSearch: Boolean = false): HomeFragment =
            HomeFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_FOCUS_SEARCH, focusSearch)
                }
            }
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var latestProjectId: String? = null
    private var activeCategory: String = "ALL"
    private var currentSearchQuery: String = ""

    private data class ToolMeta(
        val id: String,
        val view: View,
        val pinButton: ImageButton?,
        val category: Set<String>,
        val keywords: String,
        val titleRes: Int,
        val onOpen: () -> Unit
    )

    private val toolMetas = mutableListOf<ToolMeta>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val main = requireActivity() as MainActivity

        // Hero Card Actions (App, Website, UI Design)
        binding.btnHeroCreateApp.setOnClickListener {
            startActivity(Intent(requireContext(), AppCreatorActivity::class.java))
        }
        binding.btnHeroCreateWeb.setOnClickListener {
            startActivity(Intent(requireContext(), WorkspaceActivity::class.java))
        }
        binding.btnHeroCreateUi.setOnClickListener {
            main.navigateToStudio()
        }

        // 6 Primary Tool Card Actions
        val openStudio = { main.navigateToStudio() }
        val openAppCreator = { startActivity(Intent(requireContext(), AppCreatorActivity::class.java)) }
        val openReactWeb = { startActivity(Intent(requireContext(), WorkspaceActivity::class.java)) }
        val openApkBuilder = { main.navigateToTerminal("/apkbuilder --debug") }
        val openVideoRecap = { startActivity(Intent(requireContext(), ZippyCutStudioActivity::class.java)) }
        val openAudioVoice = { startActivity(Intent(requireContext(), TtsStudioActivity::class.java)) }

        binding.studio.setOnClickListener { openStudio() }
        binding.appCreator.setOnClickListener { openAppCreator() }
        binding.btnQuickWorkspace.setOnClickListener { openReactWeb() }
        binding.apkBuilder.setOnClickListener { openApkBuilder() }
        binding.zippyCut.setOnClickListener { openVideoRecap() }
        binding.ttsStudio.setOnClickListener { openAudioVoice() }

        // Utilities Tiles
        binding.btnQuickImportZip.setOnClickListener { main.navigateToProjects() }
        binding.btnUtilJson.setOnClickListener {
            startActivity(Intent(requireContext(), DeveloperToolsActivity::class.java))
        }
        binding.btnUtilHash.setOnClickListener {
            startActivity(Intent(requireContext(), DeveloperToolsActivity::class.java))
        }
        binding.btnUtilBackup.setOnClickListener {
            startActivity(Intent(requireContext(), DeveloperToolsActivity::class.java))
        }
        binding.btnQuickPreviewHtml.setOnClickListener {
            launchLatestHtmlPreview()
        }
        binding.btnQuickDevTools.setOnClickListener {
            startActivity(Intent(requireContext(), DeveloperToolsActivity::class.java))
        }

        // Projects & Resume Actions
        binding.projects.setOnClickListener {
            main.navigateToProjects()
        }
        binding.btnResumeWork.setOnClickListener {
            val intent = Intent(requireContext(), WorkspaceActivity::class.java)
            latestProjectId?.let { id ->
                intent.putExtra(WorkspaceActivity.EXTRA_PROJECT_ID, id)
            }
            startActivity(intent)
        }
        binding.btnHomeTestEngine.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        setupToolMetadata(
            openStudio = openStudio,
            openAppCreator = openAppCreator,
            openReactWeb = openReactWeb,
            openApkBuilder = openApkBuilder,
            openVideoRecap = openVideoRecap,
            openAudioVoice = openAudioVoice
        )
        setupCategoryChips()
        setupSearchFilter()
        refreshPinnedFavoritesUi()
        refreshDashboardMetrics()

        if (arguments?.getBoolean(ARG_FOCUS_SEARCH, false) == true) {
            focusToolSearch()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPinnedFavoritesUi()
        refreshDashboardMetrics()
    }

    fun focusToolSearch() {
        val b = _binding ?: return
        b.etToolSearch.requestFocus()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(b.etToolSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setupToolMetadata(
        openStudio: () -> Unit,
        openAppCreator: () -> Unit,
        openReactWeb: () -> Unit,
        openApkBuilder: () -> Unit,
        openVideoRecap: () -> Unit,
        openAudioVoice: () -> Unit
    ) {
        toolMetas.clear()
        toolMetas.addAll(
            listOf(
                ToolMeta(
                    id = "studio",
                    view = binding.studio,
                    pinButton = binding.pinStudio,
                    category = setOf("CREATE"),
                    keywords = "visual studio ui design figma screen layout canvas ဒီဇိုင်း မျက်နှာပြင် ဖန်တီးမှု",
                    titleRes = R.string.tool_visual_studio_title,
                    onOpen = openStudio
                ),
                ToolMeta(
                    id = "app_creator",
                    view = binding.appCreator,
                    pinButton = binding.pinAppCreator,
                    category = setOf("CREATE", "DEVELOP"),
                    keywords = "app creator android kotlin project template ပရောဂျက် အသစ် ဖန်တီးမှု",
                    titleRes = R.string.tool_app_creator_title,
                    onOpen = openAppCreator
                ),
                ToolMeta(
                    id = "react_web",
                    view = binding.btnQuickWorkspace,
                    pinButton = binding.pinReactWeb,
                    category = setOf("DEVELOP"),
                    keywords = "react web vite html css js workspace code editor ကုဒ် ဝက်ဘ်ဆိုဒ်",
                    titleRes = R.string.tool_react_web_title,
                    onOpen = openReactWeb
                ),
                ToolMeta(
                    id = "apk_builder",
                    view = binding.apkBuilder,
                    pinButton = binding.pinApkBuilder,
                    category = setOf("DEVELOP"),
                    keywords = "apk builder android build gradle compile package တည်ဆောက်ရန်",
                    titleRes = R.string.tool_apk_builder_title,
                    onOpen = openApkBuilder
                ),
                ToolMeta(
                    id = "video_recap",
                    view = binding.zippyCut,
                    pinButton = binding.pinVideoRecap,
                    category = setOf("VIDEO", "CREATE"),
                    keywords = "video recap zippy cut movie clip ffmpeg render subtitle ဗီဒီယို ဇာတ်လမ်းအကျဉ်း",
                    titleRes = R.string.tool_video_recap_title,
                    onOpen = openVideoRecap
                ),
                ToolMeta(
                    id = "audio_voice",
                    view = binding.ttsStudio,
                    pinButton = binding.pinAudioVoice,
                    category = setOf("AUDIO", "CREATE"),
                    keywords = "audio voice tts speech myanmar nilar thiha mp3 subtitle အသံ စကားပြော စာတန်းထိုး",
                    titleRes = R.string.tool_audio_voice_title,
                    onOpen = openAudioVoice
                )
            )
        )

        toolMetas.forEach { meta ->
            meta.pinButton?.setOnClickListener {
                togglePinnedTool(meta.id)
            }
        }
    }

    private fun getPinnedSet(): MutableSet<String> {
        val prefs = requireContext().getSharedPreferences("zipbug.settings", Context.MODE_PRIVATE)
        val raw = prefs.getString(PREF_PINNED_TOOLS, "studio,react_web") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
    }

    private fun togglePinnedTool(toolId: String) {
        val prefs = requireContext().getSharedPreferences("zipbug.settings", Context.MODE_PRIVATE)
        val set = getPinnedSet()
        if (set.contains(toolId)) {
            set.remove(toolId)
        } else {
            set.add(toolId)
        }
        prefs.edit().putString(PREF_PINNED_TOOLS, set.joinToString(",")).apply()
        refreshPinnedFavoritesUi()
    }

    private fun refreshPinnedFavoritesUi() {
        val b = _binding ?: return
        val ctx = context ?: return
        val pinned = getPinnedSet()

        val orangeColor = ContextCompat.getColor(ctx, R.color.z_orange)
        val mutedColor = ContextCompat.getColor(ctx, R.color.z_muted)

        toolMetas.forEach { meta ->
            val isPinned = pinned.contains(meta.id)
            meta.pinButton?.setColorFilter(if (isPinned) orangeColor else mutedColor)
        }

        b.containerPinnedTools.removeAllViews()
        val pinnedMetas = toolMetas.filter { pinned.contains(it.id) }
        if (pinnedMetas.isEmpty()) {
            b.sectionPinnedFavorites.visibility = View.GONE
            return
        }

        b.sectionPinnedFavorites.visibility = View.VISIBLE
        val density = resources.displayMetrics.density
        pinnedMetas.forEachIndexed { idx, meta ->
            val pill = TextView(ctx).apply {
                text = "★ " + getString(meta.titleRes)
                setTextColor(ContextCompat.getColor(ctx, R.color.z_text))
                textSize = 12f
                setBackgroundResource(R.drawable.bg_status_pill)
                val padH = (14 * density).toInt()
                val padV = (7 * density).toInt()
                setPadding(padH, padV, padH, padV)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (idx > 0) marginStart = (8 * density).toInt()
                }
                setOnClickListener { meta.onOpen() }
            }
            b.containerPinnedTools.addView(pill)
        }
    }

    private fun setupCategoryChips() {
        val chips = mapOf(
            "ALL" to binding.chipCatAll,
            "CREATE" to binding.chipCatCreate,
            "DEVELOP" to binding.chipCatDevelop,
            "VIDEO" to binding.chipCatVideo,
            "AUDIO" to binding.chipCatAudio,
            "UTILITIES" to binding.chipCatUtilities
        )

        fun updateChipSelection(selectedKey: String) {
            activeCategory = selectedKey
            val ctx = context ?: return
            chips.forEach { (key, chip) ->
                if (key == selectedKey) {
                    chip.setBackgroundResource(R.drawable.bg_chip_active)
                    chip.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_inactive)
                    chip.setTextColor(ContextCompat.getColor(ctx, R.color.z_text))
                }
            }
            applyFilter()
        }

        chips.forEach { (key, chip) ->
            chip.setOnClickListener { updateChipSelection(key) }
        }
    }

    private fun setupSearchFilter() {
        binding.btnClearToolSearch.setOnClickListener {
            binding.etToolSearch.setText("")
        }
        binding.etToolSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim()?.lowercase().orEmpty()
                binding.btnClearToolSearch.visibility =
                    if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun applyFilter() {
        val b = _binding ?: return
        val q = currentSearchQuery

        // Hero card visible when ALL or CREATE and not searching
        b.cardCreateHero.visibility =
            if (q.isEmpty() && (activeCategory == "ALL" || activeCategory == "CREATE")) View.VISIBLE else View.GONE

        // Filter primary tool cards
        var anyPrimaryVisible = false
        toolMetas.forEach { meta ->
            val catMatch = activeCategory == "ALL" || meta.category.contains(activeCategory)
            val textMatch = q.isEmpty() ||
                meta.keywords.contains(q) ||
                getString(meta.titleRes).lowercase().contains(q)
            val visible = catMatch && textMatch
            meta.view.visibility = if (visible) View.VISIBLE else View.GONE
            if (visible) anyPrimaryVisible = true
        }

        b.rowTools1.visibility =
            if (b.studio.visibility == View.VISIBLE || b.appCreator.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        b.rowTools2.visibility =
            if (b.btnQuickWorkspace.visibility == View.VISIBLE || b.apkBuilder.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        b.rowTools3.visibility =
            if (b.zippyCut.visibility == View.VISIBLE || b.ttsStudio.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        b.tvPrimaryToolsHeader.visibility = if (anyPrimaryVisible) View.VISIBLE else View.GONE

        // Utilities section visibility
        val utilKeywords = "zip json hash sha256 backup restore html preview utility utilities စစ်ဆေး ဖိုင် ကိရိယာ"
        val utilMatch = (activeCategory == "ALL" || activeCategory == "UTILITIES" || activeCategory == "DEVELOP") &&
            (q.isEmpty() || utilKeywords.contains(q))
        b.sectionUtilitiesGrid.visibility = if (utilMatch) View.VISIBLE else View.GONE
    }

    private fun refreshDashboardMetrics() {
        val ctx = context ?: return
        val app = requireActivity().application as ZipBugApp
        val db = app.database

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val allProjects = db.projectDao().listAll()
            val recapProjects = db.recapProjectDao().getAllSync()
            val buildJobs = db.buildJobDao().listAll()

            val totalProjects = allProjects.size + recapProjects.size
            val latestProj = allProjects.firstOrNull()
            latestProjectId = latestProj?.id

            val resumeSummary = when {
                latestProj != null ->
                    "${latestProj.name} • ${latestProj.kind.uppercase()} (${latestProj.entryFile})"
                recapProjects.isNotEmpty() ->
                    "${recapProjects.first().name} • VIDEO RECAP"
                else ->
                    ctx.getString(R.string.home_no_recent_work)
            }

            withContext(Dispatchers.Main) {
                val b = _binding ?: return@withContext
                b.tvStatProjects.text = "$totalProjects ${ctx.getString(R.string.home_projects_count_label)}"
                b.tvStatJobs.text = buildJobs.size.toString()
                b.tvResumeSummary.text = resumeSummary

                // Populate compact preview cards for up to 3 recent projects
                b.containerRecentProjects.removeAllViews()
                val density = resources.displayMetrics.density
                allProjects.take(3).forEach { proj ->
                    val card = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundResource(R.drawable.bg_glass_card)
                        val pad = (12 * density).toInt()
                        setPadding(pad, pad, pad, pad)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = (8 * density).toInt()
                        }
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            startActivity(
                                Intent(ctx, WorkspaceActivity::class.java)
                                    .putExtra(WorkspaceActivity.EXTRA_PROJECT_ID, proj.id)
                            )
                        }
                    }

                    val badge = TextView(ctx).apply {
                        text = proj.kind.take(5).uppercase()
                        setTextColor(ContextCompat.getColor(ctx, R.color.z_orange))
                        textSize = 10f
                        setBackgroundResource(R.drawable.bg_icon_badge_orange)
                        val pH = (8 * density).toInt()
                        val pV = (4 * density).toInt()
                        setPadding(pH, pV, pH, pV)
                    }

                    val textCol = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = (10 * density).toInt()
                        }
                    }
                    val titleView = TextView(ctx).apply {
                        text = proj.name
                        setTextColor(ContextCompat.getColor(ctx, R.color.z_text))
                        textSize = 14f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        maxLines = 1
                    }
                    val subView = TextView(ctx).apply {
                        text = "Entry: ${proj.entryFile}"
                        setTextColor(ContextCompat.getColor(ctx, R.color.z_muted))
                        textSize = 11f
                        maxLines = 1
                    }
                    textCol.addView(titleView)
                    textCol.addView(subView)

                    val chev = ImageView(ctx).apply {
                        setImageResource(R.drawable.ic_chevron_right)
                        setColorFilter(ContextCompat.getColor(ctx, R.color.z_muted))
                        layoutParams = LinearLayout.LayoutParams((18 * density).toInt(), (18 * density).toInt())
                    }

                    card.addView(badge)
                    card.addView(textCol)
                    card.addView(chev)
                    b.containerRecentProjects.addView(card)
                }
            }
        }
    }

    private fun launchLatestHtmlPreview() {
        val ctx = context ?: return
        val app = requireActivity().application as ZipBugApp
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val webProject = app.database.projectDao().listAll().firstOrNull { p ->
                p.entryFile.endsWith(".html", ignoreCase = true) ||
                    File(p.rootPath, "www/index.html").isFile ||
                    File(p.rootPath, "index.html").isFile
            }
            withContext(Dispatchers.Main) {
                if (webProject == null) {
                    startActivity(Intent(ctx, WorkspaceActivity::class.java))
                    return@withContext
                }
                runCatching {
                    val root = File(webProject.rootPath).canonicalFile
                    val entry = when {
                        File(root, webProject.entryFile).isFile -> File(root, webProject.entryFile).canonicalFile
                        File(root, "www/index.html").isFile -> File(root, "www/index.html").canonicalFile
                        else -> File(root, "index.html").canonicalFile
                    }
                    EngineManager.run(entry.parentFile ?: root)
                    startActivity(
                        Intent(ctx, WebRuntimeActivity::class.java)
                            .putExtra("url", "http://127.0.0.1:3131/${entry.name}")
                    )
                }.onFailure { err ->
                    Snackbar.make(binding.root, "Preview error: ${err.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
