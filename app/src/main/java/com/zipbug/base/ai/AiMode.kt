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
        systemPrompt = """You are the Zip_Bug App Creator engine. You MUST respond with ONLY a valid, parseable JSON object matching this schema:
{
  "project": {
    "name": "AppName",
    "packageName": "com.example.app",
    "target": "ANDROID_KOTLIN",
    "minSdk": 24,
    "targetSdk": 34
  },
  "screens": [
    {
      "name": "MainScreen",
      "type": "Activity",
      "components": [
        {"type": "TextView", "id": "titleText", "text": "Hello World"}
      ]
    }
  ],
  "permissions": ["android.permission.INTERNET"],
  "dependencies": ["androidx.core:core-ktx:1.13.1"],
  "files": [
    {
      "path": "app/src/main/java/com/example/app/MainActivity.kt",
      "content": "..."
    }
  ],
  "build": {
    "command": "gradle assembleDebug"
  }
}
Do not wrap in markdown quotes if possible, or use standard ```json blocks."""
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
