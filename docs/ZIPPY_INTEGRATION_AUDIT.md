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
| Split into segments | Reconnect | Recap had multi-clip timelines; splitting the selected clip at a source-time point is new in `ZippyCutPlan.splitClip()` |
| Reorder clips | Reconnect | Multi-clip list with ↑/↓ reorder in the new studio (`ZippyCutPlan.moveClip`, selection tracked) |
| Manual subtitles (Myanmar/EN) + SRT | Exists | `SubtitleExporter.cuesToSrt` reused by `ZippyCutPlan.subtitlesToSrt` |
| Title overlay | Reconnect | drawtext when a system font exists, otherwise container metadata — in `ZippyCutPlan.buildFfmpegArgs` |
| Text overlay (bottom) | Reconnect | New: second drawtext line (`y=h-64`); fails loudly without a font instead of being silently dropped |
| Logo overlay | Reconnect | New: logo image picked + staged like other media, applied as an extra ffmpeg input with `overlay=16:16` in the plan |
| Audio keep/mute | Reconnect | New in the plan builder (`concat` with/without audio) |
| Audio replace with selected file | Reconnect | New: replacement audio staged, added as an extra input, trimmed to the total output duration (`atrim`) |
| Blur / overlay effects | Exists (Recap `boxblur` clip effect) | Not exposed in the new studio's flow in this pass |
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

---

## F. APK size — why the reference is ~75 MB and this one is not

The owner asked for a unified APK of roughly 80 MB **with real components
only — no dummy files, duplicates or padding**. The honest breakdown:

**What makes the reference Zippy APK ~75 MB:** almost all of it is the
bundled in-process FFmpegKit native libraries — roughly **31 MB
(arm64-v8a) + 37 MB (x86_64)** of uncompressed `.so` files (`libavcodec`,
`libavformat`, `libavfilter`, `libavdevice`, `libavutil`, `libswscale`,
`libswresample`, `libffmpegkit`, `libc++_shared`). The actual product
content is small: `assets/index.html` ≈ 1 MB, `voicer_panel.html`,
`zippy_chat.json` and 33 poster images ≈ a few MB, plus the DEX/resources.

**What the unified ZIP_BUG APK contains instead:** ZIP_BUG's architecture
deliberately ships **no native ffmpeg** — rendering runs in Termux's system
ffmpeg through `TermuxBridge`. The unified APK is therefore the ZIP_BUG app
itself (Kotlin DEX, resources, studio WebView assets) plus this integration's
new code (one Activity, one layout, one pure-Kotlin plan module — kilobytes).
A realistic debug APK size is roughly **8–20 MB**, and that is the correct,
honest size for this architecture.

**Why we do NOT copy Zippy's `.so` files in:** they would only be callable
through Zippy's own JNI/Java bridge classes (`FfmpegRenderer` etc.), which
belong to the reference app's render pipeline, not ZIP_BUG's. Bundling
~68 MB of native libraries that no code in this app invokes is exactly the
dead weight / padding the owner forbade. If in-app (non-Termux) rendering is
ever wanted, the legitimate route is a dependency that is actually wired —
e.g. androidx.media3 Transformer — added via Gradle and used by the studio,
not copied binaries.

**Media3 evaluation (2026-10-28):** androidx.media3 Transformer/Effect was
evaluated as the in-app export route. It cannot be adopted in this sandbox:
Gradle itself cannot run here (the daemon protocol fails before any
dependency resolution — see the build notes in the task report), so the
dependencies cannot be resolved, compiled, or tested. Adopting an
unverifiable dependency would violate the "only label PASS what was
executed" rule. The studio therefore keeps the Termux export path, which is
ZIP_BUG's real, existing engine; the media3 route remains a documented
option for a future pass on a machine where Gradle runs.

---

## G. UI Redesign + Editor Correctness Pass (2026-10-28, v3)

### G.1 Design tokens

Canonical Zippy tokens now exist as named resources, identical in
`values/colors.xml` and `values-night/colors.xml`:

| Token | Value | Use |
|---|---|---|
| `zippy_bg` | `#090C0F` | screen background |
| `zippy_panel` | `#11171C` | panel / card |
| `zippy_panel_raised` | `#2B3640` | buttons / raised rows |
| `zippy_accent` | `#D97757` | primary accent (coral) |
| `zippy_accent2` | `#FF9F43` | secondary accent (orange) |
| `zippy_warn` | `#FFB347` | status / warning text |
| `zippy_text` | `#EEF3F6` | primary text on dark |
| `zippy_muted` | `#8D99A5` | secondary text on dark |

Night alignment: `values-night` `z_bg` changed `#0D0E12 → #090C0F` and
`z_surface` `#16181F → #11171C`, so every screen that already references the
legacy `z_*` tokens (chat, home, sheets) renders on the canonical
background/panel at night (the app's default theme mode is dark —
`LocaleThemeHelper` defaults to `THEME_DARK`). No token was renamed;
existing references keep working.

**Surfaces aligned in this pass**
- Zippy Cut Studio layout: every hardcoded hex replaced with
  `@color/zippy_*` references; the Activity's two dynamic text colours now
  use `getColor(R.color.zippy_*)` too.
- Chat / Home / bottom sheets / all `z_*`-based screens: aligned at night
  via the `z_bg`/`z_surface` value alignment above.
- MainActivity manifest entry now declares
  `windowSoftInputMode="adjustResize"` (it had none) so the chat composer
  is not covered by the keyboard; the studio already had `adjustResize`.

**Legacy scope (honest)**: day (light) `z_*` values are untouched, and
screens with their own programmatic styling (e.g. WebView studios under
`assets/`, drama/recap custom views) were not restyled in this pass.

