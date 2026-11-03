# ==========================================
# 1. إعدادات التشفير الأساسية
# ==========================================
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,EnclosingMethod

# ==========================================
# 2. حماية محرك الصوت C++ (JNI)
# ==========================================
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.zeekoltd.setat.pro.NativeAudioEngine { *; }

# ==========================================
# 3. إعلانات جوجل (AdMob / UMP)
# ==========================================
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ==========================================
# 4. خدمات مصادقة وقاعدة بيانات فايربيس
# ==========================================
-keep class com.google.firebase.** { *; }
-keep class com.firebaseui.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# حماية دوال قراءة البيانات لفايربيس لمنع البطاقات الفارغة
-keepclassmembers class * {
  public <init>();
}

# ==========================================
# 5. الحماية الشاملة لجميع الشاشات والنماذج (الدرع الشامل)
# يحمي جميع الكلاسات الداخلية (Models, Adapters) في جميع ملفاتك
# ==========================================
-keep class com.zeekoltd.setat.pro.**$* { *; }
-keep class com.zeekoltd.setat.pro.SupportRoom { *; }
-keep interface com.zeekoltd.setat.pro.** { *; }

# ==========================================
# 6. مكتبات الصور (Glide و uCrop)
# ==========================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>();
}
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.yalantis.ucrop.** { *; }

# ==========================================
# 7. مكتبات مشغل الصوت (Media3 / ExoPlayer)
# ==========================================
-keep class androidx.media3.** { *; }

# ==========================================
# 8. مكتبات الواجهة الرسومية (BlurView, Konfetti)
# ==========================================
-keep class eightbitlab.com.blurview.** { *; }
-keep class nl.dionsegijn.konfetti.** { *; }

# ==========================================
# 9. حماية الروابط العكسية للواجهات (Android Views)
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
