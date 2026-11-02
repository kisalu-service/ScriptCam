# ScriptCam — règles R8
# Compose / AndroidX sont déjà couverts par les règles consommateur des AAR.
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.coroutines.**
-keep class com.scriptcam.app.data.** { *; }
