# Add project specific ProGuard rules here.
-keep class ai.opencode.** { *; }
-keepclassmembers class ai.opencode.** { *; }
-dontwarn ai.opencode.**

# JMX / javax.management (not available on Android, referenced by JGit)
-dontwarn javax.management.**
-dontwarn java.lang.management.**
-dontwarn javax.management.**

# SLF4J static binding
-dontwarn org.slf4j.**

# Ktor debug detector
-dontwarn io.ktor.util.debug.**

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
