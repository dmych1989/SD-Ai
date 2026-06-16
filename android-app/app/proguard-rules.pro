# proguard-rules.pro
# SD-AI Android App ProGuard 规则

# ── 基础 Android ──────────────────────────────
-keep class android.** { *; }
-keep class androidx.** { *; }
-keep class com.google.** { *; }

# ── Compose ────────────────────────────────────
-keep class androidx.compose.** { *; }
-keep class kotlinx.coroutines.** { *; }

# ── Kotlin 序列化 ──────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep class kotlinx.serialization.json.** { *; }
-keep class com.sdaiapp.data.** { *; }

# ── Ktor HTTP ──────────────────────────────────
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

# ── MNN（原生库，不需混淆 Java 接口）──────────
-keep class com.alibaba.mnn.** { *; }
-keep class **JNI { *; }

# ── CrashLogger ────────────────────────────────
-keep class com.sdaiapp.utils.CrashLogger { *; }

# ── 保留 @Serializable 注解的类 ───────────────
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── 移除日志（Release 构建）────────────────────
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
