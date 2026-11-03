package com.zeekoltd.setat.pro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import java.util.Locale;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class SplashsctivityActivity extends AppCompatActivity {
    
    private String currentLang = "ar";
    private SharedPreferences prefs;
    private VideoView videoView1;
    private boolean isTransitioned = false; 
    private BlurView blurSkipBtn;

    // دالة للحصول على النص حسب اللغة
    private String tr(String arabicText, String englishText) {
        return "en".equals(currentLang) ? englishText : arabicText;
    }

    // دالة لتطبيق اللغة
    private void applyLanguage() {
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentLang = prefs.getString("app_language", "ar");

        Locale locale = new Locale(currentLang);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);

        if ("en".equals(currentLang)) {
            config.setLayoutDirection(Locale.ENGLISH);
        } else {
            config.setLayoutDirection(Locale.forLanguageTag("ar"));
        }

        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        applyLanguage();
        
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        
        setContentView(R.layout.splashsctivity);
        
        videoView1 = findViewById(R.id.videoView1);
        setupVideo();

        // إعداد وتفعيل تأثير الزجاج لزر التخطي
        blurSkipBtn = findViewById(R.id.blurSkipBtn);
        ViewGroup root = findViewById(R.id.mainContentView);
        if (blurSkipBtn != null && root != null) {
            blurSkipBtn.setupWith(root, new RenderScriptBlur(this))
                       .setBlurRadius(15f)
                       .setBlurEnabled(true);
        }

        // نظام زر الرجوع الجديد (خروج مباشر بدون نافذة تأكيد)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity(); // إغلاق التطبيق فوراً
            }
        });

        // إدارة المؤقتات الزمنية والانتقال
        final Handler handler = new Handler(Looper.getMainLooper());

        final Runnable transitionRunnable = () -> {
            if (!isTransitioned) {
                isTransitioned = true;
                if (videoView1 != null && videoView1.isPlaying()) {
                    videoView1.stopPlayback();
                }
                Intent intent = new Intent(SplashsctivityActivity.this, LanguageActivity.class);
                startActivity(intent);
                // انتقال تلاشي ناعم
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        };

        // إظهار زر التخطي الزجاجي بعد 5 ثوانٍ بتأثير ناعم
        handler.postDelayed(() -> {
            if (!isTransitioned && blurSkipBtn != null) {
                blurSkipBtn.setVisibility(View.VISIBLE);
                blurSkipBtn.setAlpha(0f);
                blurSkipBtn.animate().alpha(1f).setDuration(300).start();
            }
        }, 3000);

        // الانتقال التلقائي بعد 9 ثوانٍ
        handler.postDelayed(transitionRunnable, 8000);

        // برمجة النقر على زر التخطي الزجاجي
        if (blurSkipBtn != null) {
            View.OnClickListener skipListener = v -> {
                if (!isTransitioned) {
                    handler.removeCallbacks(transitionRunnable); 
                    isTransitioned = true;
                    if (videoView1 != null && videoView1.isPlaying()) {
                        videoView1.stopPlayback();
                    }
                    Intent intent = new Intent(SplashsctivityActivity.this, LanguageActivity.class);
                    startActivity(intent);
                    // انتقال تلاشي ناعم
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                }
            };
            
            blurSkipBtn.setOnClickListener(skipListener);
            
            // حل المشكلة: إعطاء أمر النقر أيضاً لـ LinearLayout الداخلي الذي كان يمتص اللمسة
            if (blurSkipBtn.getChildCount() > 0) {
                blurSkipBtn.getChildAt(0).setOnClickListener(skipListener);
            }
        }
    }

    private void setupVideo() {
        videoView1.setMediaController(null);
        videoView1.setClickable(false);
        videoView1.setFocusable(false);
        videoView1.setFocusableInTouchMode(false);

        String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.splash_video;
        videoView1.setVideoPath(videoPath);
        videoView1.start();
    }
}