import java.io.File
import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val releaseKeystorePath = System.getenv("ZIPBUG_RELEASE_KEYSTORE")
val releaseKeystorePassword = System.getenv("ZIPBUG_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ZIPBUG_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ZIPBUG_KEY_PASSWORD")

val releaseSigningReady =
    listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() } &&
        releaseKeystorePath?.let { file(it).isFile } == true

android {
    namespace = "com.zipbug.base"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zipbug.base"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "2.0.0-alpha04"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".next"
            versionNameSuffix = "-zippy-next"
        }

        // Side-by-side preview build: installs next to the .next debug app
        // (e.g. alpha04) without touching it, because the package ID differs
        // (com.zipbug.base.preview). Data is NOT shared with or copied from
        // the .next app. Build with: gradle assemblePreview
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true; buildConfig = true }

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // Runtime dependency of the bundled FFmpegKit AAR (below).
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// Open Source Licenses: regenerate/verify license metadata derived from the
// real dependencies above (scripts/generate-licenses.py). CI also enforces
// this through verify-project.py, which runs on every build.
tasks.register<Exec>("generateLicenseReport") {
    group = "verification"
    description = "Regenerates open-source license JSON + THIRD_PARTY_NOTICES " +
        "from the project's real dependencies."
    commandLine(
        "python3",
        rootProject.file("scripts/generate-licenses.py").absolutePath
    )
}

tasks.register<Exec>("verifyLicenseMetadata") {
    group = "verification"
    description = "Fails if a dependency lacks curated license metadata or " +
        "the generated license files are stale."
    commandLine(
        "python3",
        rootProject.file("scripts/generate-licenses.py").absolutePath,
        "--check"
    )
}

// --- Open Source Licenses v2: graph verification -------------------------
// scripts/license-graph.json is produced by scripts/resolve-license-graph.py
// (a POM walker). The tasks below re-resolve the same classpaths with
// Gradle itself: dumpLicenseGraph rewrites the committed file from
// Gradle's resolution (maintainer tool), and verifyLicenseGraph — wired
// into preDebugBuild/preReleaseBuild — fails the build when the
// committed graph drifts from Gradle's own resolution.

fun licenseModuleStrings(configuration: Configuration): Set<String> =
    configuration.resolvedConfiguration.lenientConfiguration
        .allModuleDependencies
        .map { "${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}" }
        .toSet()

fun licenseGraphSection(text: String, section: String): Set<String> {
    val start = text.indexOf("\"$section\"")
    require(start >= 0) {
        "license-graph.json is missing the \"$section\" section"
    }
    val open = text.indexOf('[', start)
    val close = text.indexOf(']', open)
    return Regex("\"([^\"]+)\"").findAll(text.substring(open, close))
        .map { it.groupValues[1] }
        .toSet()
}

val dumpLicenseGraph by tasks.registering {
    group = "verification"
    description =
        "Rewrite scripts/license-graph.json from Gradle's own resolution."
    doLast {
        val graphFile = rootProject.file("scripts/license-graph.json")
        val extras = if (graphFile.exists()) {
            licenseGraphSection(graphFile.readText(), "extras")
        } else {
            emptySet()
        }
        val detached = configurations.detachedConfiguration(
            dependencies.create("androidx.core:core-ktx:1.13.1"),
            dependencies.create("androidx.appcompat:appcompat:1.7.0"),
            dependencies.create("com.google.android.material:material:1.12.0"),
            dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
        )
        val appModules = licenseModuleStrings(
            configurations.named("releaseRuntimeClasspath").get()
        )
        val generatedModules = licenseModuleStrings(detached)

        fun section(name: String, modules: Set<String>): String =
            "  \"$name\": [\n" +
                modules.sorted().joinToString(",\n") { "    \"$it\"" } +
                "\n  ]"
        graphFile.writeText(
            "{\n" +
                section("app", appModules) + ",\n" +
                section("generatedApps", generatedModules) + ",\n" +
                section("extras", extras) +
                "\n}\n"
        )
        println(
            "license-graph.json rewritten: app=${appModules.size} " +
                "generatedApps=${generatedModules.size}"
        )
    }
}

