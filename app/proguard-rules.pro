# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Android ---
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference
-keep public class * extends androidx.fragment.app.Fragment

# --- Realm ---
-keep class io.realm.annotations.RealmModule
-keep @io.realm.annotations.RealmModule class *
-keep class io.realm.internal.Keep
-keep @io.realm.internal.Keep class * { *; }
-dontwarn io.realm.**

# --- Retrofit ---
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# --- Gson ---
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# --- Glide ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$ImageType {
  **[] $VALUES;
  public *;
}
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$ImageType {
  **[] $VALUES;
  public *;
}

# --- Hilt / Dagger ---
-keep class com.google.dagger.** { *; }
-keep class dagger.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# --- MPAndroidChart ---
-keep class com.github.mikephil.charting.** { *; }

# --- MaterialDrawer ---
-keep class com.mikepenz.materialdrawer.** { *; }

# --- Markwon ---
-keep class io.noties.markwon.** { *; }

# --- OpenCSV ---
-keep class com.opencsv.** { *; }

# --- Osmdroid ---
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# --- Application Models ---
# Keep all models to ensure Gson and Realm work correctly with reflection
-keep class org.ole.planet.myplanet.model.** { *; }
-keepnames class org.ole.planet.myplanet.model.** { *; }

# --- Kotlin Coroutines ---
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
