# Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keep,includedescriptorclasses class com.borgorninja.androtask.**$$serializer { *; }
-keepclassmembers class com.borgorninja.androtask.** {
    *** Companion;
}

# Room
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** INSTANCE;
    public static ** getDatabase(...);
}

# Accessibility Service - must survive shrinking
-keep class com.borgorninja.androtask.MacroAccessibilityService { *; }
-keep class com.borgorninja.androtask.FloatingOverlayService { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
