package com.zeekoltd.setat.pro;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import java.util.Locale;

public class LanguageActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Dialog languageDialog;
    private Dialog privacyDialog;
    private String selectedLanguage = "";
    
    private static final String LANG_AR = "ar";
    private static final String LANG_EN = "en";
    private static final String PREFS_LANG_KEY = "app_language";
    private static final String LANGUAGE_SELECTED_KEY = "language_selected";
    private static final String PRIVACY_ACCEPTED_KEY = "privacy_accepted";
    private String currentLang = "ar";

    private String tr(String arabicText, String englishText) {
        return "en".equals(currentLang) ? englishText : arabicText;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdgeHelper.enable(getWindow());

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        editor = prefs.edit();
        currentLang = prefs.getString("app_language", "ar");

        buildScreenUI();
        checkInitialStates();
    }
    private void buildScreenUI() {
        RelativeLayout mainLayout = new RelativeLayout(this);
        mainLayout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));

        // 1. الهدف الساطع (brightBackgroundTarget) ليعكسه الزجاج
        FrameLayout brightTarget = new FrameLayout(this);
        brightTarget.setId(R.id.brightBackgroundTarget); // يجب تعريفه في ids.xml أو استخدامه كـ id ديناميكي، سنستخدمه كما هو.
        brightTarget.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));

        ImageView backgroundImage = new ImageView(this);
        backgroundImage.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

        try {
            int resourceId = getResources().getIdentifier("vntrocreen", "drawable", getPackageName());
            if (resourceId != 0) {
                backgroundImage.setImageResource(resourceId);
            } else {
                backgroundImage.setBackgroundColor(Color.parseColor("#121620"));
            }
        } catch (Exception e) {
            backgroundImage.setBackgroundColor(Color.parseColor("#121620"));
        }

        brightTarget.addView(backgroundImage);
        mainLayout.addView(brightTarget);

        // 2. التعتيم המوحد 70%
        View overlay = new View(this);
        overlay.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        overlay.setBackgroundColor(Color.parseColor("#B3000000")); 
        mainLayout.addView(overlay);

        // 3. حماية الـ Insets
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, insets) -> insets);
            ViewCompat.requestApplyInsets(mainLayout);
        }

        setContentView(mainLayout);
    }

    private void checkInitialStates() {
        boolean isLanguageSelected = prefs.getBoolean(LANGUAGE_SELECTED_KEY, false);
        boolean isPrivacyAccepted = prefs.getBoolean(PRIVACY_ACCEPTED_KEY, false);
        String savedLanguage = prefs.getString(PREFS_LANG_KEY, "");

        if (isLanguageSelected && !savedLanguage.isEmpty()) {
            applyLanguage(savedLanguage);
            if (isPrivacyAccepted) {
                // استدعاء فحص موافقة جوجل قبل الانتقال
                checkGoogleConsentAndProceed();
            } else {
                new Handler(Looper.getMainLooper()).postDelayed(this::showPrivacyPolicyDialog, 500);
            }
        } else {
            new Handler(Looper.getMainLooper()).postDelayed(this::showLanguageSelectionDialog, 500);
        }
    }

    // موافقة إعلانات Google تُدار مركزياً قبل تهيئة الإعلانات في AdMobAdsHelper.
    // لا نربط عرض نموذج UMP بشاشة اللغة حتى يظهر أيضاً للمستخدمين الحاليين.
    private void checkGoogleConsentAndProceed() {
        startInternetCheckActivity();
    }

    // 🌟 تحويل نافذة اللغة للزجاج الموحد 🌟
    private void showLanguageSelectionDialog() {
        float d = getResources().getDisplayMetrics().density;
        
        RadioGroup languageGroup = new RadioGroup(this);
        languageGroup.setOrientation(LinearLayout.VERTICAL);
        languageGroup.setGravity(Gravity.CENTER);
        languageGroup.setPadding(0, 0, 0, (int)(15*d));

        LinearLayout englishOptionLayout = createLanguageOption(d, "English", LANG_EN);
        final RadioButton englishRadio = (RadioButton) englishOptionLayout.getChildAt(0);
        
        LinearLayout arabicOptionLayout = createLanguageOption(d, "العربية", LANG_AR);
        final RadioButton arabicRadio = (RadioButton) arabicOptionLayout.getChildAt(0);

        languageGroup.addView(englishOptionLayout);
        languageGroup.addView(arabicOptionLayout);

        arabicRadio.setChecked(true);
        selectedLanguage = LANG_AR;

        View.OnClickListener engClickListener = v -> {
            englishRadio.setChecked(true);
            arabicRadio.setChecked(false);
            selectedLanguage = LANG_EN;
        };

        View.OnClickListener arClickListener = v -> {
            arabicRadio.setChecked(true);
            englishRadio.setChecked(false);
            selectedLanguage = LANG_AR;
        };

        englishOptionLayout.setOnClickListener(engClickListener);
        englishRadio.setOnClickListener(engClickListener);
        arabicOptionLayout.setOnClickListener(arClickListener);
        arabicRadio.setOnClickListener(arClickListener);

        // استخدام زر كبسولي موحد
        View btnView = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, null);
        TextView tvBtnText = btnView.findViewById(R.id.tvButtonText);
        if (tvBtnText != null) {
            tvBtnText.setText("موافق / OK");
            tvBtnText.setTextColor(Color.parseColor("#00FFFF"));
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(languageGroup);
        container.addView(btnView);
        languageDialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        languageDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (languageDialog.getWindow() != null) {
            languageDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            languageDialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            languageDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            languageDialog.getWindow().setDimAmount(0.70f);
        }

        View baseView = LayoutInflater.from(this).inflate(R.layout.dialog_base_glass, null);
        languageDialog.setContentView(baseView);

        MovingBorderView centerMovingBorder = baseView.findViewById(R.id.centerMovingBorder);
        if (centerMovingBorder != null) {
            centerMovingBorder.setCornerRadius(14f);
            centerMovingBorder.setGlowColor("#00FFFF"); 
            centerMovingBorder.setContinuousMode(true); 
        }

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText("اختر لغة / Choose Language");
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        if (dynamicContainer != null) {
            dynamicContainer.addView(container);
        }

        btnView.setOnClickListener(v -> {
            if (!selectedLanguage.isEmpty()) {
                currentLang = selectedLanguage; 
                saveLanguage(selectedLanguage);
                applyLanguage(selectedLanguage);
                languageDialog.dismiss();
                showPrivacyPolicyDialog();
            } else {
                Toast.makeText(LanguageActivity.this, "الرجاء اختيار لغة / Please choose a language", Toast.LENGTH_SHORT).show();
            }
        });

        languageDialog.setCancelable(false);
        languageDialog.show();
    }

    // 🌟 تحويل نافذة الخصوصية للزجاج الموحد 🌟
    private void showPrivacyPolicyDialog() {
        float density = getResources().getDisplayMetrics().density;
        
        privacyDialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        privacyDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (privacyDialog.getWindow() != null) {
            privacyDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            privacyDialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            privacyDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            privacyDialog.getWindow().setDimAmount(0.70f);
        }

        View baseView = LayoutInflater.from(this).inflate(R.layout.dialog_base_glass, null);
        privacyDialog.setContentView(baseView);

        MovingBorderView centerMovingBorder = baseView.findViewById(R.id.centerMovingBorder);
        if (centerMovingBorder != null) {
            centerMovingBorder.setCornerRadius(14f);
            centerMovingBorder.setGlowColor("#00FFFF"); 
            centerMovingBorder.setContinuousMode(true); 
        }

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(tr("سياسة الخصوصية", "Privacy Policy"));
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        if (dynamicContainer != null) {
            
            TextView messageView = new TextView(this);
            LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            msgParams.setMargins(0, 0, 0, (int) (20 * density));
            messageView.setLayoutParams(msgParams);
            messageView.setText(tr(
                    "مرحباً بك في الإصدار الاحترافي (سيتات أورج PRO). نلتزم بأعلى معايير الأمان لحماية بياناتك، يرجى قراءة سياسة الخصوصية والموافقة عليها للمتابعة.",
                    "Welcome to the professional edition (Setat Org PRO). We are committed to the highest security standards to protect your data. Please read and agree to our privacy policy to continue."
            ));
            messageView.setTextColor(Color.WHITE);
            messageView.setTextSize(14);
            messageView.setLineSpacing((int)(5*density), 1);
            messageView.setGravity(Gravity.CENTER);
            dynamicContainer.addView(messageView);

            // زر قراءة السياسة
            View btnReadView = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, dynamicContainer, false);
            TextView tvReadText = btnReadView.findViewById(R.id.tvButtonText);
            if (tvReadText != null) {
                tvReadText.setText(tr("قراءة السياسة", "Read Policy"));
            }
            btnReadView.setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.zeekoltd.com/privacy.html"));
                startActivity(browserIntent);
            });
            LinearLayout.LayoutParams btnReadParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnReadParams.bottomMargin = (int)(10 * density);
            dynamicContainer.addView(btnReadView, btnReadParams);

            // زر الموافقة
            View btnAgreeView = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, dynamicContainer, false);
            TextView tvAgreeText = btnAgreeView.findViewById(R.id.tvButtonText);
            if (tvAgreeText != null) {
                tvAgreeText.setText(tr("موافق", "Agree"));
                tvAgreeText.setTextColor(Color.parseColor("#00FFFF"));
            }
            btnAgreeView.setOnClickListener(v -> {
                editor.putBoolean(PRIVACY_ACCEPTED_KEY, true);
                editor.apply();
                privacyDialog.dismiss();
                // استدعاء فحص موافقة جوجل بدلاً من الانتقال المباشر
                checkGoogleConsentAndProceed();
            });
            dynamicContainer.addView(btnAgreeView);
        }

        privacyDialog.setCancelable(false);
        privacyDialog.show();
    }

    private LinearLayout createLanguageOption(float density, String text, String tag) {
        LinearLayout layout = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (int) (50 * density));
        params.setMargins(0, 0, 0, (int) (10 * density));
        layout.setLayoutParams(params);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setBackgroundColor(Color.TRANSPARENT);

        RadioButton radio = new RadioButton(this);
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        radioParams.setMargins(0, 0, (int) (10 * density), 0);
        radio.setLayoutParams(radioParams);
        // زر التحديد بلون متوهج
        radio.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#00FFFF")));
        radio.setTag(tag);
        radio.setClickable(false); 

        TextView textView = new TextView(this);
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        textView.setText(text);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(16);
        textView.setTypeface(textView.getTypeface(), Typeface.BOLD);

        layout.addView(radio);
        layout.addView(textView);
        return layout;
    }

    private void saveLanguage(String languageCode) {
        editor.putString(PREFS_LANG_KEY, languageCode);
        editor.putBoolean(LANGUAGE_SELECTED_KEY, true);
        editor.apply();
    }

    private void applyLanguage(String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);

        if ("en".equals(languageCode)) {
            config.setLayoutDirection(Locale.ENGLISH);
        } else {
            config.setLayoutDirection(Locale.forLanguageTag("ar"));
        }

        DisplayMetrics metrics = resources.getDisplayMetrics();
        resources.updateConfiguration(config, metrics);
    }

    private void startInternetCheckActivity() {
        Intent intent = new Intent(LanguageActivity.this, MainactivityActivity.class);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public static void applySavedLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", MODE_PRIVATE);
        String lang = prefs.getString("app_language", LANG_AR);

        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);

        if ("en".equals(lang)) {
            config.setLayoutDirection(Locale.ENGLISH);
        } else {
            config.setLayoutDirection(Locale.forLanguageTag("ar"));
        }

        DisplayMetrics metrics = resources.getDisplayMetrics();
        resources.updateConfiguration(config, metrics);
    }
}