### G.2 Chat-first verification (code evidence, no behaviour change needed)

- **Opens into AI Chat**: `MainActivity` is the LAUNCHER activity and its
  layout is the chat screen itself (message RecyclerView + composer +
  drawer sidebar) — `MainActivity.onCreate` sets up chat directly.
- **Header provider/model pill**: `updateModelPillLabel()` renders from the
  real `currentProvider` state, which is updated on session select
  (MainActivity ~L117-121), on initial session load (~L279-281) and in the
  model picker (~L575+). The pill shows a per-provider display label
  (e.g. "Gemini 2.5 Flash") rather than the raw model id string — wired to
  real state, left as-is.
- **Suggestion chips**: `setupEmptyStateSuggestions()` (~L232-244) sets the
  composer text from the chip's prompt string and calls `requestFocus()` —
  chips fill the composer, they do not auto-send. ✔ as required.
- **Message copy**: `ChatAdapter` already provides `btnCopyMessage` and
  `btnCopyCode` actions backed by `copyToClipboard()` with a confirmation
  toast (~L101-114, L149+). ✔ present, unchanged.
- No provider logic was touched; no-key/network states keep their existing
  honest status messages.

### G.3 Editor correctness: output aspect + no-audio clips

**Aspect (`ZippyCutPlan.OutputAspect`)**: `LANDSCAPE_16_9` (1280×720) and
`PORTRAIT_9_16` (720×1280). `buildFfmpegArgs` now scales/pads to the
selected canvas (`scale=<W>:<H>:force_original_aspect_ratio=decrease,
pad=<W>:<H>:...`). Logo stays at `overlay=16:16` (inside both canvases);
the bottom text overlay keeps the relative `y=h-64`. The studio has a
16:9 / 9:16 toggle in the preview panel that updates the plan state and
resizes the preview frame (220dp-high full-width for landscape; 360dp-high,
9:16-width, centred for portrait).

**No-audio clips (`Clip.hasAudio`)**: detected at import in the studio via
`MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO` (absent metadata defaults
to `true`, preserving legacy behaviour). In KEEP mode the filter graph no
longer references `[i:a]` for a silent clip; it synthesizes a matching
silent segment instead:
`anullsrc=channel_layout=stereo:sample_rate=44100,atrim=0:<dur>,asetpts=PTS-STARTPTS[a<i>]`,
so `concat` still receives exactly one audio segment per clip and mixed
timelines (some clips with, some without audio) render. REPLACE mode is
unaffected (uses the replacement track); MUTE mode never references clip
audio. Clip rows and the metadata line show a "no audio" marker.

**Draft schema v3**: stores `aspect` and per-clip `hasAudio`.
`fromJson` migrates v2 drafts (missing fields default to
`LANDSCAPE_16_9` / `hasAudio=true`) and the v1 single-source chain
(including split preservation) still works.

### G.4 Tests (all executed 2026-10-28)

Standalone JVM (kotlinc 2.0.21 + JUnit 4.13.2, the established method —
Gradle cannot run in the sandbox):

- `ZippyCutPlanTest`: **OK (24 tests)** — the previous 16 plus 8 new:
  portrait canvas (720:1280 present, 1280:720 absent), landscape canvas
  unchanged, silent-clip `anullsrc` synthesis with no `[0:a]` reference,
  mixed-timeline graph validity (labels defined exactly once,
  `concat=n=2:v=1:a=1`), silent+MUTE, REPLACE with silent clips (uses the
  replacement input only), JSON v3 round-trip, v2→v3 migration defaults.
  No first-run failures in this pass.

**Real ffmpeg render smoke test** (`scripts/render-smoke/`, harness only —
not app code): synthetic lavfi media (clip A = video+audio, clip B =
video with NO audio stream, portrait source P, generated music track) was
rendered with the plan builder's actual argument lists on ffmpeg 8.1.2 and
verified with ffprobe. **8/8 PASS**:

| Case | Result (ffprobe evidence) |
|---|---|
| audio-only, landscape | 1280×720, audio stream, 3.0s |
| silent-only, portrait | 720×1280, audio (synthesized silence), 3.0s |
| mixed A+B, portrait | 720×1280, audio, 6.0s |
| mixed A+B, landscape | 1280×720, audio, 6.0s |
| mixed + REPLACE | 1280×720, audio, 6.0s |
| mixed + MUTE, portrait | 720×1280, NO audio stream, 6.0s |
| portrait source → landscape | 1280×720, audio, 3.0s |
| mixed + title/text drawtext, portrait (DejaVuSans) | 720×1280, audio, 6.0s |

Reproduce with `scripts/render-smoke/run-render-smoke.sh` (needs JDK,
kotlinc, ffmpeg; JUnit + org.json are fetched/located by the script).

**Gradle `:app:assembleDebug`: BLOCKED** in this sandbox (attempted once
this pass): `Could not receive a message from the daemon.` — the known VM
daemon-protocol failure (`MessageIOException … Unexpected type tag 109`
in the full trace). The APK is built by the owner on Termux (below), as
before; the previous v2 code built successfully there in 4 minutes.

### G.5 Owner rebuild (Termux)

```bash
cd ~/zipbug-unified
git pull   # branch integration/zippy-zipbug-unified-20261028
export JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
printf 'sdk.dir=%s\n' "$HOME/android-sdk" > local.properties
bash scripts/build-termux.sh --debug --timeout-seconds 3600
```

Output: `Download/Zip_Bug-debug.apk`. Signing note (unchanged): the Termux
debug certificate differs from alpha04's, so this APK cannot update-install
over alpha04 and alpha04 must not be uninstalled / its data cleared; test
side-by-side only via a different package id, which starts with empty data.
