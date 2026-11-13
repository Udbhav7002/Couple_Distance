# Firebase Auth & Firestore
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# DataStore / Protobuf
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class androidx.work.** { *; }

# Compose (keep all composable functions from being renamed/removed)
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Our data models (used in Firestore serialization)
-keep class com.closer.app.data.** { *; }
-keep class com.closer.app.backend.** { *; }

# Enum values used in DataStore preferences
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# General rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
