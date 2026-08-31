# Keep extractor
-keep class com.indirgitsin.app.data.downloader.LameEncoder { *; }

-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Preserve the JavaScript bridge and serialized cipher configuration in optimized builds.
-keep class com.zemer.cipher.** { *; }

# Keep persisted worker names and the media API exercised from the separate instrumented APK.
# UI/framework implementation code can still be shrunk and optimized.
-keep,allowoptimization class com.indirgitsin.app.data.downloader.** { public *; }
-keep,allowoptimization class com.indirgitsin.app.data.model.** { public *; }

# Rhino uses reflection for its JavaScript runtime. Match the upstream NewPipe approach.
# https://github.com/TeamNewPipe/NewPipe/blob/dev/app/proguard-rules.pro
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
# Desktop-only JavaBean conversion and JSR-223/tools integrations are not used on Android.
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
