package com.zeekoltd.setat.pro;

import android.app.Dialog;
import android.widget.FrameLayout;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;

public class ProfileDialogHelper {

    private static Dialog currentDialog;
    private static ImageView currentProfileImage;
    private static TextView currentProfileName;
    private static View currentLogoutButton;
    private static View currentDeleteButton;

    public static void showProfileDialog(MainActivity activity, String currentLang) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            EdgeToEdgeHelper.enable(window);
        }

        dialog.setContentView(R.layout.dialog_profile);
        currentDialog = dialog;
        
        View dialogRoot = dialog.findViewById(R.id.dialogRootView);
        View innerLayout = dialog.findViewById(R.id.dialogInnerLayout);
        if (dialogRoot != null && innerLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(dialogRoot, (v, insets) -> {
                float d = activity.getResources().getDisplayMetrics().density;
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                int topInset = bars.top;
                int bottomInset = bars.bottom;
                if (topInset == 0 && activity.getWindow() != null) {
                    WindowInsetsCompat actInsets = ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
                    if (actInsets != null) {
                        Insets actBars = actInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                        topInset = actBars.top;
                        bottomInset = actBars.bottom;
                    }
                }
                innerLayout.setPadding(
                        (int)(16 * d), 
                        topInset + (int)(20 * d), 
                        (int)(16 * d), 
                        bottomInset + (int)(20 * d)
                );
                return insets;
            });
            ViewCompat.requestApplyInsets(dialogRoot);
        }

        FrameLayout blurDialogRoot = dialog.findViewById(R.id.blurDialogRoot);
        View decorView = activity.getWindow().getDecorView();

        View btnBack = dialog.findViewById(R.id.dialogBtnBack);
        TextView tvTitle = dialog.findViewById(R.id.dialogTvTitle);
        currentProfileImage = dialog.findViewById(R.id.dialogProfileImage);
        ImageView cameraIcon = dialog.findViewById(R.id.dialogCameraIcon);
        currentProfileName = dialog.findViewById(R.id.dialogProfileName);
        View editNameTrigger = dialog.findViewById(R.id.dialogEditNameTrigger);
        View editNameContainer = dialog.findViewById(R.id.dialogEditNameContainer);
        EditText etName = dialog.findViewById(R.id.dialogEtName);
        Button btnSaveName = dialog.findViewById(R.id.dialogBtnSaveName);
        
        View btnSupport = dialog.findViewById(R.id.dialogBtnSupport);
        TextView tvSupport = dialog.findViewById(R.id.dialogTvSupport);
        View btnWebsite = dialog.findViewById(R.id.dialogBtnWebsite);
        TextView tvWebsite = dialog.findViewById(R.id.dialogTvWebsite);
        View btnPrivacy = dialog.findViewById(R.id.dialogBtnPrivacy);
        TextView tvPrivacy = dialog.findViewById(R.id.dialogTvPrivacy);
        View btnAbout = dialog.findViewById(R.id.dialogBtnAbout);
        TextView tvAbout = dialog.findViewById(R.id.dialogTvAbout);
        
        View btnLogout = dialog.findViewById(R.id.dialogBtnLogout);
        TextView tvLogout = dialog.findViewById(R.id.dialogTvLogout);
        View btnDeleteAccount = dialog.findViewById(R.id.dialogBtnDeleteAccount);
        currentLogoutButton = btnLogout;
        currentDeleteButton = btnDeleteAccount;
        TextView tvDeleteAccount = dialog.findViewById(R.id.dialogTvDeleteAccount);
        
        TextView tvDev = dialog.findViewById(R.id.dialogTvDev);
        TextView tvRights = dialog.findViewById(R.id.dialogTvRights);
        TextView tvVersion = dialog.findViewById(R.id.dialogTvVersion);

        tvTitle.setText(activity.tr("الملف الشخصي", "Profile"));

        etName.setHint(activity.tr("أدخل اسمك", "Enter your name"));
        btnSaveName.setText(activity.tr("حفظ الاسم", "Save Name"));
        tvSupport.setText(activity.tr("الدعم", "Support"));
        if (tvWebsite != null) tvWebsite.setText(activity.tr("الموقع", "Website"));
        tvPrivacy.setText(activity.tr("الخصوصية", "Privacy"));
        tvAbout.setText(activity.tr("حول التطبيق", "About"));
        tvLogout.setText(activity.tr("تسجيل الخروج", "Logout"));
        tvDeleteAccount.setText(activity.tr("حذف الحساب نهائياً", "Delete Account Permanently"));
        tvDev.setText(activity.tr("تطوير : ZEEKO ORG APPS LTD", "Developer: ZEEKO ORG APPS LTD"));
        tvRights.setText(activity.tr("جميع الحقوق محفوظة © 2026", "All rights reserved © 2026"));

        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            String version = pInfo.versionName;
            tvVersion.setText(activity.tr("رقم الإصدار: ", "Version: ") + version);
        } catch (Exception e) {
            tvVersion.setText(activity.tr("رقم الإصدار: غير معروف", "Version: Unknown"));
        }

        btnBack.setOnClickListener(v -> dialog.dismiss());

        FirebaseUser user = activity.mAuth.getCurrentUser();
        if (user != null) {
            DialogHelper.loadProfileImageIntoView(activity, currentProfileImage, user.getUid());
            
            String displayName = user.getDisplayName();
            if (displayName == null || displayName.isEmpty()) displayName = activity.tr("مستخدم", "User");
            currentProfileName.setText(displayName);
            etName.setText(displayName);

            View.OnClickListener pickImageClick = v -> activity.pickProfileImage();
            currentProfileImage.setOnClickListener(pickImageClick);
            cameraIcon.setOnClickListener(pickImageClick);

            editNameTrigger.setOnClickListener(v -> {
                if (editNameContainer.getVisibility() == View.GONE) {
                    editNameContainer.setVisibility(View.VISIBLE);
                    etName.requestFocus();
                } else {
                    editNameContainer.setVisibility(View.GONE);
                }
            });

            btnSaveName.setOnClickListener(v -> {
                String newName = etName.getText().toString().trim();
                if (newName.isEmpty()) {
                    Toast.makeText(activity, activity.tr("الاسم لا يمكن أن يكون فارغاً", "Name cannot be empty"), Toast.LENGTH_SHORT).show();
                    return;
                }
                user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(newName).build())
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("displayName").setValue(newName);
                            currentProfileName.setText(newName);
                            editNameContainer.setVisibility(View.GONE);
                            Toast.makeText(activity, activity.tr("تم حفظ الاسم بنجاح", "Name saved successfully"), Toast.LENGTH_SHORT).show();
                        }
                    });
            });

            btnLogout.setOnClickListener(v -> {
                dialog.dismiss();
                DialogHelper.showLogoutConfirmDialog(activity, currentLang, () -> {
                    FirebaseAuth.getInstance().signOut();
                    activity.clearGoogleCredentialState();
                    activity.refreshAccountUi();
                    Toast.makeText(activity, activity.tr("تم تسجيل الخروج", "Logged out"), Toast.LENGTH_SHORT).show();
                });
            });

            btnDeleteAccount.setOnClickListener(v -> {
                dialog.dismiss();
                showDeleteAccountDialog(activity, currentLang, user);
            });

        } else {
            currentProfileImage.setImageResource(R.drawable.ic_default_user);
            currentProfileName.setText(activity.tr("مستخدم", "User"));
            btnLogout.setVisibility(View.GONE);
            btnDeleteAccount.setVisibility(View.GONE);

            View.OnClickListener loginClick = v -> {
                dialog.dismiss();
                DialogHelper.showLoginDialog(activity, currentLang);
            };
            
            currentProfileImage.setOnClickListener(loginClick);
            cameraIcon.setOnClickListener(loginClick);
            editNameTrigger.setOnClickListener(loginClick);
        }

        btnSupport.setOnClickListener(v -> {
            dialog.dismiss();
            activity.showLoadingAndNavigate();
        });

        if (btnWebsite != null) {
            btnWebsite.setOnClickListener(v -> {
                activity.openUrlInBrowser("https://www.zeekoltd.com");
            });
        }

        btnPrivacy.setOnClickListener(v -> DialogHelper.showPrivacyOptionsDialog(
                activity,
                currentLang,
                () -> activity.openUrlInBrowser("https://www.zeekoltd.com/privacy.html"),
                () -> AdMobConsentManager.showPrivacyOptionsForm(activity, (shown, errorMessage) -> {
                    if (!shown) {
                        String message = errorMessage != null && !errorMessage.isEmpty()
                                ? activity.tr("تعذر فتح خيارات إعلانات Google: ", "Could not open Google ads privacy choices: ") + errorMessage
                                : activity.tr("لا تتطلب إعدادات الإعلانات الحالية نموذج خيارات إضافياً.",
                                        "No additional ad privacy form is currently required.");
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    }
                })));

        btnAbout.setOnClickListener(v -> {
            DialogHelper.showAboutDialog(activity, currentLang);
        });

        dialog.show();
    }

    public static void updateProfileImageInDialog(MainActivity activity, String uid) {
        if (currentDialog != null && currentDialog.isShowing() && currentProfileImage != null) {
            DialogHelper.loadProfileImageIntoView(activity, currentProfileImage, uid);
        }
    }

    /** Updates the visible profile dialog without dismissing/recreating it. */
    public static void refreshAccountUi(MainActivity activity) {
        if (currentDialog == null || !currentDialog.isShowing()) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (currentProfileImage != null) {
                currentProfileImage.setImageResource(R.drawable.ic_default_user);
                currentProfileImage.setOnClickListener(v -> {
                    if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();
                    String lang = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .getString("app_language", "ar");
                    DialogHelper.showLoginDialog(activity, lang);
                });
            }
            if (currentProfileName != null) {
                currentProfileName.setText(activity.tr("مستخدم", "User"));
            }
            if (currentLogoutButton != null) currentLogoutButton.setVisibility(View.GONE);
            if (currentDeleteButton != null) currentDeleteButton.setVisibility(View.GONE);
        } else {
            if (currentProfileImage != null) {
                DialogHelper.loadProfileImageIntoView(activity, currentProfileImage, user.getUid());
            }
            if (currentProfileName != null) {
                String name = user.getDisplayName();
                currentProfileName.setText(name == null || name.trim().isEmpty()
                        ? activity.tr("مستخدم", "User") : name.trim());
            }
            if (currentLogoutButton != null) currentLogoutButton.setVisibility(View.VISIBLE);
            if (currentDeleteButton != null) currentDeleteButton.setVisibility(View.VISIBLE);
        }
    }

    public static void refreshDialogDataIfOpen(MainActivity activity) {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            SharedPreferences prefs = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            String lang = prefs.getString("app_language", "ar");
            showProfileDialog(activity, lang);
        }
    }

    private static void showDeleteAccountDialog(MainActivity activity, String currentLang, FirebaseUser user) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
        }

        View baseView = LayoutInflater.from(activity).inflate(R.layout.dialog_base_glass, null);
        dialog.setContentView(baseView);
        
        FrameLayout blurCenterWindow = baseView.findViewById(R.id.blurCenterWindow);
        
        MovingBorderView centerMovingBorder = baseView.findViewById(R.id.centerMovingBorder);
        if (centerMovingBorder != null) {
            centerMovingBorder.setCornerRadius(14f);
            centerMovingBorder.setGlowColor("#FF4C4C");
            centerMovingBorder.setContinuousMode(true);
        }

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        TextView tvDesc = baseView.findViewById(R.id.tvDialogDescription);
        
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(activity.tr("تحذير خطير!", "Warning!"));
            tvTitle.setTextColor(Color.parseColor("#FF4C4C"));
        }
        
        if (tvDesc != null) {
            tvDesc.setVisibility(View.VISIBLE);
            tvDesc.setText(activity.tr("هل أنت متأكد أنك تريد حذف حسابك نهائياً؟\nسيتم مسح جميع بياناتك ولا يمكن التراجع.", 
                           "Are you sure you want to delete your account?\nAll data will be erased and cannot be recovered."));
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        if (dynamicContainer != null) {
            View btnConfirmView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, dynamicContainer, false);
            TextView tvConfirm = btnConfirmView.findViewById(R.id.tvButtonText);
            tvConfirm.setText(activity.tr("نعم، احذف حسابي", "Yes, Delete"));
            tvConfirm.setTextColor(Color.parseColor("#FF4C4C"));
            
            View btnCancelView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, dynamicContainer, false);
            TextView tvCancel = btnCancelView.findViewById(R.id.tvButtonText);
            tvCancel.setText(activity.tr("تراجع", "Cancel"));

            btnConfirmView.setOnClickListener(v -> {
                dialog.dismiss();
                executeAccountDeletion(activity, currentLang, user);
            });
            btnCancelView.setOnClickListener(v -> dialog.dismiss());
            
            dynamicContainer.addView(btnConfirmView);
            dynamicContainer.addView(btnCancelView);
        }

        dialog.setCancelable(true);
        dialog.show();
    }

    private static void executeAccountDeletion(MainActivity activity, String currentLang, FirebaseUser user) {
        Dialog loading = DialogHelper.showLoadingDialog(activity, currentLang, activity.tr("جاري حذف الحساب...", "Deleting account..."));
        String uid = user.getUid();

        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        db.child("users").child(uid).removeValue();
        db.child("user_avatars").child(uid).removeValue();
        db.child("user_avatars_metadata").child(uid).removeValue();
        
        File localFile = new File(activity.getFilesDir(), "avatar_" + uid + ".jpg");
        if (localFile.exists()) localFile.delete();

        user.delete().addOnCompleteListener(task -> {
            loading.dismiss();
            if (task.isSuccessful()) {
                FirebaseAuth.getInstance().signOut();
                activity.clearGoogleCredentialState();
                activity.refreshAccountUi();
                Toast.makeText(activity, activity.tr("تم حذف الحساب نهائياً", "Account deleted permanently"), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, activity.tr("الرجاء تسجيل الخروج والدخول مجدداً ثم المحاولة", "Please re-login and try again"), Toast.LENGTH_LONG).show();
                FirebaseAuth.getInstance().signOut();
                activity.clearGoogleCredentialState();
                activity.refreshAccountUi();
            }
        });
    }
}