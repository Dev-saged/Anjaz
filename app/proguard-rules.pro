-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.anjaz.app.** { kotlinx.serialization.KSerializer serializer(...); }

# JNI يصل إلى هذه الأصناف بالاسم
-keep class com.k2fsa.sherpa.onnx.** { *; }

-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
