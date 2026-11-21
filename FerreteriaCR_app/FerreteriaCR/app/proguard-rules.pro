# Proguard rules for FerreteriaMovil
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.ferreteria.movil.data.model.** { *; }
