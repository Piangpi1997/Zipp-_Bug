# Termux Engine

Termux is the heavy execution layer for Zip_Bug.

Recommended tools:
- Java 17
- Gradle 8.9
- Python
- ffmpeg
- yt-dlp
- git
- aapt2
- zipalign

Enable external command execution in Termux:

~~~properties
# ~/.termux/termux.properties
allow-external-apps=true
~~~

Restart Termux after changing the property.

For Android builds on ARM64, use the native Termux aapt2 binary:

~~~properties
# ~/.gradle/gradle.properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
~~~

Zip_Bug requests com.termux.permission.RUN_COMMAND and calls Termux RunCommandService with an allow-listed executable path. The app intentionally does not expose arbitrary raw shell execution to imported mini-apps.
