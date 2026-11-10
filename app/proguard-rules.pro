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

# Gson reflects over model fields; Gson and Retrofit ship their own consumer rules
-keep class org.ole.planet.myplanet.model.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# Markwon missing optional dependencies
-dontwarn com.caverock.androidsvg.**
-dontwarn org.commonmark.ext.gfm.strikethrough.**

# PdfBox-Android optional JPEG 2000 decoder
-dontwarn com.gemalto.jp2.**
