import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

val generatedPrivacyAssetsDir = layout.buildDirectory.dir("generated/privacy-assets")
val generatePrivacyAssets by tasks.registering(Copy::class) {
    from(rootProject.file("PRIVACY.md"))
    into(generatedPrivacyAssetsDir)
}

android {
    namespace = "com.Badnng.moe"
    compileSdk = 37

    lint {
        checkReleaseBuilds = false
    }

    defaultConfig {
        applicationId = "com.Badnng.moe"
        minSdk = 35
        targetSdk = 37
        versionCode = 20260713_11
        versionName = "26.7.13.C01-Dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.add("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    signingConfigs {
        val localProperties = Properties().apply {
            val localFile = rootProject.file("local.properties")
            if (localFile.exists()) {
                load(localFile.inputStream())
            }
        }

        val keyStorePath = System.getenv("KEY_STORE_PATH")?.let {
            rootProject.file(it)    // ← 相对根目录
        } ?: localProperties.getProperty("key.store.path")?.let {
            file(it)
        }

        val keyStorePassword = System.getenv("STORE_PASSWORD")
            ?: localProperties.getProperty("key.store.password")
        val keyAlias = System.getenv("KEY_ALIAS")
            ?: localProperties.getProperty("key.alias")
        val keyPassword = System.getenv("KEY_PASSWORD")
            ?: localProperties.getProperty("key.alias.password")

        if (keyStorePath != null) {
            create("release") {
                storeFile = keyStorePath
                storePassword = keyStorePassword ?: ""
                this.keyAlias = keyAlias ?: ""
                this.keyPassword = keyPassword ?: ""
            }

            getByName("debug") {
                storeFile = keyStorePath
                storePassword = keyStorePassword ?: ""
                this.keyAlias = keyAlias ?: ""
                this.keyPassword = keyPassword ?: ""
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro")
        }
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets.getByName("main").assets.directories.add(
        generatedPrivacyAssetsDir.get().asFile.absolutePath
    )

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generatePrivacyAssets)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val shizuku_version = "13.1.5"
    // LocalBroadcastManager
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    // OkHttp for update checking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.kyant0:backdrop:2.0.0-alpha03")
    implementation("dev.rikka.shizuku:provider:${shizuku_version}")
    implementation("dev.rikka.shizuku:api:${shizuku_version}")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // PaddleOCR (替换 ML Kit 文字识别)
    implementation("com.github.equationl.paddleocr4android:ncnnandroidppocr:v1.3.0")
    // Miuix Blur (毛玻璃模糊效果)
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.3")
    // Miuix UI 组件库 (Card, Button, Switch, SmallTitle 等)
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    // Miuix Preference 组件 (ArrowPreference, SwitchPreference 等)
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    // Miuix Navigation3 UI (NavDisplay, NavDisplayTransitionEffects)
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.3")
    // AndroidX Navigation3 Runtime (NavKey)
    implementation("androidx.navigation3:navigation3-runtime:1.1.4")
    // Miuix Extended Icons (MiuixIcons.Regular.Edit / Settings)
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.3")
    // 保留 ML Kit 条码扫描
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
