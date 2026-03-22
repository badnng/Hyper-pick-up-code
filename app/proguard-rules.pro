# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }

# PaddleOCR ncnn 库
-keep class com.equationl.ncnnandroidppocr.** { *; }
-keep interface com.equationl.ncnnandroidppocr.** { *; }

# ncnn 库（OCR 依赖）
-keep class org.ncnn.** { *; }
-keep interface org.ncnn.** { *; }

# 禁用 OCR 库的优化（避免 R8 破坏内部逻辑）
-dontoptimize com.equationl.ncnnandroidppocr.**
-dontoptimize org.ncnn.**

# 保留所有 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留 OCR 相关的 JNI 类
-keep class * implements java.io.Serializable { *; }
