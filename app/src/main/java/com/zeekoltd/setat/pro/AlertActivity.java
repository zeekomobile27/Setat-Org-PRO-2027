package com.zeekoltd.setat.pro;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.browser.customtabs.CustomTabsIntent;

import java.util.Locale;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class AlertActivity extends AppCompatActivity {
    
    private String currentLang = "ar";
    private SharedPreferences langPrefs;
    private LinearLayout bannerContainer;
    private LinearLayout contentLayout;
    private View mainContentView;
    
    private String tr(String arabicText, String englishText) {
        return "en".equals(currentLang) ? englishText : arabicText;
    }
    
    private void applyLanguage() {
        langPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentLang = langPrefs.getString("app_language", "ar");
        
        Locale locale = new Locale(currentLang);
        Locale.setDefault(locale);
        Resources resources = getResources();
        android.content.res.Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        
        if ("en".equals(currentLang)) {
            config.setLayoutDirection(Locale.ENGLISH);
        } else {
            config.setLayoutDirection(Locale.forLanguageTag("ar"));
        }
        
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        applyLanguage();
        EdgeToEdgeHelper.enable(getWindow());
        setContentView(R.layout.alert);
        initializeLogic();
        
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });
    }

    private void initializeLogic() {
        mainContentView = findViewById(R.id.mainContentView);
        contentLayout = findViewById(R.id.contentLayout);
        bannerContainer = findViewById(R.id.bannerContainer);
        
        // 🌟 حماية الواجهة لكي لا تتداخل مع شريط إشعارات الهاتف 🌟
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            ViewCompat.setOnApplyWindowInsetsListener(mainContentView, (v, insets) -> {
                View blurTopBarAlert = findViewById(R.id.blurTopBarAlert);
                float d = getResources().getDisplayMetrics().density;
                
                if (blurTopBarAlert != null) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) blurTopBarAlert.getLayoutParams();
                    params.topMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top + (int)(15 * d);
                    blurTopBarAlert.setLayoutParams(params);
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(mainContentView);
        }
        
        setupBlurViews();
        setupUI();
        setupEventListeners();
        setupNetworkReceiver();
    }

    // 🌟 تفعيل الزجاج للشريط العلوي 🌟
    private void setupBlurViews() {
        ViewGroup brightTarget = findViewById(R.id.brightBackgroundTarget);
        if (brightTarget == null) brightTarget = (ViewGroup) mainContentView;
        if (brightTarget == null) return;
        
        BlurView blurTopBarAlert = findViewById(R.id.blurTopBarAlert);
        
        if (blurTopBarAlert != null) {
            blurTopBarAlert.setupWith(BlurTargetHelper.ensure(brightTarget))
                .setBlurRadius(15f)
                .setBlurEnabled(true);
        }
    }

    private void setupUI() {
        final float density = getResources().getDisplayMetrics().density;
        
        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) {
            tvTitle.setText(tr("الإشعارات 🔔", "Notifications 🔔"));
        }
        
        TextView allNotificationsTitle = new TextView(this);
        LinearLayout.LayoutParams titleLayoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleLayoutParams.gravity = Gravity.CENTER;
        titleLayoutParams.setMargins(0, (int)(10 * density), 0, (int)(20 * density));
        allNotificationsTitle.setLayoutParams(titleLayoutParams);
        allNotificationsTitle.setText(tr("جميع الإشعارات", "All Notifications"));
        allNotificationsTitle.setTextColor(Color.parseColor("#00FFFF")); // متطابق مع الزجاج الموحد
        allNotificationsTitle.setTextSize(16);
        allNotificationsTitle.setTypeface(null, Typeface.BOLD);
        allNotificationsTitle.setShadowLayer(2, 1, 1, Color.BLACK);
        contentLayout.addView(allNotificationsTitle);
        
        try {
            SharedPreferences sharedPreferences = getSharedPreferences("notifications", MODE_PRIVATE);
            String notifications = sharedPreferences.getString("notification_list", "");
            
            if (notifications.isEmpty()) {
                TextView noNotifications = new TextView(this);
                noNotifications.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                noNotifications.setText(tr("لا توجد إشعارات حتى الآن 📭", "No notifications yet 📭"));
                noNotifications.setTextColor(Color.WHITE);
                noNotifications.setTextSize(16);
                noNotifications.setTypeface(null, Typeface.BOLD);
                noNotifications.setGravity(Gravity.CENTER);
                noNotifications.setPadding(0, (int)(50 * density), 0, 0);
                contentLayout.addView(noNotifications);
            } else {
                String[] notificationList = notifications.split("\\n\\n---\\n\\n");
                
                TextView countText = new TextView(this);
                countText.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                countText.setText(tr("عدد الإشعارات: ", "Number of notifications: ") + notificationList.length);
                countText.setTextColor(Color.parseColor("#E6E6E6"));
                countText.setTextSize(14);
                countText.setTypeface(null, Typeface.BOLD);
                countText.setGravity(Gravity.CENTER);
                countText.setPadding(0, 0, 0, (int)(15 * density));
                contentLayout.addView(countText);
                
                // 🌟 تحويل كروت الإشعارات للزجاج الموحد برمجياً 🌟
                ViewGroup brightTarget = findViewById(R.id.brightBackgroundTarget);
                
                for (int i = 0; i < notificationList.length; i++) {
                    String notification = notificationList[i];
                    if (!notification.trim().isEmpty()) {
                        
                        BlurView blurCard = new BlurView(this);
                        LinearLayout.LayoutParams blurParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        blurParams.setMargins(0, 0, 0, (int)(15 * density));
                        blurCard.setLayoutParams(blurParams);
                        blurCard.setBackgroundResource(R.drawable.bg_glass_border);
                        blurCard.setClipToOutline(true);
                        
                        if (brightTarget != null) {
                            blurCard.setupWith(BlurTargetHelper.ensure(brightTarget))
                                    .setBlurRadius(15f)
                                    .setBlurEnabled(true);
                        }

                        LinearLayout notificationCard = new LinearLayout(this);
                        notificationCard.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ));
                        notificationCard.setOrientation(LinearLayout.VERTICAL);
                        notificationCard.setPadding((int)(15 * density), (int)(15 * density), (int)(15 * density), (int)(15 * density));
                        
                        TextView notificationNumber = new TextView(this);
                        notificationNumber.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ));
                        notificationNumber.setText(tr("الإشعار #", "Notification #") + (notificationList.length - i));
                        notificationNumber.setTextColor(Color.parseColor("#00FFFF"));
                        notificationNumber.setTextSize(12);
                        notificationNumber.setTypeface(null, Typeface.BOLD);
                        notificationNumber.setPadding(0, 0, 0, (int)(5 * density));
                        notificationNumber.setShadowLayer(2, 1, 1, Color.BLACK);
                        
                        TextView notificationView = new TextView(this);
                        notificationView.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ));
                        notificationView.setText(notification);
                        notificationView.setTextColor(Color.WHITE);
                        notificationView.setTextSize(14);
                        notificationView.setLineSpacing((int)(4 * density), 1);
                        
                        notificationCard.addView(notificationNumber);
                        notificationCard.addView(notificationView);
                        
                        blurCard.addView(notificationCard);
                        
                        contentLayout.addView(blurCard);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupEventListeners() {
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnClear = findViewById(R.id.btnClear);
        
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }
        
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> showClearDialog());
        }
    }

    // 🌟 تحويل نافذة المسح للتصميم الزجاجي الموحد 🌟
    private void showClearDialog() {
        final float d = getResources().getDisplayMetrics().density;
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.70f);
        }

        View baseView = LayoutInflater.from(this).inflate(R.layout.dialog_base_glass, null);
        dialog.setContentView(baseView);
        
        BlurView blurCenterWindow = baseView.findViewById(R.id.blurCenterWindow);
        View decorView = getWindow().getDecorView();
        ViewGroup targetView = decorView.findViewById(R.id.brightBackgroundTarget);
        if (targetView == null) {
            targetView = (ViewGroup) decorView.findViewById(android.R.id.content);
        }
        if (blurCenterWindow != null && targetView != null) {
            blurCenterWindow.setupWith(BlurTargetHelper.ensure(targetView)).setBlurRadius(15f).setBlurEnabled(true);
        }

        MovingBorderView centerMovingBorder = baseView.findViewById(R.id.centerMovingBorder);
        if (centerMovingBorder != null) {
            centerMovingBorder.setCornerRadius(14f);
            centerMovingBorder.setGlowColor("#FF4C4C"); 
            centerMovingBorder.setContinuousMode(true);
        }

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(tr("مسح الإشعارات", "Clear Notifications"));
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        if (dynamicContainer != null) {
            
            TextView messageView = new TextView(this);
            messageView.setText(tr("هل تريد مسح جميع الإشعارات؟", "Do you want to clear all notifications?"));
            messageView.setTextColor(Color.WHITE);
            messageView.setTextSize(14);
            messageView.setTypeface(null, Typeface.BOLD);
            messageView.setGravity(Gravity.CENTER);
            messageView.setPadding(0, 0, 0, (int)(25 * d));
            dynamicContainer.addView(messageView);

            // زر الموافقة الكبسولي
            View btnYesView = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, dynamicContainer, false);
            TextView tvYesText = btnYesView.findViewById(R.id.tvButtonText);
            if (tvYesText != null) {
                tvYesText.setText(tr("نعم", "Yes"));
                tvYesText.setTextColor(Color.parseColor("#FF4C4C"));
            }
            btnYesView.setOnClickListener(v -> {
                SharedPreferences sharedPreferences = getSharedPreferences("notifications", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.remove("notification_list");
                editor.apply();
                Toast.makeText(AlertActivity.this, tr("تم مسح جميع الإشعارات", "All notifications cleared"), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                finish();
                startActivity(getIntent());
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
            LinearLayout.LayoutParams btnYesParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnYesParams.bottomMargin = (int)(10 * d);
            dynamicContainer.addView(btnYesView, btnYesParams);

            // زر الإلغاء الكبسولي
            View btnNoView = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, dynamicContainer, false);
            TextView tvNoText = btnNoView.findViewById(R.id.tvButtonText);
            if (tvNoText != null) {
                tvNoText.setText(tr("لا", "No"));
                tvNoText.setTextColor(Color.WHITE);
            }
            btnNoView.setOnClickListener(v -> dialog.dismiss());
            dynamicContainer.addView(btnNoView);
        }

        dialog.setCancelable(true);
        dialog.show();
    }

    private void setupNetworkReceiver() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                if (connectivityManager != null) {
                    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                    boolean isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
                    if (!isConnected) {
                        startActivity(new Intent(getApplicationContext(), MainActivity.class));
                        finish();
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
    }
}