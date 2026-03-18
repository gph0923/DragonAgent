# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep DTOs
-keep,includedescriptorclasses class com.dragon.agent.**$$serializer { *; }
-keepclassmembers class com.dragon.agent.** {
    *** Companion;
}
-keepclasseswithmembers class com.dragon.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}
