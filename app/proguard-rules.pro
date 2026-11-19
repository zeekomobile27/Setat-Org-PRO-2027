# Keep stack traces readable; these attributes do not keep whole app classes.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,EnclosingMethod,InnerClasses

# JNI exports use the exact class and native method names in AudioEngine.cpp.
# Keep those names only; Java helper members remain eligible for R8.
-keep class com.zeekoltd.setat.pro.NativeAudioEngine {
    native <methods>;
}

# Firebase Realtime Database uses these classes via getValue(Model.class).
# Preserve only the no-argument constructors and public JSON field names.
# Model class names and unused methods remain eligible for R8 renaming/optimization.
-keepclassmembers,allowoptimization class com.zeekoltd.setat.pro.**$SetModelV2 {
    public <init>();
    public <fields>;
}

-keepclassmembers,allowoptimization class com.zeekoltd.setat.pro.SearchActivity$SetModelSearch {
    public <init>();
    public <fields>;
}

-keepclassmembers,allowoptimization class com.zeekoltd.setat.pro.Them2026Activity$ThemeModel {
    public <init>();
    public <fields>;
}

-keepclassmembers,allowoptimization class com.zeekoltd.setat.pro.SupportChatActivity$SupportChatMessage {
    public <init>();
    public <fields>;
}

# Layout XML names this custom view; app code also reflects on these three methods.
-keepnames class com.zeekoltd.setat.pro.MovingBorderView
-keepclassmembers,allowoptimization class com.zeekoltd.setat.pro.MovingBorderView {
    public <init>(android.content.Context, android.util.AttributeSet);
    public void setCornerRadius(float);
    public void setGlowColor(java.lang.String);
    public void setContinuousMode(boolean);
}

# Optional Pangle types reported missing by R8; these suppress warnings only.
-dontwarn com.bytedance.sdk.openadsdk.core.model.NetExtParams$RenderType
-dontwarn com.bytedance.sdk.openadsdk.core.settings.TTSdkSettings$FETCH_REQUEST_SOURCE

# Keep the merged rule export used by CI for diagnosis.
-printconfiguration build/outputs/mapping/release/r8-merged-configuration.txt
