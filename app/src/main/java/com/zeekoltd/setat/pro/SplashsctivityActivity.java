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
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.Random;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class SplashsctivityActivity extends AppCompatActivity {
    
    private String currentLang = "ar";
    private SharedPreferences prefs;
    private boolean isTransitioned = false; 
    private BlurView blurSkipBtn;
    
    // عناصر الحركة البرمجية
    private FrameLayout particleContainer;
    private ImageView logoImage;
    private TextView mainTitle;
    private Handler particleHandler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    
    // البيانات التي ستتطاير في الشاشة
    private final String[][] setsData = {
        {"سيتات عراقية", "Iraqi Sets", "🇮🇶"},
        {"سيتات مصرية", "Egyptian Sets", "🇪🇬"},
        {"سيتات سورية", "Syrian Sets", "🇸🇾"},
        {"سيتات خليجية", "Khaleeji Sets", "🇸🇦"},
        {"سيتات مغربية", "Moroccan Sets", "🇲🇦"},
        {"سيتات احترافية", "Pro Sets", "🎹"}
    };

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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#121620"));
        }
        
        setContentView(R.layout.splashsctivity);
        
        blurSkipBtn = findViewById(R.id.blurSkipBtn);
        particleContainer = findViewById(R.id.particleContainer);
        logoImage = findViewById(R.id.logoImage);
        mainTitle = findViewById(R.id.mainTitle);

        ViewGroup root = findViewById(R.id.mainContentView);
        if (blurSkipBtn != null && root != null) {
            blurSkipBtn.setupWith(root, new RenderScriptBlur(this))
                       .setBlurRadius(15f)
                       .setBlurEnabled(true);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });

        startCinematicAnimation();

        final Handler handler = new Handler(Looper.getMainLooper());

        // الانتقال للشاشة التالية
        final Runnable transitionRunnable = () -> {
            if (!isTransitioned) {
                isTransitioned = true;
                particleHandler.removeCallbacksAndMessages(null);
                Intent intent = new Intent(SplashsctivityActivity.this, LanguageActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        };

        // زر التخطي يظهر بعد ثانيتين (2000 ms)
        handler.postDelayed(() -> {
            if (!isTransitioned && blurSkipBtn != null) {
                blurSkipBtn.setVisibility(View.VISIBLE);
                blurSkipBtn.setAlpha(0f);
                blurSkipBtn.animate().alpha(1f).setDuration(300).start();
            }
        }, 2000);

        // إجمالي الوقت 5 ثوانٍ
        handler.postDelayed(transitionRunnable, 5000);

        if (blurSkipBtn != null) {
            View.OnClickListener skipListener = v -> {
                if (!isTransitioned) {
                    handler.removeCallbacks(transitionRunnable); 
                    transitionRunnable.run();
                }
            };
            blurSkipBtn.setOnClickListener(skipListener);
            if (blurSkipBtn.getChildCount() > 0) {
                blurSkipBtn.getChildAt(0).setOnClickListener(skipListener);
            }
        }
    }

    // 🌟 محرك الحركة البرمجية السينمائية 🌟
    private void startCinematicAnimation() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        // مهمة توليد البطاقات المتطايرة (تعمل لمدة 2.5 ثانية فقط)
        Runnable particleSpawner = new Runnable() {
            int count = 0;
            @Override
            public void run() {
                if (count < 15 && !isTransitioned) { // توليد 15 بطاقة
                    spawnFlyingCard(screenWidth, screenHeight);
                    count++;
                    particleHandler.postDelayed(this, 150); // إطلاق بطاقة كل جزء من الثانية
                }
            }
        };
        particleHandler.post(particleSpawner);

        // بعد 2.5 ثانية، يظهر الشعار والاسم الأساسي بقوة
        particleHandler.postDelayed(() -> {
            if (!isTransitioned) {
                logoImage.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(800)
                         .setInterpolator(new OvershootInterpolator()).start();
                         
                mainTitle.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(800)
                         .setStartDelay(200)
                         .setInterpolator(new OvershootInterpolator()).start();
            }
        }, 2500);
    }

    private void spawnFlyingCard(int screenWidth, int screenHeight) {
        String[] data = setsData[random.nextInt(setsData.length)];
        
        // بناء البطاقة
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.bg_glass_circle_border); // استخدام خلفية الزجاج الموجودة لديك
        card.setPadding(20, 20, 20, 20);

        TextView tvFlag = new TextView(this);
        tvFlag.setText(data[2]);
        tvFlag.setTextSize(24);
        tvFlag.setGravity(Gravity.CENTER);

        TextView tvAr = new TextView(this);
        tvAr.setText(data[0]);
        tvAr.setTextColor(Color.WHITE);
        tvAr.setTextSize(14);
        tvAr.setGravity(Gravity.CENTER);

        TextView tvEn = new TextView(this);
        tvEn.setText(data[1]);
        tvEn.setTextColor(Color.parseColor("#D4AF37")); // لون ذهبي
        tvEn.setTextSize(12);
        tvEn.setGravity(Gravity.CENTER);

        card.addView(tvFlag);
        card.addView(tvAr);
        card.addView(tvEn);

        // وضع البطاقة في منتصف الشاشة بحجم صغير جداً
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        card.setLayoutParams(params);
        card.setScaleX(0.1f);
        card.setScaleY(0.1f);
        card.setAlpha(0f);
        
        particleContainer.addView(card);

        // تحديد اتجاه عشوائي للتطاير خارج الشاشة
        int targetX = (random.nextInt(screenWidth) - (screenWidth / 2)) * 2;
        int targetY = (random.nextInt(screenHeight) - (screenHeight / 2)) * 2;

        // الحركة الفائقة (Zoom out + Fade in -> Fade out)
        card.animate()
            .scaleX(2.5f).scaleY(2.5f)
            .translationX(targetX).translationY(targetY)
            .alpha(1f)
            .setDuration(1500)
            .withEndAction(() -> {
                card.animate().alpha(0f).setDuration(500).withEndAction(() -> {
                    particleContainer.removeView(card);
                }).start();
            }).start();
    }
}
