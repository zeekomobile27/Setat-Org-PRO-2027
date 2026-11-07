package com.zeekoltd.setat.pro;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class DialogHelper {
    private static String tr(String lang, String ar, String en) { return "en".equals(lang) ? en : ar; }
    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static ImageView dialogProfileImageView = null;

    // دالة داخلية خاصة لبناء القاعدة الزجاجية المضيئة الموحدة لكل النوافذ
    private static Dialog createBaseGlassDialog(Context context, View customView, String title, String description) {
        final Dialog dialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            
            // --- التعتيم الاحترافي خلف النافذة ---
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f); // تعتيم بنسبة 65%
        }

        View baseView = LayoutInflater.from(context).inflate(R.layout.dialog_base_glass, null);
        dialog.setContentView(baseView);

        BlurView blurCenterWindow = baseView.findViewById(R.id.blurCenterWindow);
        Activity activity = (Activity) context;
        View decorView = activity.getWindow().getDecorView();
        
        // 🌟 التعديل السحري: البحث عن الهدف الساطع أولاً لتجاهل التعتيم 🌟
        ViewGroup targetView = decorView.findViewById(R.id.brightBackgroundTarget);
        if (targetView == null) {
            targetView = (ViewGroup) decorView.findViewById(android.R.id.content);
        }
        
        if (blurCenterWindow != null && targetView != null) {
            blurCenterWindow.setupWith(BlurTargetHelper.ensure(targetView))
                    .setBlurRadius(15f)
                    .setBlurEnabled(true);
        }

        MovingBorderView centerMovingBorder = baseView.findViewById(R.id.centerMovingBorder);
        if (centerMovingBorder != null) {
            centerMovingBorder.setCornerRadius(14f);
            centerMovingBorder.setGlowColor("#00FFFF"); 
            centerMovingBorder.setContinuousMode(true); 
        }

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        TextView tvDesc = baseView.findViewById(R.id.tvDialogDescription);

        if (title != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(title);
        }
        if (description != null) {
            tvDesc.setVisibility(View.VISIBLE);
            tvDesc.setText(description);
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        if (dynamicContainer != null && customView != null) {
            dynamicContainer.addView(customView);
        }

        return dialog;
    }

    public static void showLanguageDialog(final MainActivity activity, final String lang, final AdMobAdsHelper ads) {
        float d = activity.getResources().getDisplayMetrics().density;
        
        RadioGroup group = new RadioGroup(activity);
        group.setGravity(Gravity.CENTER);
        group.setPadding(0, 0, 0, (int)(15*d));

        RadioButton rbEn = new RadioButton(activity); 
        rbEn.setId(View.generateViewId()); 
        rbEn.setText("English"); 
        rbEn.setTextColor(Color.WHITE); 
        rbEn.setTypeface(null, Typeface.BOLD);
        
        RadioButton rbAr = new RadioButton(activity); 
        rbAr.setId(View.generateViewId()); 
        rbAr.setText("العربية"); 
        rbAr.setTextColor(Color.WHITE); 
        rbAr.setTypeface(null, Typeface.BOLD);
        
        if ("en".equals(lang)) rbEn.setChecked(true); else rbAr.setChecked(true);
        group.addView(rbEn); 
        group.addView(rbAr);

        View btnView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, null);
        TextView tvBtnText = btnView.findViewById(R.id.tvButtonText);
        if (tvBtnText != null) tvBtnText.setText(tr(lang, "موافق", "OK"));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(group);
        container.addView(btnView);

        final Dialog dialog = createBaseGlassDialog(activity, container, tr(lang, "اختر لغة / Choose Language", "Choose Language"), null);

        btnView.setOnClickListener(v -> {
            String sel = rbAr.isChecked() ? "ar" : "en";
            activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putString("app_language", sel).apply();
            if (ads != null) ads.setCurrentLanguage(sel);
            dialog.dismiss();
            Intent intent = activity.getIntent();
            activity.finish();
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        });

        dialog.show();
    }

    public static void showPrivacyOptionsDialog(final MainActivity activity, final String lang,
                                                final Runnable onPolicySelected,
                                                final Runnable onGooglePrivacySelected) {
        View policyButton = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, null);
        TextView policyText = policyButton.findViewById(R.id.tvButtonText);
        if (policyText != null) {
            policyText.setText(tr(lang, "سياسة خصوصية التطبيق", "App privacy policy"));
        }

        View googleButton = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, null);
        TextView googleText = googleButton.findViewById(R.id.tvButtonText);
        if (googleText != null) {
            googleText.setText(tr(lang, "خيارات خصوصية إعلانات Google", "Google ads privacy choices"));
        }

        View closeButton = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, null);
        TextView closeText = closeButton.findViewById(R.id.tvButtonText);
        if (closeText != null) {
            closeText.setText(tr(lang, "إغلاق", "Close"));
        }

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(policyButton);
        container.addView(googleButton);
        container.addView(closeButton);

        final Dialog dialog = createBaseGlassDialog(
                activity, container,
                tr(lang, "خيارات الخصوصية", "Privacy options"), null);

        policyButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPolicySelected != null) onPolicySelected.run();
        });
        googleButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (onGooglePrivacySelected != null) onGooglePrivacySelected.run();
        });
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public static void showAboutDialog(final MainActivity activity, final String lang) {
        float d = activity.getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(activity);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, (int)(250*d));
        scrollParams.bottomMargin = (int)(20*d);
        scroll.setLayoutParams(scrollParams);
        
        TextView content = new TextView(activity);
        content.setText(tr(lang, "تطبيق سيتات اورج PRO 2027\n\nالمكتبة الموسيقية الأضخم والأكثر احترافية للإيقاعات والسيتات الحصرية.\nتم تطوير وإدارة هذا التطبيق رسمياً بواسطة شركة ZEEKO ORG APPS LTD.\n\nنلتزم بتقديم محتوى صوتي عالي الجودة وحصري لدعم صناع الموسيقى والعازفين حول العالم.\n\nجميع حقوق الطبع والنشر والملكية الفكرية محفوظة © 2026.", 
                                 "Setat ORG PRO 2027 App\n\nThe largest and most professional musical library for exclusive rhythms and sets.\nOfficially developed and managed by ZEEKO ORG APPS LTD.\n\nWe are committed to delivering high-quality, exclusive audio content to support music creators and players worldwide.\n\nAll copyrights and intellectual property rights are reserved © 2026."));
        content.setTextColor(Color.WHITE);
        content.setTextSize(14);
        content.setTypeface(null, Typeface.BOLD);
        content.setGravity(Gravity.CENTER);
        content.setLineSpacing((int)(5*d), 1);
        scroll.addView(content);

        View btnView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, null);
        TextView tvBtnText = btnView.findViewById(R.id.tvButtonText);
        if (tvBtnText != null) tvBtnText.setText(tr(lang, "إغلاق", "Close"));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(scroll);
        container.addView(btnView);

        final Dialog dialog = createBaseGlassDialog(activity, container, tr(lang, "حول التطبيق", "About App"), null);
        btnView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public static void showExitDialog(final Activity activity, final String lang) {
        View btnYesView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, null);
        TextView tvYesText = btnYesView.findViewById(R.id.tvButtonText);
        if (tvYesText != null) {
            tvYesText.setText(tr(lang, "نعم", "Yes"));
            tvYesText.setTextColor(Color.parseColor("#FF4C4C"));
        }
        btnYesView.setOnClickListener(v -> { AdMobAdsHelper.destroyInstance(); activity.finish(); });

        View btnNoView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, null);
        TextView tvNoText = btnNoView.findViewById(R.id.tvButtonText);
        if (tvNoText != null) tvNoText.setText(tr(lang, "لا", "No"));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(btnYesView);
        container.addView(btnNoView);

        final Dialog dialog = createBaseGlassDialog(activity, container, tr(lang, "هل تريد الخروج من التطبيق؟", "Do you want to exit the app?"), null);
        btnNoView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public static void showLogoutConfirmDialog(final MainActivity activity, final String lang, final Runnable onConfirm) {
        View btnYesView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, null);
        TextView tvYesText = btnYesView.findViewById(R.id.tvButtonText);
        if (tvYesText != null) {
            tvYesText.setText(tr(lang, "نعم", "Yes"));
            tvYesText.setTextColor(Color.parseColor("#FF4C4C"));
        }

        View btnNoView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, null);
        TextView tvNoText = btnNoView.findViewById(R.id.tvButtonText);
        if (tvNoText != null) tvNoText.setText(tr(lang, "لا", "No"));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(btnYesView);
        container.addView(btnNoView);

        final Dialog dialog = createBaseGlassDialog(activity, container, tr(lang, "هل تريد تسجيل الخروج؟", "Do you want to logout?"), null);
        
        btnYesView.setOnClickListener(v -> { 
            dialog.dismiss(); 
            if (onConfirm != null) onConfirm.run(); 
        });
        btnNoView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // 🌟 نافذة الدخول المحدثة كلياً لتعتمد على الزجاج المضيء بكامل الشاشة 🌟
    public static void showLoginDialog(final Context context, final String lang) {
        final Dialog dialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
        }

        // استخدام واجهة الدخول كواجهة رئيسية للنافذة
        View loginRootView = LayoutInflater.from(context).inflate(R.layout.dialog_login, null);
        dialog.setContentView(loginRootView);

        Activity activity = (Activity) context;
        View decorView = activity.getWindow().getDecorView();
        
        ViewGroup targetView = loginRootView.findViewById(R.id.brightBackgroundTarget);
        if (targetView == null) {
            targetView = decorView.findViewById(R.id.brightBackgroundTarget);
        }
        if (targetView == null) {
            targetView = (ViewGroup) decorView.findViewById(android.R.id.content);
        }
        
        // ربط كافة طبقات الزجاج في النافذة الجديدة بالخلفية
        int[] blurIds = {
            R.id.blurTopBarLogin, R.id.blurTabContainer, 
            R.id.blurBtnLogin, R.id.blurBtnGoogleLogin, 
            R.id.blurBtnRegister, R.id.blurBtnGoogleRegister, 
            R.id.blurBtnCancel
        };
        for (int id : blurIds) {
            BlurView bv = loginRootView.findViewById(id);
            if (bv != null && targetView != null) {
                bv.setupWith(BlurTargetHelper.ensure(targetView))
                  .setBlurRadius(15f)
                  .setBlurEnabled(true);
            }
        }

        LinearLayout btnChangeLangLogin = loginRootView.findViewById(R.id.btnChangeLangLogin);
        TextView tvAppName = loginRootView.findViewById(R.id.tvAppName);
        TextView tabLogin = loginRootView.findViewById(R.id.tabLogin);
        TextView tabRegister = loginRootView.findViewById(R.id.tabRegister);
        LinearLayout loginContent = loginRootView.findViewById(R.id.loginContent);
        LinearLayout registerContent = loginRootView.findViewById(R.id.registerContent);
        EditText etEmail = loginRootView.findViewById(R.id.etEmail);
        EditText etPassword = loginRootView.findViewById(R.id.etPassword);
        ImageView togglePassword = loginRootView.findViewById(R.id.togglePassword);
        
        // الأزرار تم تحويلها في التصميم الجديد إلى LinearLayout 
        LinearLayout btnLogin = loginRootView.findViewById(R.id.btnLogin);
        LinearLayout btnGoogleLogin = loginRootView.findViewById(R.id.btnGoogleLogin);
        EditText etRegName = loginRootView.findViewById(R.id.etRegName);
        EditText etRegEmail = loginRootView.findViewById(R.id.etRegEmail);
        EditText etRegPass = loginRootView.findViewById(R.id.etRegPass);
        EditText etRegConfirmPass = loginRootView.findViewById(R.id.etRegConfirmPass);
        LinearLayout btnRegister = loginRootView.findViewById(R.id.btnRegister);
        LinearLayout btnGoogleRegister = loginRootView.findViewById(R.id.btnGoogleRegister);
        LinearLayout btnCancel = loginRootView.findViewById(R.id.btnCancel);

        tvAppName.setText(tr(lang, "سيتات اورج 2026", "Setat Org 2026"));
        tabLogin.setText(tr(lang, "تسجيل الدخول", "Login"));
        tabRegister.setText(tr(lang, "إنشاء حساب", "Register"));
        etEmail.setHint(tr(lang, "البريد الإلكتروني", "Email"));
        etPassword.setHint(tr(lang, "كلمة المرور", "Password"));
        
        if (btnLogin != null && btnLogin.getChildAt(0) instanceof TextView) {
            ((TextView) btnLogin.getChildAt(0)).setText(tr(lang, "تسجيل الدخول", "Login"));
        }
        if (btnGoogleLogin != null && btnGoogleLogin.getChildAt(1) instanceof TextView) {
            ((TextView) btnGoogleLogin.getChildAt(1)).setText(tr(lang, "تسجيل الدخول عبر جوجل", "Login with Google"));
        }

        etRegName.setHint(tr(lang, "الاسم الكامل", "Full Name"));
        etRegEmail.setHint(tr(lang, "البريد الإلكتروني", "Email"));
        etRegPass.setHint(tr(lang, "كلمة المرور", "Password"));
        etRegConfirmPass.setHint(tr(lang, "تأكيد كلمة المرور", "Confirm Password"));
        
        if (btnRegister != null && btnRegister.getChildAt(0) instanceof TextView) {
            ((TextView) btnRegister.getChildAt(0)).setText(tr(lang, "إنشاء حساب", "Register"));
        }
        if (btnGoogleRegister != null && btnGoogleRegister.getChildAt(1) instanceof TextView) {
            ((TextView) btnGoogleRegister.getChildAt(1)).setText(tr(lang, "إنشاء الحساب عبر جوجل", "Register with Google"));
        }
        if (btnCancel != null && btnCancel.getChildAt(0) instanceof TextView) {
            ((TextView) btnCancel.getChildAt(0)).setText(tr(lang, "إلغاء", "Cancel"));
        }
        if (btnChangeLangLogin != null && btnChangeLangLogin.getChildAt(1) instanceof TextView) {
            ((TextView) btnChangeLangLogin.getChildAt(1)).setText(tr(lang, "اللغة", "Language"));
        }

        btnChangeLangLogin.setOnClickListener(v -> {
            if (context instanceof MainActivity) {
                dialog.dismiss();
                AdMobAdsHelper ads = AdMobAdsHelper.getInstance((MainActivity) context);
                showLanguageDialog((MainActivity) context, lang, ads);
            }
        });

        togglePassword.setOnClickListener(v -> {
            if (etPassword.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePassword.setImageResource(R.drawable.ic_eye);
            } else {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePassword.setImageResource(R.drawable.ic_eye_off);
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        tabLogin.setOnClickListener(v -> {
            tabLogin.setBackgroundResource(R.drawable.bg_glass_capsule);
            tabLogin.setTextColor(Color.WHITE);
            tabRegister.setBackgroundColor(Color.TRANSPARENT);
            tabRegister.setTextColor(Color.parseColor("#A6FFFFFF"));
            loginContent.setVisibility(View.VISIBLE);
            registerContent.setVisibility(View.GONE);
        });

        tabRegister.setOnClickListener(v -> {
            tabRegister.setBackgroundResource(R.drawable.bg_glass_capsule);
            tabRegister.setTextColor(Color.WHITE);
            tabLogin.setBackgroundColor(Color.TRANSPARENT);
            tabLogin.setTextColor(Color.parseColor("#A6FFFFFF"));
            loginContent.setVisibility(View.GONE);
            registerContent.setVisibility(View.VISIBLE);
        });

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(context, tr(lang, "املأ جميع الحقول", "Fill all fields"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (pass.length() < 6) {
                Toast.makeText(context, tr(lang, "كلمة المرور 6 أحرف على الأقل", "Password must be at least 6 characters"), Toast.LENGTH_SHORT).show();
                return;
            }
            
            Dialog loadingDialog = showLoadingDialog(context, lang, tr(lang, "جاري تسجيل الدخول...", "Logging in..."));
            
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    loadingDialog.dismiss();
                    if (task.isSuccessful()) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) {
                            saveAccountToPrefs(context, user.getUid(), user.getEmail(), user.getDisplayName());
                        }
                        dialog.dismiss();
                        Toast.makeText(context, tr(lang, "تم تسجيل الدخول بنجاح", "Logged in successfully"), Toast.LENGTH_SHORT).show();
                    } else {
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "";
                        if (errorMsg.contains("no user record") || errorMsg.contains("There is no user record")) {
                            Toast.makeText(context, tr(lang, "لا يوجد حساب بهذا البريد", "No account with this email"), Toast.LENGTH_SHORT).show();
                        } else if (errorMsg.contains("password is invalid") || errorMsg.contains("INVALID_PASSWORD")) {
                            Toast.makeText(context, tr(lang, "كلمة المرور غير صحيحة", "Incorrect password"), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, tr(lang, "فشل تسجيل الدخول", "Login failed"), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        });

        View.OnClickListener googleClick = v -> {
            dialog.dismiss();
            if (context instanceof MainActivity) {
                ((MainActivity) context).startGoogleSignIn();
            }
        };
        btnGoogleLogin.setOnClickListener(googleClick);
        btnGoogleRegister.setOnClickListener(googleClick);

        btnRegister.setOnClickListener(v -> {
            String name = etRegName.getText().toString().trim();
            String email = etRegEmail.getText().toString().trim();
            String pass = etRegPass.getText().toString().trim();
            String confirmPass = etRegConfirmPass.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(context, tr(lang, "املأ جميع الحقول", "Fill all fields"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (pass.length() < 6) {
                Toast.makeText(context, tr(lang, "كلمة المرور 6 أحرف على الأقل", "Password must be at least 6 characters"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!pass.equals(confirmPass)) {
                Toast.makeText(context, tr(lang, "كلمتا المرور غير متطابقتين", "Passwords do not match"), Toast.LENGTH_SHORT).show();
                return;
            }

            Dialog loadingDialog = showLoadingDialog(context, lang, tr(lang, "جاري إنشاء الحساب...", "Creating account..."));

            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) {
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                                .build();
                            user.updateProfile(profileUpdates).addOnCompleteListener(updateTask -> {
                                DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
                                usersRef.child(user.getUid()).child("displayName").setValue(name);
                                usersRef.child(user.getUid()).child("email").setValue(email);
                                
                                saveAccountToPrefs(context, user.getUid(), email, name);
                                loadingDialog.dismiss();
                                dialog.dismiss();
                                Toast.makeText(context, tr(lang, "تم إنشاء الحساب بنجاح", "Account created successfully"), Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        loadingDialog.dismiss();
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "";
                        if (errorMsg.contains("email address is already in use") || errorMsg.contains("EMAIL_EXISTS")) {
                            Toast.makeText(context, tr(lang, "البريد مستخدم بالفعل", "Email already in use"), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, tr(lang, "فشل إنشاء الحساب", "Registration failed"), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public static void showLoginDialog(final Context context, final String lang, final int backgroundRes) {
        showLoginDialog(context, lang);
    }

    public static Dialog showLoadingDialog(Context context, String lang, String message) {
        final Dialog loadingDialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        loadingDialog.setCancelable(false);
        
        Window window = loadingDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            
            // --- التعتيم الاحترافي خلف النافذة ---
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
        }
        
        View baseView = LayoutInflater.from(context).inflate(R.layout.dialog_base_glass, null);
        loadingDialog.setContentView(baseView);

        BlurView blurCenterWindow = baseView.findViewById(R.id.blurCenterWindow);
        Activity activity = (Activity) context;
        View decorView = activity.getWindow().getDecorView();
        
        // 🌟 التعديل السحري: البحث عن الهدف الساطع أولاً لتجاهل التعتيم 🌟
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
            centerMovingBorder.setGlowColor("#00FFFF");
            centerMovingBorder.setContinuousMode(true);
        }

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(message);
        }

        float d = context.getResources().getDisplayMetrics().density;
        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        progressBar.getIndeterminateDrawable().setColorFilter(Color.parseColor("#00FFFF"), android.graphics.PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams((int)(45*d), (int)(45*d));
        progressBar.setLayoutParams(pbParams);

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        if (dynamicContainer != null) {
            dynamicContainer.addView(progressBar);
        }

        loadingDialog.show();
        return loadingDialog;
    }

    public static void saveAccountToPrefs(Context context, String uid, String email, String name) {
        if (email == null) email = "";
        if (name == null) name = "";
        
        SharedPreferences prefs = context.getSharedPreferences("saved_accounts", Context.MODE_PRIVATE);
        String accountsJson = prefs.getString("accounts_list", "[]");
        try {
            org.json.JSONArray accountsArray = new org.json.JSONArray(accountsJson);
            
            boolean exists = false;
            for (int i = 0; i < accountsArray.length(); i++) {
                if (accountsArray.getJSONObject(i).optString("uid", "").equals(uid)) {
                    exists = true;
                    accountsArray.getJSONObject(i).put("email", email);
                    accountsArray.getJSONObject(i).put("name", name);
                    break;
                }
            }
            
            if (!exists) {
                org.json.JSONObject newAccount = new org.json.JSONObject();
                newAccount.put("uid", uid);
                newAccount.put("email", email);
                newAccount.put("name", name);
                accountsArray.put(newAccount);
            }
            
            while (accountsArray.length() > 5) {
                org.json.JSONArray newArray = new org.json.JSONArray();
                for (int i = 1; i < accountsArray.length(); i++) {
                    newArray.put(accountsArray.get(i));
                }
                accountsArray = newArray;
            }
            
            prefs.edit().putString("accounts_list", accountsArray.toString()).apply();
        } catch (Exception e) {}
    }

    public static void loadProfileImageIntoView(Context context, ImageView imageView) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (user != null) ? user.getUid() : "guest";
        loadProfileImageIntoView(context, imageView, uid);
    }

    public static void loadProfileImageIntoView(Context context, ImageView imageView, String uid) {
        imageView.setImageResource(R.drawable.ic_default_user);
        File localFile = new File(context.getFilesDir(), "avatar_" + uid + ".jpg");
        
        if (localFile.exists() && localFile.length() > 0) {
            Glide.with(context)
                .load(localFile)
                .circleCrop()
                .skipMemoryCache(true)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                .placeholder(R.drawable.ic_default_user)
                .into(imageView);
        } else {
            downloadAndSaveAvatar(context, uid, imageView);
        }
    }

    private static void downloadAndSaveAvatar(Context context, String uid, ImageView imageView) {
        FirebaseDatabase.getInstance().getReference("user_avatars")
            .child(uid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    String base64 = snapshot.getValue(String.class);
                    if (base64 != null && !base64.isEmpty()) {
                        executorService.execute(() -> {
                            try {
                                byte[] decoded = Base64.decode(base64, Base64.NO_WRAP);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                                if (bitmap != null) {
                                    File localFile = new File(context.getFilesDir(), "avatar_" + uid + ".jpg");
                                    FileOutputStream fos = new FileOutputStream(localFile);
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                                    fos.close();
                                    
                                    if (context instanceof android.app.Activity) {
                                        ((android.app.Activity) context).runOnUiThread(() -> {
                                            Glide.with(context)
                                                .load(localFile)
                                                .circleCrop()
                                                .skipMemoryCache(true)
                                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                                .placeholder(R.drawable.ic_default_user)
                                                .into(imageView);
                                        });
                                    }
                                }
                            } catch (Exception e) {
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {}
            });
    }
}
