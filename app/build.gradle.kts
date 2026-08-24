import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        versionCode = 20260825_11
        versionName = "26.8.25.C01-Dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // 项目只发布 arm64；避免 OpenCV/ONNX/ML Kit 的其它 ABI 进入 APK。
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
        // Miuix 0.9.4-rc01 的 miuix-nav 使用 JVM 21 编译，Android 编译链需保持一致。
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
            // 保持 .so 非压缩，交给 zip 对齐；与 16KB ELF 页对齐配合使用。
            useLegacyPackaging = false
            // 再加一层打包侧保险，防止依赖 AAR 带入模拟器 ABI。
            excludes += setOf(
                "**/armeabi-v7a/**",
                "**/x86/**",
                "**/x86_64/**",
            )
        }
    }

    androidResources {
        noCompress += "onnx"
    }
}

tasks.named("preBuild").configure {
    dependsOn(generatePrivacyAssets)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(files("libs/xms-wearable-lib_1.4_release.aar"))
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
    implementation(libs.androidx.lifecycle.runtime.compose)
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
    // Miuix 0.9.4-rc01 的导航事件 API 在源码中被直接引用；其发布元数据标为 runtime，
    // 因此需要显式加入编译 classpath。
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // PaddleOCR v6 Tiny 本地精简推理后端（arm64-only，自定义构建）
    implementation(files("libs/onnxruntime-slim.aar"))
    implementation(files("libs/opencv-slim.aar"))
    // Miuix Blur (毛玻璃模糊效果)
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4-rc01")
    // Miuix UI 组件库 (Card, Button, Switch, SmallTitle 等)
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4-rc01")
    // Miuix Preference 组件 (ArrowPreference, SwitchPreference 等)
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4-rc01")
    // Miuix 0.9.4-rc01 自研导航运行时，替代已移除的 miuix-navigation3-ui。
    implementation("top.yukonga.miuix.kmp:miuix-nav:0.9.4-rc01")
    // Miuix Extended Icons (MiuixIcons.Regular.Edit / Settings)
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4-rc01")
    // 保留 ML Kit 条码扫描
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    // 本地 JVM 单元测试需要真实的 org.json 实现；Android SDK 的 org.json
    // 仅提供未实现的桩方法，会让协议编码/解析测试全部报 "not mocked"。
    testImplementation("org.json:json:20250517")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
