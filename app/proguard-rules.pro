# Swiffle ProGuard / R8 Optimization Rules

# Keep Native JNI Engine methods
-keep class com.swiffle.app.NativeEngine { *; }

# Keep models used across SharedPreferences & JSON
-keep class com.swiffle.app.ui.home.RecentFile { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# AndroidX Compose Runtime
-keepclassmembers class androidx.compose.** {
    public <methods>;
}

# Coil Image Loader
-keepclassmembers class coil.** {
    public <methods>;
}
-dontwarn coil.**

# Apache POI
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**

# ML Kit Text Recognition
-keep class com.google.mlkit.vision.text.** { *; }
-dontwarn com.google.mlkit.vision.text.**
