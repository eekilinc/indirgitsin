# Keep extractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Preserve the JavaScript bridge and serialized cipher configuration in optimized builds.
-keep class com.zemer.cipher.** { *; }
