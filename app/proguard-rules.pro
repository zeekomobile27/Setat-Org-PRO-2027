# ==========================================
# 1. إعدادات التشفير الأساسية والحفاظ على التوقيعات
# ==========================================
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,EnclosingMethod,InnerClasses

# ==========================================
# 2. حماية محرك الصوت C++ (JNI)
# ==========================================
# NativeAudioEngine is kept in full below so JNI method names remain unchanged.
-keep class com.zeekoltd.setat.pro.NativeAudioEngine { *; }

# ==========================================
# 3. Yandex Mobile Ads, mediation adapters, and AppMetrica
# ==========================================
-keep class com.yandex.mobile.ads.** { *; }
-keep class com.yandex.ads.mediation.** { *; }
-keep class io.appmetrica.analytics.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ==========================================
# 4. خدمات مصادقة وقاعدة بيانات فايربيس
# ==========================================
-keep class com.google.firebase.** { *; }
-keep class com.firebaseui.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# حماية دوال قراءة البيانات لفايربيس
-keepclassmembers class * {
  public <init>();
}

# ==========================================
# 5. الحماية الشاملة المانعة للانهيار (حماية الشاشات واللغات)
# ==========================================
# حماية جميع الأنشطة (Activities) من إعادة التوجيه الخاطئ عند تغيير اللغة
-keep public class * extends androidx.appcompat.app.AppCompatActivity {
    public void *(android.view.View);
    public void recreate();
}

# حماية جميع الكلاسات الداخلية، النماذج، والمحولات (Adapters)
-keep class com.zeekoltd.setat.pro.**$* { *; }
-keep class com.zeekoltd.setat.pro.SupportRoom { *; }
-keep interface com.zeekoltd.setat.pro.** { *; }

# حماية تفضيلات اللغة والتخزين المؤقت (SharedPreferences & Cache) لمنع تلف البيانات عند التبديل
-keepclassmembers class com.zeekoltd.setat.pro.** {
    java.lang.String currentLang;
    void applyLanguage();
    void refreshLanguageSmoothly();
}

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

# R8: Pangle SDK missing-class references reported during release shrinking.
-dontwarn com.bytedance.sdk.openadsdk.core.model.NetExtParams$RenderType
-dontwarn com.bytedance.sdk.openadsdk.core.settings.TTSdkSettings$FETCH_REQUEST_SOURCE

# ==========================================
# 10. شبكات Yandex Mediation 8.6.0.0
# ==========================================
# احتفظ بأسماء الكلاسات والأعضاء التي قد تستدعيها محولات الشبكات انعكاسياً.
# موارد كل الشبكات محفوظة عبر shrinkResources false في app/build.gradle.
-keep class com.applovin.** { *; }
-keep class com.appnext.** { *; }
-keep class sg.bigo.ads.** { *; }
-keep class com.chartboost.** { *; }
-keep class com.fyber.** { *; }
-keep class com.jirbo.adcolony.** { *; }
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.google.android.libraries.ads.** { *; }
-keep class com.inmobi.** { *; }
-keep class com.ironsource.** { *; }
-keep class com.vungle.** { *; }
-keep class com.mbridge.** { *; }
-keep class com.mintegral.** { *; }
-keep class com.bytedance.** { *; }
-keep class com.ss.android.** { *; }
-keep class com.startapp.** { *; }
-keep class com.tapjoy.** { *; }
-keep class com.unity3d.** { *; }
-keep class com.my.target.** { *; }
-keep class com.vk.** { *; }
-keep class com.moloco.** { *; }
-keep class com.zmaticoo.** { *; }

# احفظ كود التطبيق ونماذج/حقول Firebase وتسجيل الدخول بالمعرّفات كما هي.
# بيانات Firebase وقواعدها على الخادم ليست شيفرة R8؛ القواعد لا تُحذف من التطبيق.
-keep class com.zeekoltd.setat.pro.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }
