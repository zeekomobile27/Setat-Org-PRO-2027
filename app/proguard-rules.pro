# ==========================================
# 1. إعدادات أساسية آمنة للتشفير
# تم إزالة الأوامر المدمرة لهيكلة الحزم للحفاظ على استقرار التطبيق
# ==========================================
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,EnclosingMethod

# ==========================================
# 2. حماية دوال C++ (JNI) ومحرك الصوت Oboe
# ==========================================
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.zeekoltd.setat.pro.NativeAudioEngine { *; }

# ==========================================
# 3. إعلانات جوجل AdMob وموافقات المستخدمين (UMP)
# ==========================================
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ==========================================
# 4. خدمات فايربيس وتسجيل الدخول (لحل مشكلة الدخول)
# ==========================================
-keep class com.google.firebase.** { *; }
-keep class com.firebaseui.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# ==========================================
# 5. حماية نماذج البيانات (Models) لفايربيس (لحل مشكلة فراغ السيتات وانهيار المحادثة)
# ==========================================
-keep class com.zeekoltd.setat.pro.**$SetModelV2 { *; }
-keep class com.zeekoltd.setat.pro.**$SupportChatMessage { *; }
-keep class com.zeekoltd.setat.pro.SupportRoom { *; }
-keep class com.zeekoltd.setat.pro.**$SupportRoom { *; }

# ==========================================
# 6. مكتبة Glide للصور
# ==========================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>();
}
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ==========================================
# 7. مكتبة Media3 (ExoPlayer)
# ==========================================
-keep class androidx.media3.** { *; }

# ==========================================
# 8. مكتبات واجهة المستخدم
# ==========================================
-keep class eightbitlab.com.blurview.** { *; }
-keep class nl.dionsegijn.konfetti.** { *; }
-keep class com.yalantis.ucrop.** { *; }

# ==========================================
# 9. حماية عناصر الواجهة ودورات الحياة
# ==========================================
-keepclassmembers class * extends android.content.Context {
   public void *(android.view.View);
   public void *(android.view.MenuItem);
}
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
