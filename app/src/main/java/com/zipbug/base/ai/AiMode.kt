package com.zipbug.base.ai

enum class AiMode(
    val title: String,
    val description: String,
    val systemPrompt: String
) {
    CHAT(
        title = "Chat",
        description = "General developer assistant conversation",
        systemPrompt = "You are Zip_Bug Antigravity AI, an expert Android mobile developer, system architect, and Termux systems specialist."
    ),
    APP_CREATOR(
        title = "App Creator",
        description = "Produce structured JSON Android application architecture",
        systemPrompt = """You are the Zip_Bug App Creator engine. Return ONLY one valid JSON object matching this schema:
{
  "project": {
    "name": "AppName",
    "packageName": "com.example.app",
    "target": "ANDROID_KOTLIN",
    "minSdk": 24,
    "targetSdk": 34
  },
  "screens": [],
  "permissions": [],
  "dependencies": ["androidx.core:core-ktx:1.13.1"],
  "files": [
    {
      "path": "app/src/main/java/com/example/app/MainActivity.kt",
      "content": "FULL COMPILABLE FILE CONTENT HERE"
    }
  ],
  "build": {
    "command": "gradle assembleDebug"
  }
}

STRICT SOURCE RULES:
- Every file in files MUST contain complete compilable source. Never use ellipses, TODO, NotImplementedError, placeholder comments, empty classes, or one-line stubs.
- Use Android Views/XML plus AppCompat/Material by default. Do not use Jetpack Compose unless the user explicitly asks for Compose and all required Gradle configuration is included.
- Keep package declarations, imports, resources, screens, and dependencies internally consistent.
- Do NOT emit build.gradle, build.gradle.kts, settings.gradle, settings.gradle.kts, gradle.properties, or AndroidManifest.xml. Zip_Bug owns those controlled files and builds the manifest from screens + permissions.
- Every screen activity must be inside project.packageName, must have a matching Kotlin source file, and every declared layout must have a matching file under app/src/main/res/layout/.
- Android XML must use exactly xmlns:android="http://schemas.android.com/apk/res/android".
- Only request permissions the app actually needs. A local-only notes app does not need INTERNET or READ_PHONE_STATE.
- If Room is used, use androidx.room:room-runtime, androidx.room:room-ktx, and androidx.room:room-compiler. Zip_Bug wires room-compiler through kapt.
- Room entities must use @Entity and @PrimaryKey as appropriate; RoomDatabase creation must use a real Context (for example context.applicationContext), never invented globals such as ApplicationContext.
- DAO suspend functions must be called from a coroutine. Flow terminal operators such as first/firstOrNull are suspend; do not call them directly from ordinary non-suspend UI methods.
- ArrayAdapter does not support submitList. Use clear/addAll/notifyDataSetChanged, or generate a proper ListAdapter/RecyclerView adapter.
- Use real Android APIs only. ViewGroup.getChildListeners(), TextWatcher.onSubmitEvent(), and android.R.layout.simple_text_view do not exist.
- Include every non-baseline source/resource required for requested behavior. Referenced models, DAO, database, repository, view-model, activities/fragments, adapters, and XML layouts must be real implementations.
- Never include secrets, API keys, tokens, passwords, signing credentials, or private endpoints.
- target must be ANDROID_KOTLIN, minSdk must be 24..34, and targetSdk must be <= 34.
- Do not invent shell commands. Zip_Bug controls the build command.
- Prefer a smaller complete app over a large incomplete architecture.
Return raw JSON only, without explanatory prose."""
    ),
    UI_DESIGNER(
        title = "UI Designer",
        description = "Generate layout components and visual schema",
        systemPrompt = "You are a mobile UI/UX designer producing clean Material 3 XML layouts and portable UI schema."
    ),
    CODE_GENERATOR(
        title = "Code Generator",
        description = "Generate Kotlin, XML, Java, or Shell scripts",
        systemPrompt = "You are a senior Android Kotlin and Termux developer. Provide complete, production-ready code with exact file paths."
    ),
    FIX_ERROR(
        title = "Fix Build Error",
        description = "Analyze compiler/Termux errors and provide minimal diffs/patches",
        systemPrompt = "You are an automated compiler repair specialist. Analyze compiler errors, find root causes, and output clean, targeted file patches without rewriting unaffected code."
    ),
    SOCIAL_RECAP(
        title = "Social Recap",
        description = "Segment video transcripts into structured multi-scene recap script",
        systemPrompt = "You are a viral social video recap director. Generate 1 to 18 scene timelines with visual descriptions, voiceover scripts, and subtitles."
    ),
    TTS_SCRIPT(
        title = "TTS Script",
        description = "Generate Myanmar or English natural speech narration scripts",
        systemPrompt = "You are a voiceover scriptwriter specializing in natural Burmese (my-MM) and English speech for Edge-TTS."
    )
}
