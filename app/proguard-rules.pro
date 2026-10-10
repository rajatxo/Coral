# =============================================================================
# Coral ProGuard / R8 rules
# =============================================================================
# These rules keep classes that R8 would otherwise strip or rename, which
# would cause runtime crashes in the release build. Debug builds don't run
# R8 so they're unaffected.
#
# The release build is signed with the debug key for now (fine for testing).
# When we publish to Play Store, we'll switch to a real upload key.
# =============================================================================

# --- Kotlin metadata (required for reflection) ---
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @kotlin.Metadata *;
}

# --- kotlinx.serialization ---
# Without these, R8 strips the generated serializers and the app crashes
# when trying to decode playlists.json / favorites.json.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod

# Keep the generated serializers for our @Serializable classes
-keepclassmembers class com.rajatxo.coral.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.rajatxo.coral.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.rajatxo.coral.data.model.** { *; }

# --- Media3 / ExoPlayer ---
# ExoPlayer uses reflection to instantiate decoders/renderers.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Coil (image loading) ---
-keep class coil3.** { *; }
-dontwarn coil3.**

# --- Coral model classes (used in JSON serialization + reflection) ---
-keep class com.rajatxo.coral.domain.model.** { *; }
-keep class com.rajatxo.coral.data.lyrics.** { *; }
-keep class com.rajatxo.coral.data.prefs.** { *; }
-keep class com.rajatxo.coral.data.premium.** { *; }

# --- JAudioTagger (metadata embedding) ---
# Uses heavy reflection to instantiate format-specific tag readers/writers.
# Without these, R8 strips the tag classes and embedding fails at runtime.
# ★ REPLACED with native TagLib (libs/taglib.aar) — see below. Keeping these
#   rules is harmless in case any reference lingers in unused code paths.
-keep class org.jaudiotagger.** { *; }
-keep class org.jaudiotagger.tag.** { *; }
-keep class org.jaudiotagger.audio.** { *; }
-keep class org.jaudiotagger.audio.exceptions.** { *; }
-dontwarn org.jaudiotagger.**

# --- TagLib (native C++ audio tag library via JNI) ---
# The Java wrapper class aman.taglib.TagLib declares native methods that
# call into libtaglib_jni.so. R8 must keep the class + native method names
# intact so JNI can find them at runtime.
-keep class aman.taglib.** { *; }
-keepclassmembers class aman.taglib.** {
    native <methods>;
}
-dontwarn java.lang.invoke.StringConcatFactory

# --- Font resources (Poppins) ---
-keep class com.rajatxo.coral.R$font { *; }

# --- Generic safe fallbacks ---
-dontwarn javax.lang.model.**
