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
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.rajatxo.coral.**$$serializer { *; }
-keepclassmembers class com.rajatxo.coral.** {
    *** Companion;
}
-keepclasseswithmembers class com.rajatxo.coral.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep, includedescriptorclass class com.rajatxo.coral.data.model.** { *; }

# --- Media3 / ExoPlayer ---
# ExoPlayer uses reflection to instantiate decoders/renderers. R8 stripping
# these causes "Could not find a decoder" crashes on release builds.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Coil (image loading) ---
# Coil uses reflection to load ImageLoader providers. Keep its public API.
-keep class coil3.** { *; }
-dontwarn coil3.**

# --- Compose ---
# Compose is mostly fine with R8, but the runtime needs these kept for
# @Composable function lookup.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keepclassmembers class androidx.compose.** {
    public *;
}

# --- Coral model classes (used in JSON serialization) ---
-keep class com.rajatxo.coral.domain.model.** { *; }
-keep class com.rajatxo.coral.data.model.** { *; }
-keep class com.rajatxo.coral.data.lyrics.** { *; }
-keep class com.rajatxo.coral.data.prefs.** { *; }
-keep class com.rajatxo.coral.data.premium.** { *; }

# --- Font resources (Poppins) ---
-keep class com.rajatxo.coral.R$font { *; }

# --- Generic safe fallbacks ---
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.lang.model.**
-keepattributes Signature, Exceptions, InnerClasses, EnclosingMethod
