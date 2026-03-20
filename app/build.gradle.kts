import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.Badnng.moe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.Badnng.moe"
        minSdk = 35
        targetSdk = 36
        versionCode = 20260321_12
        versionName = "26.3.21.C02-Dev"

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
        // 从 local.properties 读取配置
        val localProperties = Properties().apply {
            val localFile = rootProject.file("local.properties")
            if (localFile.exists()) {
                load(localFile.inputStream())
            }
        }

        // 获取 keystore 路径并使用
        val keyStorePath = localProperties.getProperty("key.store.path")
        if (keyStorePath != null) {
            create("release") {
                storeFile = file(keyStorePath)
                storePassword = localProperties.getProperty("key.store.password") ?: ""
                keyAlias = localProperties.getProperty("key.alias") ?: ""
                keyPassword = localProperties.getProperty("key.alias.password") ?: ""
            }

            getByName("debug") {
                storeFile = file(keyStorePath)
                storePassword = localProperties.getProperty("key.store.password") ?: ""
                keyAlias = localProperties.getProperty("key.alias") ?: ""
                keyPassword = localProperties.getProperty("key.alias.password") ?: ""
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // 让 release 使用 debug 签名（实际上现在两者用同一个文件）
        }
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug") // 使用相同签名
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    val shizuku_version = "13.1.5"
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
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.material3.windowsizeclass)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // PaddleOCR (替换 ML Kit 文字识别)
    implementation("com.github.equationl.paddleocr4android:ncnnandroidppocr:v1.3.0")
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