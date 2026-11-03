# Add project specific ProGuard rules here.
# Preserve line numbers and source file names for crash analysis in Google Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room database implementation and entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.example.data.model.** { *; }
-dontwarn androidx.room.**
-dontwarn okio.**
-dontwarn okhttp3.**

