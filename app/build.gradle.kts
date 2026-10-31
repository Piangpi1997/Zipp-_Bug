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
