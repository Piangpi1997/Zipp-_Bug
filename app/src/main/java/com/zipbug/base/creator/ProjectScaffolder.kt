package com.zipbug.base.creator

import android.content.Context
import com.zipbug.base.ZipBugApp
import com.zipbug.base.data.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ProjectScaffolder {

    suspend fun createSingleActivityProject(
        context: Context,
        appName: String,
        packageName: String,
        buttonText: String = "Click Me",
        toastMessage: String = "Hello"
    ): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = appName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "MyApp" }
            val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
            val projectDir = File(projectsDir, safeName).apply { mkdirs() }

            val packageRelPath = packageName.replace('.', '/')
            val javaDir = File(projectDir, "app/src/main/java/$packageRelPath").apply { mkdirs() }
            val resDir = File(projectDir, "app/src/main/res").apply { mkdirs() }
            val layoutDir = File(resDir, "layout").apply { mkdirs() }
            val drawableDir = File(resDir, "drawable").apply { mkdirs() }
            val valuesDir = File(resDir, "values").apply { mkdirs() }

            // 1. AndroidManifest.xml
            val manifestContent = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$packageName">

    <uses-sdk
        android:minSdkVersion="26"
        android:targetSdkVersion="34" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>""".trimIndent()
            File(projectDir, "app/src/main/AndroidManifest.xml").writeText(manifestContent, Charsets.UTF_8)

            // 2. MainActivity.java
            val mainActivityContent = """package $packageName;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnAction = findViewById(R.id.btnAction);
        if (btnAction != null) {
            btnAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(MainActivity.this, "$toastMessage", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}""".trimIndent()
            val mainActivityFile = File(javaDir, "MainActivity.java")
            mainActivityFile.writeText(mainActivityContent, Charsets.UTF_8)

            // 3. res/layout/activity_main.xml
            val layoutContent = """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    android:background="#121212"
    android:padding="24dp">

    <TextView
        android:id="@+id/tvAppTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/app_name"
        android:textColor="#FFFFFF"
        android:textSize="24sp"
        android:textStyle="bold"
        android:layout_marginBottom="32dp" />

    <Button
        android:id="@+id/btnAction"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/btn_action"
        android:textColor="#FFFFFF"
        android:background="@drawable/btn_red_style"
        android:paddingHorizontal="32dp"
        android:paddingVertical="14dp"
        android:textSize="16sp" />

</LinearLayout>""".trimIndent()
            File(layoutDir, "activity_main.xml").writeText(layoutContent, Charsets.UTF_8)

            // 4. res/drawable/btn_red_style.xml
            val buttonStyleContent = UiComponentGallery.curatedComponents[0].nativeXmlContent
            File(drawableDir, "btn_red_style.xml").writeText(buttonStyleContent, Charsets.UTF_8)

            // 5. res/values/strings.xml
            val stringsContent = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">$appName</string>
    <string name="btn_action">$buttonText</string>
    <string name="toast_message">$toastMessage</string>
</resources>""".trimIndent()
            File(valuesDir, "strings.xml").writeText(stringsContent, Charsets.UTF_8)

            // 6. res/values/colors.xml
            val colorsContent = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="bg_dark">#121212</color>
    <color name="text_white">#FFFFFF</color>
    <color name="red_primary">#E53935</color>
    <color name="red_pressed">#B71C1C</color>
</resources>""".trimIndent()
            File(valuesDir, "colors.xml").writeText(colorsContent, Charsets.UTF_8)

            // 7. build.gradle.kts
            val gradleContent = """plugins {
    id("com.android.application")
}

android {
    namespace = "$packageName"
    compileSdk = 34

    defaultConfig {
        applicationId = "$packageName"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
}
""".trimIndent()
            File(projectDir, "build.gradle.kts").writeText(gradleContent, Charsets.UTF_8)

            // 8. settings.gradle.kts
            val settingsContent = """rootProject.name = "$safeName"
include(":app")
""".trimIndent()
            File(projectDir, "settings.gradle.kts").writeText(settingsContent, Charsets.UTF_8)

            // 9. Initialize pnl1 Panel Assembly
            UiComponentGallery.savePanel(projectDir, PanelModel(cardHeading = appName))

            val projectEntity = ProjectEntity(
                id = "proj_${safeName}",
                name = appName,
                kind = "Native Android",
                rootPath = projectDir.absolutePath,
                entryFile = mainActivityFile.absolutePath,
                createdAt = System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis()
            )

            val app = context.applicationContext as ZipBugApp
            app.database.projectDao().upsert(projectEntity)

            projectEntity
        }
    }
}
