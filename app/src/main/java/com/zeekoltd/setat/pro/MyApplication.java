package com.zeekoltd.setat.pro;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// استيراد Firebase فقط
import com.google.firebase.FirebaseApp;

public class MyApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        // 🌟 فرض حجم الخط الثابت (1.0) لحماية التصميم 🌟
        super.attachBaseContext(updateResources(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        updateResources(this);

        // 🌟 تهيئة Firebase فقط (آمن ويعمل في الخلفية) 🌟
        FirebaseApp.initializeApp(this);
        
        // 🚨 تم إزالة MobileAds.initialize من هنا للامتثال لسياسات GDPR 🚨
        // سيتم تهيئته في MainActivity بعد موافقة المستخدم

        // مراقب دورة حياة الشاشات
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                updateResources(activity); // ضمان تطبيق الخط فور إنشاء أي شاشة
            }
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    // دالة تحديث الموارد وفرض مقياس الخط
    private Context updateResources(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        if (configuration.fontScale != 1.0f) {
            configuration.fontScale = 1.0f;
            return context.createConfigurationContext(configuration);
        }
        return context;
    }
}
