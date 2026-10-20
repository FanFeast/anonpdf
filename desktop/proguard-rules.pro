# The desktop release build is shrunk like the Android one: the Compose icon
# library alone is ~37 MB, and the app uses a few dozen icons from it.

# PDFBox reflects over its own class hierarchy and loads resources (glyph lists,
# AFM metrics, ICC profiles) by name, so it cannot be renamed. Same rule as the
# Android build, under upstream PDFBox's package names.
-keep class org.apache.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.fontbox.**

# PDFBox's optional extras: certificate-based encryption (BouncyCastle) and JBIG2
# / JPEG 2000 decoders. None ship with the app.
-dontwarn org.bouncycastle.**
-dontwarn com.github.jaiimageio.**

# PDFBox logs through commons-logging, which picks its implementation by class
# name at runtime. Without this the shrinker drops every implementation and
# opening any PDF fails. Its optional bridges (log4j, avalon, servlet) are absent.
-keep class org.apache.commons.logging.** { *; }
-dontwarn org.apache.commons.logging.**

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
