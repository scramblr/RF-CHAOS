# Add project specific ProGuard rules here.

# Keep Room entities
-keep class com.scramblr.rftoolkit.data.models.** { *; }

# Keep enum classes
-keepclassmembers enum * { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
