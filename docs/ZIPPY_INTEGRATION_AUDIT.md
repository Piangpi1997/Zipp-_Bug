# ZIPPY INTEGRATION AUDIT — Zippy Cut UI × ZIP_BUG

Date: 2026-10-28 · Branch: `integration/zippy-zipbug-unified-20261028` ·
Starting HEAD: `f4a5354` · Backup before changes:
`~/workspace/zipbug-backups/zipbug-f4a5354-before-zippy-integration.zip`

Reference APK: `Zippy_Cut_Clone_1.0.2.apk` (package `com.zippy.cut.clone`,
versionName 1.0.2 / versionCode 63, minSdk 26, targetSdk 35). Its UI lives in
`assets/index.html` (~968 KB, single-file WebView app) plus
`assets/voicer_panel.html`, `assets/zippy_chat.json` and 33 poster-design
images in `assets/poster_designs/`.

---

## A. Zippy Cut UI inventory (from the reference APK)

### Screens / surfaces

| Surface | What it is |
|---|---|
| Startup / intro | Branding splash (`zippy_intro` video/images, seasonal variants), then a LOGIN KEY card in the original build (license gate — removed in the owner's clone, and not applicable to ZIP_BUG) |
| Home (`ZippyHome`) | Series/project cards, episode list, "continue" cards, a Zippy_Cut_Pro join button, chat card entry |
| Main editor | Top bar (58 px), video preview, per-second audio-dot strip (minutes × slots), video clip grid, wire/connection canvas (SVG overlays linking audio slots to clips), lock/pack workflow buttons, flow panel |
| Focus overlay (`#focusOverlay`, "SIMPLE MODE") | Full-screen simplified editor: audio dots grid (6 across), clip grid, lock/pack, preview |
| Flow panels | Story flow and Dub flow artwork (`flow_story.webp`, `flow_dub.webp`), step-by-step panels |
| Zippy Chat | Chat-first assistant driven by `zippy_chat.json` intents (cut ranges, script intake, voice picking) |
| Voicer panel | Separate WebView page (`voicer_panel.html`): split text, MP3 parts, combine |
| Poster studio | 33 built-in poster design prompts/images, master-prompt copy/share to external AI apps |
| Menu extras | "Make money" sheet (monetisation services) and an AI-tool premium shop — marketing content with placeholder links |
| TTS screens (native) | Edge TTS + Azure TTS activities, voice picker (Nilar / Thiha etc.) |

### Look and feel

- Default theme tokens (`:root`): background `#090c0f`, panels `#11171c` /
  `#182027`, borders `#2b3640`, text `#eef3f6`, muted `#8d99a5`,
  **accent (coral) `#d97757`**, orange `#ff9f43`, cyan `#59e7ff`,
  purple `#8a7dff`. Alternative `glass` (teal/green) and `gold` themes exist.
- The login card in later builds uses a warm charcoal palette
  (`#262624` background, `#30302e` card, same `#d97757` button).
- Dense, timeline-first layout: preview on top, timeline strip under it,
  tool panels below; heavy use of small caps section labels and emoji icons.
- Myanmar + English bilingual labels throughout (`L(my, en)` helper).

### Timeline / editor controls (WebView side)

Import/pick video, per-slot audio linking, clip suggestions/auto-fill,
trim/cut ranges, split at playhead-style points, lock finished slots,
pack (render finished part), transitions/FX markers (REVERSE / ZOOM / FLIP /
FREEZE), subtitle/dialogue boxes, title/poster generation prompts,
series episodes (parts), part merge, export/share, undo snapshots.

### Native dependencies of the reference APK

- Native libraries: FFmpegKit (`libffmpegkit.so`, `libavcodec/format/filter/
  device/util`, `libswscale/swresample`, `libc++_shared`) for arm64-v8a and
  x86_64 — **rendering happens in-process, not via an external engine**.
- Permissions: INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK,
  FOREGROUND_SERVICE (+ DATA_SYNC / MEDIA_PROCESSING), POST_NOTIFICATIONS,
  WRITE_EXTERNAL_STORAGE (≤ API 28).
- A foreground `RenderService`, FileProvider, and several bridge classes
  (MainActivity bridge, SeriesBridge, Tts/Voicer bridges, SchoolBridge,
  CleanBridge).

---

## B. Android bridge methods the Zippy UI calls — grouped

The WebView calls ≈ 80 native methods (`AndroidBridge.*` on the main page,
`SN.*` = SeriesBridge, plus TTS/Voicer bridge calls).

### B1. Can be served by ZIP_BUG engines (an analog exists)

| Zippy method(s) | ZIP_BUG analog |
|---|---|
| `pickVideo`, `pickVideos`, `probe`, `videoFps`, `deviceInfo`, `batteryState` | System document picker + `MediaMetadataRetriever` |
| `startRender`, `cancelRender`, `renderModes`, ` setCutRange`, `cutPartTemp`, `savePartSpeed`, `sharePartSpeed` | Termux ffmpeg job pipeline (`TermuxBridge` + Room `BuildJob` states) — argument plans must be rebuilt in ZIP_BUG's plan format, the Zippy JSON plan is not compatible |
| `saveOutput`, `openOutput`, `shareOutput`, `listSavedVideos`, `galleryThumbs`, `openSaved`, `shareSaved`, `renameSaved`, `deleteSaved`, `saveEpisode`, `saveEpisodeVideo`, `episodeOutput(Path)`, `keepOutput` | MediaStore / shared Downloads + `FileProvider` share & view intents |
| `mergeStart`, `mergeSave(Path)`, `mergeCancel`, `SN.list/getMeta/putMeta/deleteSeries/forget/activate/adoptVideo/fileUrl/seriesBytes/saveImage` | ZIP_BUG Recap projects/clips persistence (Room) + Termux concat renders — semantics differ, needs a translation layer, not a drop-in |
| `saveProjectState`, `loadBootstrap`, `stateJson`, `newProject`, `clearAllProjectData`, `getClearableBytes` | JSON files in app-private storage / Room |
| `copyText`, `readClipboard`, `haptic`, `openExternal`, `saveOverlayImage`, `clearOverlayImages` | Android clipboard / vibrator / intents / files |
| `analyzeAudioSilence` | Possible as a new Termux ffmpeg `silencedetect` plan (not built yet) |
| `savePosterFrames`, `sharePoster`, `shareMasterPrompt` | Frame extraction via Termux ffmpeg + share intents (prompt text sharing already trivially possible) |
| `ttsVoices`, `ttsStart`, `ttsState`, `ttsStop`, `ttsUse`, `openVoicer`, `openUnlimitedTts`, `openVoicerSide`, `voicerParts` | ZIP_BUG `TtsStudioActivity` / `EdgeTtsHelper` (Edge-TTS via Termux) — voice catalogue and panel flow differ |
| `saveChatImage`, `saveChatText`, `shareChatFile`, `chatSafeName` | ZIP_BUG chat (MainActivity) file helpers |
| `tapSeen`, `takeTapNote`, `bootMark`, `bootPending`, `takeRecoveryNote`, `systemMode`, `setSystemMode`, `appVersion` | SharedPreferences / internal log files |

### B2. Would need Zippy's in-process native ffmpeg stack

| Zippy component | Why ZIP_BUG cannot serve it 1:1 |
|---|---|
| `FfmpegRenderer` / `RenderService` / `WatchedFfmpeg` (the actual render engine behind `startRender`) | ZIP_BUG deliberately ships **no native ffmpeg**; its engine is Termux. Same jobs, different execution path and progress model |
| `requestFocusProxy` (fast low-res proxy video of the source for the Focus UI) | Same reason — generatable via Termux ffmpeg, but not as the synchronous in-process call the WebView expects |
| Real-time preview proxying / frame-accurate WebView playback sync | Depends on the native proxy + WebView media plumbing above |

### B3. Not applicable to ZIP_BUG

| Method / surface | Reason |
|---|---|
| `openKeyLogin`, trial push (`ZippyTrial.*`) | ZIP_BUG has no license gate; the owner's clone removed it from Zippy too |
| "Make money" / premium-shop sheets | Placeholder marketing content with example links |
| Zippy videos feed (`fetchVideos`, `videosCache`) | A Google Apps Script content feed specific to Zippy's channel; no ZIP_BUG counterpart content source |

---

## C. ZIP_BUG feature inventory (this branch) and launch points

| Feature | Entry point |
|---|---|
| Chat-First AI chat, provider/model picker, chat history | `MainActivity` (launcher activity), drawer history, model bottom sheet; providers configured in `SettingsActivity`, keys in `SecretStore` |
| Workspace / Projects / source editor / ZIP import-export | `WorkspaceActivity` (Home card, drawer, Projects), `ProjectsFragment`, `ProjectWorkspaceManager`, `ZipImporter` |
| App Builder | `AppBuilderActivity` (drawer, tools sheet, Home) |
| App Creator (+ React/Web scaffolders) | `AppCreatorActivity`, `AiModeActivity`, Home hero cards |
| APK Builder | Terminal command `/apkbuilder` → `ApkBuildCoordinator` (Termux Gradle) |
| Visual / Advanced / LikeFigma Studio | `StudioActivity` variants + `StudioFragment` WebView assets (`studio/`, `studio_advanced/`, `studio_likefigma/`), chosen in `MainActivity.showStudioChoice()` |
| Terminal / Termux integration | `TerminalActivity`, `TerminalFragment`, `TermuxBridge` (allow-listed tools incl. ffmpeg) |
| Video & Recap Studio | `RecapStudioActivity` (video choice dialog, Home search metadata), `MediaRecapActivity` (pipeline screen from Terminal) |
| Myanmar Drama Editor | `MyanmarDramaEditorActivity` (video choice dialog) |
| Audio / TTS tools | `TtsStudioActivity` (Home card, drawer), `EdgeTtsHelper` |
| Settings, language, theme, developer tools | `SettingsActivity`, `AppLanguage` / `LocaleThemeHelper`, `DeveloperToolsActivity`, `DeviceVerificationActivity`, `SigningDiagnosticsActivity`, `CommandHelpActivity` |
| Video render stack | `recap/RecapRenderCoordinator` + `RecapTimelineManager` + `SubtitleExporter` + `drama/MyanmarDramaRenderCoordinator` — all build ffmpeg argument lists and execute **via Termux**; Room entities in `data/recap/` |

All of the above entries still exist after this integration; the new studio
was **added** (see §E), nothing was removed.

---

## D. Gap matrix — Zippy video editor vs ZIP_BUG

| Capability | Status | Where |
|---|---|---|
| Video import (picker) + real metadata | Exists | Recap/Drama already; new studio reuses `RecapMediaStager` |
| Preview play/pause/seek | Exists | `VideoView` in Drama editor; new studio adds its own |
| Trim | Exists | Recap clips (start/end per clip) |
| Split into segments | Reconnect | Recap had multi-clip timelines; a single-source split point is new in `ZippyCutPlan.segments()` |
| Reorder clips | Exists (Recap multi-clip) | Not in the new single-source studio (documented scope) |
| Manual subtitles (Myanmar/EN) + SRT | Exists | `SubtitleExporter.cuesToSrt` reused by `ZippyCutPlan.subtitlesToSrt` |
| Title overlay | Reconnect | New: drawtext when a system font exists, otherwise container metadata — both in `ZippyCutPlan.buildFfmpegArgs` |
| Logo overlay | Missing in new studio | Exists partially in Drama editor (logo picker); not ported in this pass |
| Audio keep/mute | Reconnect | New in the plan builder (`concat` with/without audio) |
| Blur / overlay effects | Exists (Recap `boxblur` clip effect) | Not exposed in the new studio's single-source flow in this pass |
| Real render/export | Exists | Termux ffmpeg via `TermuxBridge` (Recap path) |
| Output preview / save / share | Exists | FileProvider + public Downloads output, as in Recap |
| Draft restore (edit state, original media untouched) | Reconnect | New: JSON draft in app-private storage (`zippycut/draft.json`) |
| Auto Caption (transcription) | Missing — needs a provider/key | Shown **disabled with status** in the new studio; manual subtitles fully work |
| WebView wire-canvas audio↔clip linking, focus proxy, series episode engine | Missing (Zippy-native concepts) | Would need the B2 native stack or a large translation layer; not faked |

---

## E. Chosen approach and why

**Not chosen:** embedding Zippy's `assets/index.html` into ZIP_BUG. The page
calls ≈ 80 bridge methods whose render core (`startRender`, focus proxies,
series store) is Zippy's in-process FFmpegKit renderer. ZIP_BUG has no
native ffmpeg and no such bridge, so the embedded page would be exactly the
"buttons that do nothing" demo the owner forbade.

**Chosen:** rebuild the studio **natively in ZIP_BUG**, in Zippy's visual
language (near-black `#090C0F` background, `#11171C` panels, coral `#D97757`
primary buttons, orange `#FF9F43` section labels, muted `#8D99A5` secondary
text — the reference's own tokens), wired to ZIP_BUG's real engines:

- `ui/ZippyCutStudioActivity.kt` + `res/layout/activity_zippy_cut_studio.xml`
- `zippy/ZippyCutPlan.kt` — pure, unit-tested edit state, validation,
  trim/split segments, SRT, ffmpeg plan, draft JSON
- Entry points **added**: Home's existing "Zippy Cut" card now opens the new
  studio (Recap Studio remains reachable from the video-tools dialog and
  tool search), and the video-tools dialog in `MainActivity` lists
  "Zippy Cut Studio" first, ahead of the unchanged Recap/Drama entries.
- Export status is the real Room/Termux job status
  (QUEUED / RUNNING / SUCCESS / FAILED + exit code); no percentage is shown
  for the render because the engine does not report real progress.
- Signing/package note: this branch builds the same applicationId as before
  (`com.zipbug.base` / debug `com.zipbug.base.next`, versionCode 7,
  versionName 2.0.0-alpha04, debug suffix `-zippy-next`). A debug APK built
  outside the original builder's machine carries a **different Android
  debug certificate** and cannot update-install over the owner's alpha04
  APK; that is a signing-identity fact, not data loss — do not uninstall or
  clear data to work around it.
