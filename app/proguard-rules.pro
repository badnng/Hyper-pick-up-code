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

# PaddleOCR v6 官方 Android SDK 与 ONNX Runtime
-keep class com.paddle.ocr.** { *; }
-keep class ai.onnxruntime.** { *; }

# OpenCV Java bindings used by PP-OCRv6 preprocessing. Keep only the bindings
# referenced by the OCR pipeline so R8 can remove camera/dnn/video wrappers;
# the prebuilt native lib is kept intact and handled by ABI/package stripping.
-keep class org.opencv.core.Core { *; }
-keep class org.opencv.core.CvType { *; }
-keep class org.opencv.core.Mat { *; }
-keep class org.opencv.core.MatOfByte { *; }
-keep class org.opencv.core.MatOfPoint { *; }
-keep class org.opencv.core.MatOfPoint2f { *; }
-keep class org.opencv.core.Point { *; }
-keep class org.opencv.core.Scalar { *; }
-keep class org.opencv.core.Size { *; }
-keep class org.opencv.android.Utils { *; }
-keep class org.opencv.imgcodecs.Imgcodecs { *; }
-keep class org.opencv.imgproc.Imgproc { *; }
-keepclassmembers class org.opencv.** { native <methods>; }

# Xiaomi Wearable AIDL contract and Parcelable callback models.
-keep class com.xiaomi.xms.wearable.** { *; }
-keep interface com.xiaomi.xms.wearable.** { *; }
