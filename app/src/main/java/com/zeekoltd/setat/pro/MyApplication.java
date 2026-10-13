package com.zeekoltd.setat.pro;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// استيرادات التهيئة الاحترافية
import com.google.firebase.FirebaseApp;
import com.google.android.gms.ads.MobileAds;

public class MyApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        // 🌟 فرض حجم الخط الثابت (1.0) منذ لحظة بناء السياق الأساسي 🌟
        super.attachBaseContext(updateResources(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // تطبيق الخط أيضاً عند إنشاء التطبيق
        updateResources(this);

        // 🌟 التهيئة العالمية (Global Initialization) 🌟
        // هذه الأكواد قوية جداً وتعمل حتى بدون إنترنت (تضع الطلبات في طابور وتنفذها عند عودة الاتصال)
        FirebaseApp.initializeApp(this);
        
        MobileAds.initialize(this, initializationStatus -> {
            // اكتملت تهيئة محرك الإعلانات في الخلفية وهو مستعد للعمل متى ما توفر الإنترنت
        });

        // تسجيل مراقب لدورة حياة جميع الشاشات في التطبيق
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                String activityName = activity.getClass().getName();
                
                // استثناء الشاشات التي لا نريد ظهور نافذة التحديث فيها
                if (activityName.contains("UCropActivity") ||
                    activityName.contains("SplashsctivityActivity") ||
                    activityName.contains("LanguageActivity") ||
                    activityName.contains("MainactivityActivity")) {
                    return; 
                }

                // جلب اللغة الحالية وفحص التحديثات في الشاشات المسموح بها فقط
                String currentLang = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("app_language", "ar");
                new UpdateManager(activity, currentLang).checkForUpdates();
            }

            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                updateResources(activity); // ضمان تطبيق الخط فور إنشاء أي شاشة جديدة
            }
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    // دالة مساعدة لتحديث الموارد وفرض مقياس الخط
    private Context updateResources(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        if (configuration.fontScale != 1.0f) {
            configuration.fontScale = 1.0f;
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            return context.createConfigurationContext(configuration);
        }
        return context;
    }
}
