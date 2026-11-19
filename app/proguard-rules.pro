# Keep useful crash-report details while allowing R8 to optimize release code.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,EnclosingMethod,InnerClasses

# JNI calls use exact Java names and signatures from the native audio engine.
-keep class com.zeekoltd.setat.pro.NativeAudioEngine { *; }

# Firebase Realtime Database maps these model fields by their exact JSON keys.
# Keep only the model classes actually passed to DataSnapshot.getValue(Class).
-keep,allowoptimization class com.zeekoltd.setat.pro.**$SetModelV2 { *; }
-keep,allowoptimization class com.zeekoltd.setat.pro.SearchActivity$SetModelSearch { *; }
-keep,allowoptimization class com.zeekoltd.setat.pro.Them2026Activity$ThemeModel { *; }
-keep,allowoptimization class com.zeekoltd.setat.pro.SupportChatActivity$SupportChatMessage { *; }

# Preserve the Firebase support-room data object used by the admin screen.
-keep,allowoptimization class com.zeekoltd.setat.pro.SupportRoom { *; }

# MainActivity invokes these MovingBorderView methods by reflection.
-keepclassmembers class com.zeekoltd.setat.pro.MovingBorderView {
    public void setCornerRadius(float);
    public void setGlowColor(java.lang.String);
    public void setContinuousMode(boolean);
}

# Existing Pangle optional class references reported by R8 for this mediation build.
-dontwarn com.bytedance.sdk.openadsdk.core.model.NetExtParams$RenderType
-dontwarn com.bytedance.sdk.openadsdk.core.settings.TTSdkSettings$FETCH_REQUEST_SOURCE

# Yandex mediation and its adapters provide their own consumer keep rules.
# Avoid package-wide keep rules here: they prevent shrinking, optimization, and obfuscation.
