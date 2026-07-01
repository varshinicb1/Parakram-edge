## MILITARY-GRADE PROGUARD RULES
## ================================

## Keep debuggable stack traces in production
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

## Strip all logging from release builds (Timber will auto-disable)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

## Kotlin stdlib + Coroutines
-keep class kotlin.Metadata { *; }
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class * extends kotlinx.coroutines.internal.MainDispatcherFactory { public *; }

## Data model classes — never obfuscate serialization targets
-keep class com.example.data.** { *; }
-keep class com.example.data.firebase.** { *; }
-keep class com.example.data.db.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.squareup.moshi.Json <fields>;
}

## kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** { *** Companion; }
-keepclasseswithmembers class com.example.** { kotlinx.serialization.KSerializer serializer(...); }

## Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

## Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

## Moshi + OkHttp
-keep class com.squareup.moshi.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

## Retrofit
-keep class retrofit2.** { *; }
-keepclassmembers class * { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.**

## Jetpack Compose + Material3
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

## Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

## Biometric
-keep class androidx.biometric.** { *; }

## Google Services / Credential Manager
-keep class com.google.android.gms.auth.** { *; }

