# Ktor / kotlinx.serialization / coroutines keep rules.
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclassmembers class **$$serializer { *; }
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.serialization.**