val verifyLicenseGraph by tasks.registering {
    group = "verification"
    description =
        "Fail if scripts/license-graph.json drifts from Gradle's resolution."
    doLast {
        val graphFile = rootProject.file("scripts/license-graph.json")
        check(graphFile.exists()) {
            "scripts/license-graph.json is missing; run " +
                "scripts/resolve-license-graph.py"
        }
        val text = graphFile.readText()
        val expectedApp = licenseGraphSection(text, "app")
        val expectedGen = licenseGraphSection(text, "generatedApps")
        val detached = configurations.detachedConfiguration(
            dependencies.create("androidx.core:core-ktx:1.13.1"),
            dependencies.create("androidx.appcompat:appcompat:1.7.0"),
            dependencies.create("com.google.android.material:material:1.12.0"),
            dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
        )
        val actualApp = licenseModuleStrings(
            configurations.named("releaseRuntimeClasspath").get()
        )
        val actualGen = licenseModuleStrings(detached)
        val problems = mutableListOf<String>()
        (expectedApp - actualApp).forEach {
            problems += "app: committed but not resolved: $it"
        }
        (actualApp - expectedApp).forEach {
            problems += "app: resolved but not committed: $it"
        }
        (expectedGen - actualGen).forEach {
            problems += "generatedApps: committed but not resolved: $it"
        }
        (actualGen - expectedGen).forEach {
            problems += "generatedApps: resolved but not committed: $it"
        }
        check(problems.isEmpty()) {
            "License graph drift detected — re-run " +
                "scripts/resolve-license-graph.py (or the " +
                "dumpLicenseGraph task) and regenerate:\n" +
                problems.joinToString("\n")
        }
        println(
            "License graph matches Gradle resolution: " +
                "app=${actualApp.size}, generatedApps=${actualGen.size}"
        )
    }
}

tasks.matching {
    it.name == "preDebugBuild" || it.name == "preReleaseBuild"
}.configureEach {
    dependsOn(verifyLicenseGraph)
}

// ---------------------------------------------------------------------
// Bundled in-app FFmpeg engine (FFmpegKit "full", LGPL).
//
// FFmpegKit was removed from Maven Central when the project retired,
// so the AAR is fetched once from Google's read-only mirror of Maven
// Central and pinned by SHA-256. The file is git-ignored; CI fetches
// it the same way. The native libraries are packaged for arm64-v8a
// only (the Termux engine remains the fallback on other ABIs) and are
// stored uncompressed (modern packaging: no extraction copy on disk).
// ---------------------------------------------------------------------
val ffmpegKitAarUrl =
    "https://maven-central.storage-download.googleapis.com/maven2/" +
        "com/arthenica/ffmpeg-kit-full/6.0.LTS/ffmpeg-kit-full-6.0.LTS.aar"
val ffmpegKitAarSha256 =
    "e77eb8d02b67e225759b27e7288f84a8d2a4392abd2418f282c2936a990df4c3"
val ffmpegKitAar = layout.projectDirectory.file(
    "libs/ffmpeg-kit-full-6.0.LTS.aar"
)

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadFfmpegKit by tasks.registering {
    description = "Downloads the pinned FFmpegKit AAR if missing/stale."
    outputs.file(ffmpegKitAar)
    doLast {
        val target = ffmpegKitAar.asFile
        if (target.exists() && sha256Hex(target) == ffmpegKitAarSha256) {
            return@doLast
        }
        target.parentFile.mkdirs()
        val tmp = File(target.parentFile, target.name + ".download")
        URL(ffmpegKitAarUrl).openStream().use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        check(sha256Hex(tmp) == ffmpegKitAarSha256) {
            "FFmpegKit AAR checksum mismatch — refusing to use it"
        }
        tmp.renameTo(target)
    }
}

tasks.configureEach {
    if (name.startsWith("pre") && name.endsWith("Build")) {
        dependsOn(downloadFfmpegKit)
    }
}

android {
    defaultConfig {
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(files("libs/ffmpeg-kit-full-6.0.LTS.aar"))
}
