# ==========================================
# 1. إعدادات التشفير القصوى (لرفع نسبة التشفير وصعوبة الهندسة العكسية)
# ==========================================
-repackageclasses ''
-allowaccessmodification
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,EnclosingMethod

# ==========================================
# 2. حماية دوال C++ (JNI) ومحرك الصوت Oboe (مهم جداً)
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
# 4. خدمات فايربيس وتسجيل الدخول
# ==========================================
-keep class com.google.firebase.** { *; }
-keep class com.firebaseui.** { *; }
# حماية دوال قراءة وكتابة البيانات في Firebase لتجنب انهيار التطبيق عند جلب البيانات
-keepclassmembers class * {
  public <init>();
}

# ==========================================
# 5. مكتبة Glide للصور
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
# 6. مكتبة Media3 (ExoPlayer)
# ==========================================
-keep class androidx.media3.** { *; }

# ==========================================
# 7. مكتبات واجهة المستخدم (BlurView، Konfetti، و uCrop)
# ==========================================
-keep class eightbitlab.com.blurview.** { *; }
-keep class nl.dionsegijn.konfetti.** { *; }
-keep class com.yalantis.ucrop.** { *; }

# ==========================================
# 8. حماية عناصر الواجهة المرتبطة بـ XML (لمنع انهيار التصميم)
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
