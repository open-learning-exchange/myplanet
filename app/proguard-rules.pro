# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# General attributes
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Realm
-keep class io.realm.annotations.RealmModule
-keep @io.realm.annotations.RealmModule class *
-keep class io.realm.internal.Keep
-keep @io.realm.internal.Keep class * { *; }
-keep class io.realm.CompactOnLaunchCallback
-dontwarn javax.**
-dontwarn io.realm.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Retrofit
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-dontwarn retrofit2.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Hilt / Dagger
-keep class com.google.dagger.hilt.** { *; }
-keep interface com.google.dagger.hilt.** { *; }
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }
-dontwarn dagger.hilt.**
# Keep Hilt generated classes
-keep class * extends com.google.dagger.hilt.android.internal.managers.ActivityComponentManager
-keep class * extends com.google.dagger.hilt.android.internal.managers.FragmentComponentManager
-keep class * extends com.google.dagger.hilt.android.internal.managers.ViewComponentManager
-keep class * extends com.google.dagger.hilt.android.internal.managers.ServiceComponentManager
-keep class * extends com.google.dagger.hilt.android.internal.managers.BroadcastReceiverComponentManager
# Keep entry points
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.HiltAndroidApp class *

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# MaterialDrawer
-keep class com.mikepenz.materialdrawer.** { *; }
-keep interface com.mikepenz.materialdrawer.** { *; }
-dontwarn com.mikepenz.materialdrawer.**

# Osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.json.**
-keep class kotlinx.serialization.json.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }
-dontwarn androidx.work.**

# MyPlanet Models and UI
-keep class org.ole.planet.myplanet.model.** { *; }
# Keep fragments and activities to be safe with Hilt and manifest references
-keep class org.ole.planet.myplanet.ui.** { *; }
-keep class org.ole.planet.myplanet.base.** { *; }

# Markwon missing optional dependencies
-dontwarn com.caverock.androidsvg.**
-dontwarn org.commonmark.ext.gfm.strikethrough.**

# Material Dialogs
-dontwarn com.afollestad.materialdialogs.**
-keep class com.afollestad.materialdialogs.** { *; }

# PhotoView
-keep class com.github.chrisbanes.photoview.** { *; }

# Circular Progress View
-keep class com.github.VaibhavLakhera.circularprogressview.** { *; }
