# quiz_helper proguard rules (release minify disabled by default; keep safe defaults)
-keepattributes *Annotation*
-keep class com.quizhelper.** { *; }
-dontwarn androidx.security.**
