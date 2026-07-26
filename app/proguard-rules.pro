# PDFBox-Android reflects over its own class hierarchy and loads resources
# (glyph lists, AFM metrics, ICC profiles) by name, so it cannot be renamed.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**

# PDFBox carries references to desktop-Java APIs that do not exist on Android.
# They sit on code paths we never take; silence the warnings rather than ship them.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.bind.**
-dontwarn org.apache.commons.logging.**
-dontwarn sun.misc.**

# BouncyCastle is excluded from the build (see app/build.gradle.kts): PDFBox only
# reaches it for certificate-based encryption, which this app does not offer.
# Password protection uses the platform JCE. Silence the dangling references.
-dontwarn org.bouncycastle.**

# Kotlin metadata + coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
