# Error Prone's CLASS-retained test annotations refer to this javac-only enum.
# It is used by static analysis, not by the Android instrumentation runtime.
-dontwarn javax.lang.model.element.Modifier

# Test discovery and AndroidX Test use reflection extensively. Keep the harness intact;
# R8 still rewrites references using the optimized target APK's mapping.
# These rules apply only to the test APK, never to the distributed application.
-keep class ** { *; }
-dontoptimize
