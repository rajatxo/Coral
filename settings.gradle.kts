pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ★ JitPack — required for JAudioTagger (AdrienPoupa Android fork).
        //   Used by MetadataEmbedder to write cover art into MP3/M4A/FLAC tags.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Coral"
include(":app")
include(":misc:alacdecoder")
