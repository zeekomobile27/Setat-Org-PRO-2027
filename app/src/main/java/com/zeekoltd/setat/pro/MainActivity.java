package com.zeekoltd.setat.pro;

import android.animation.ValueAnimator;
import android.widget.FrameLayout;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.cardview.widget.CardView;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.*;
import com.google.firebase.messaging.FirebaseMessaging;
import com.yalantis.ucrop.UCrop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import nl.dionsegijn.konfetti.core.PartyFactory;
import nl.dionsegijn.konfetti.core.Position;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.core.emitter.EmitterConfig;
import nl.dionsegijn.konfetti.xml.KonfettiView;

public class MainActivity extends AppCompatActivity {
    private String currentLang = "ar";
    private View mainContentView;
    
    private AdMobAdsHelper adMobAdsHelper;
    private static int adSkipCounter = 0;

    public FirebaseAuth mAuth; 
    private DatabaseReference usersRef;
    private CredentialManager credentialManager;
    private ImageView imgProfilePic;
    private View googleAuthBlocker;
    private boolean blurViewsConfigured = false;
    private FirebaseAuth.AuthStateListener authStateListener;
    public static final String DEV_UID = "GrWIoyYfgCVU3BJjZzfu2T0PYdo2"; 
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ActivityResultLauncher<String> pickImageLauncher; 
    private ActivityResultLauncher<String> requestPermissionLauncher;
    
    public Dialog googleSignInLoadingDialog = null; 
    private Dialog pianoLoadingDialog = null;
    private Dialog currentSmartDialog = null; 

    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
        }
        
        applyLanguage();
        EdgeToEdgeHelper.enable(getWindow());
        setContentView(R.layout.main);
        
        initActivityResultLaunchers();
        initializeLogic();
        checkAndShowWelcomeCelebration();
        checkAndShowSmartPopupDialog(); 
    }

    @Override
    public void recreate() {
        refreshLanguageSmoothly();
    }

    public void refreshLanguageSmoothly() {
        applyLanguage();
        if (adMobAdsHelper != null) {
            adMobAdsHelper.setCurrentLanguage(currentLang);
        }
        int layoutDir = "en".equals(currentLang) ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL;
        getWindow().getDecorView().setLayoutDirection(layoutDir);
        if (mainContentView != null) {
            mainContentView.setLayoutDirection(layoutDir);
        }
        setupUI();
        loadUserHeaderInfo();
        loadDynamicSetsCounts();
        setupSmartBanner();
    }

    private void checkAndShowSmartPopupDialog() {
        DatabaseReference popupRef = FirebaseDatabase.getInstance().getReference("Smart_Popup_Window");
        popupRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isDestroyed() || isFinishing() || !snapshot.exists()) return;

                boolean isVisible = false;
                if (snapshot.hasChild("is_visible")) {
                    Object visObj = snapshot.child("is_visible").getValue();
                    if (visObj != null && String.valueOf(visObj).equalsIgnoreCase("true")) {
                        isVisible = true;
                    }
                }

                if (!isVisible) {
                    if (currentSmartDialog != null && currentSmartDialog.isShowing()) {
                        currentSmartDialog.dismiss();
                    }
                    return;
                }

                String currentVersion = snapshot.hasChild("popup_version") ? String.valueOf(snapshot.child("popup_version").getValue()) : "1";
                SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                String savedVersion = prefs.getString("dismissed_popup_version", "");

                if (currentVersion.equals(savedVersion)) return;

                String titleAr = snapshot.hasChild("title_ar") ? String.valueOf(snapshot.child("title_ar").getValue()) : "";
                String titleEn = snapshot.hasChild("title_en") ? String.valueOf(snapshot.child("title_en").getValue()) : "";
                String msgAr = snapshot.hasChild("message_ar") ? String.valueOf(snapshot.child("message_ar").getValue()) : "";
                String msgEn = snapshot.hasChild("message_en") ? String.valueOf(snapshot.child("message_en").getValue()) : "";
                String actionType = snapshot.hasChild("action_type") ? String.valueOf(snapshot.child("action_type").getValue()) : "";
                String actionValue = snapshot.hasChild("action_value") ? String.valueOf(snapshot.child("action_value").getValue()) : "";

                String displayTitle = "en".equals(currentLang) && !titleEn.isEmpty() ? titleEn : titleAr;
                String displayMsg = "en".equals(currentLang) && !msgEn.isEmpty() ? msgEn : msgAr;

                displayTitle = displayTitle.replace("null", "").replace("\"", "").trim();
                displayMsg = displayMsg.replace("null", "").replace("\"", "").trim();

                showSmartDialogUi(displayTitle, displayMsg, actionType, actionValue, currentVersion);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showSmartDialogUi(String title, String message, String actionType, String actionValue, String version) {
        if (currentSmartDialog != null && currentSmartDialog.isShowing()) return; 

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.75f);
        }

        View baseView = LayoutInflater.from(this).inflate(R.layout.dialog_base_glass, null);
        dialog.setContentView(baseView);
        currentSmartDialog = dialog;

        MovingBorderView centerMovingBorder = baseView.findViewById(R.id.centerMovingBorder);
        if (centerMovingBorder != null) {
            try {
                java.lang.reflect.Method setCorner = centerMovingBorder.getClass().getMethod("setCornerRadius", float.class);
                java.lang.reflect.Method setGlow = centerMovingBorder.getClass().getMethod("setGlowColor", String.class);
                java.lang.reflect.Method setMode = centerMovingBorder.getClass().getMethod("setContinuousMode", boolean.class);
                setCorner.invoke(centerMovingBorder, 14f);
                setGlow.invoke(centerMovingBorder, "#00FFFF"); 
                setMode.invoke(centerMovingBorder, true);
            } catch (Exception e) {}
        }

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null && !title.isEmpty()) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(title);
            tvTitle.setTextColor(Color.parseColor("#FFD700")); 
        }

        TextView tvDesc = baseView.findViewById(R.id.tvDialogDescription);
        if (tvDesc != null && !message.isEmpty()) {
            tvDesc.setVisibility(View.VISIBLE);
            tvDesc.setText(message);
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        float density = getResources().getDisplayMetrics().density;

        LinearLayout btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.VERTICAL);
        btnContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        try {
            View btnProceed = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, btnContainer, false);
            LinearLayout.LayoutParams btnParams1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnParams1.bottomMargin = (int)(8 * density); 
            btnProceed.setLayoutParams(btnParams1);
            
            TextView tvBtnProceed = btnProceed.findViewById(R.id.tvButtonText);
            tvBtnProceed.setText(tr("إنتقل >>", "Proceed >>"));
            tvBtnProceed.setTextColor(Color.parseColor("#00FFFF")); 
            
            btnProceed.setOnClickListener(v -> {
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString("dismissed_popup_version", version).apply();
                dialog.dismiss();
                
                String aType = actionType.replace("null", "").replace("\"", "").trim();
                String aValue = actionValue.replace("null", "").replace("\"", "").trim();
                
                if (aType.equalsIgnoreCase("link") && !aValue.isEmpty()) openUrlInBrowser(aValue);
                else if (aType.equalsIgnoreCase("section") && !aValue.isEmpty()) {
                    try {
                        int sectionId = Integer.parseInt(aValue);
                        navigateToActivity(sectionId);
                    } catch (NumberFormatException e) {}
                }
            });
            btnContainer.addView(btnProceed);
        } catch (Exception e) {}

        try {
            View btnClose = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, btnContainer, false);
            LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnClose.setLayoutParams(btnParams2);
            
            TextView tvBtnClose = btnClose.findViewById(R.id.tvButtonText);
            tvBtnClose.setText(tr("إغلاق", "Close"));
            tvBtnClose.setTextColor(Color.parseColor("#A0A0A0")); 
            
            btnClose.setOnClickListener(v -> dialog.dismiss());
            btnContainer.addView(btnClose);
        } catch (Exception e) {}

        dynamicContainer.addView(btnContainer);
        dialog.setCancelable(false); 
        dialog.show();
    }

    private void checkAndShowWelcomeCelebration() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isShown = prefs.getBoolean("welcome_update_2027_shown", false);
        if (isShown) return;

        if (mAuth.getCurrentUser() != null) fetchNameAndCelebrate(mAuth.getCurrentUser().getUid(), prefs);
        else {
            mAuth.addAuthStateListener(new FirebaseAuth.AuthStateListener() {
                @Override
                public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user != null) {
                        firebaseAuth.removeAuthStateListener(this);
                        fetchNameAndCelebrate(user.getUid(), prefs);
                    }
                }
            });
        }
    }

    private void fetchNameAndCelebrate(String uid, SharedPreferences prefs) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("displayName");
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isDestroyed() || isFinishing()) return;
                String userName = snapshot.exists() ? snapshot.getValue(String.class) : tr("يا بطل", "Champion");
                showCelebrationDialog(userName, prefs);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showCelebrationDialog(String userName, SharedPreferences prefs) {
        if (prefs.getBoolean("welcome_update_2027_shown", false) || isFinishing()) return;

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.75f);
        }

        View baseView = LayoutInflater.from(this).inflate(R.layout.dialog_base_glass, null);
        dialog.setContentView(baseView);
        
        KonfettiView konfettiView = baseView.findViewById(R.id.konfettiViewDialog);
        if (konfettiView != null) {
            EmitterConfig emitterConfig = new Emitter(300L, TimeUnit.MILLISECONDS).max(300);
            konfettiView.start(
                new PartyFactory(emitterConfig)
                    .spread(360)
                    .colors(Arrays.asList(0xfce18a, 0xff726d, 0xf4306d, 0xb48def))
                    .setSpeedBetween(0f, 30f)
                    .position(new Position.Relative(0.5, 0.3))
                    .build()
            );
        }

        MovingBorderView centerMovingBorder = baseView.findViewById(R.id.centerMovingBorder);
        if (centerMovingBorder != null) {
            try {
                java.lang.reflect.Method setCorner = centerMovingBorder.getClass().getMethod("setCornerRadius", float.class);
                java.lang.reflect.Method setGlow = centerMovingBorder.getClass().getMethod("setGlowColor", String.class);
                java.lang.reflect.Method setMode = centerMovingBorder.getClass().getMethod("setContinuousMode", boolean.class);
                setCorner.invoke(centerMovingBorder, 14f);
                setGlow.invoke(centerMovingBorder, "#FFD700"); 
                setMode.invoke(centerMovingBorder, true);
            } catch (Exception e) {}
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        float density = getResources().getDisplayMetrics().density;

        TextView tvEmojis = new TextView(this);
        tvEmojis.setText("🎉✨🎊");
        tvEmojis.setTextSize(35);
        tvEmojis.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emojisParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        emojisParams.bottomMargin = (int)(10 * density);
        tvEmojis.setLayoutParams(emojisParams);
        dynamicContainer.addView(tvEmojis);

        TextView tvWelcomeName = new TextView(this);
        tvWelcomeName.setText(tr("مرحباً بك يا " + userName, "Welcome, " + userName));
        tvWelcomeName.setTextColor(Color.parseColor("#FFD700"));
        tvWelcomeName.setTextSize(22);
        tvWelcomeName.setTypeface(null, Typeface.BOLD);
        tvWelcomeName.setGravity(Gravity.CENTER);
        tvWelcomeName.setShadowLayer(15f, 0f, 0f, Color.parseColor("#FF8C00"));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.bottomMargin = (int)(15 * density);
        tvWelcomeName.setLayoutParams(nameParams);
        dynamicContainer.addView(tvWelcomeName);

        TextView msg = new TextView(this);
        String msgAr = "في منتدى العازفين المحترفين!\n\nيوجد ميزات وإضافات خرافية في التطبيق، استكشفها الآن واستمتع بالتجربة الجديدة كلياً 🚀";
        String msgEn = "To the Professional Musicians Forum!\n\nThere are awesome new features and additions in the app, explore them now and enjoy the completely new experience 🚀";
        msg.setText(tr(msgAr, msgEn));
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(16);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(6 * density, 1);
        msg.setShadowLayer(4f, 1f, 1f, Color.parseColor("#FF000000"));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = (int)(25 * density);
        msg.setLayoutParams(p);
        dynamicContainer.addView(msg);

        try {
            View btnExplore = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, dynamicContainer, false);
            TextView tvBtn = btnExplore.findViewById(R.id.tvButtonText);
            tvBtn.setText(tr("هيا بنا 🔥", "Let's Go 🔥"));
            tvBtn.setTextColor(Color.parseColor("#FFD700"));
            btnExplore.setOnClickListener(v -> {
                prefs.edit().putBoolean("welcome_update_2027_shown", true).apply();
                dialog.dismiss();
            });
            dynamicContainer.addView(btnExplore);
        } catch (Exception e) {}

        dialog.setCancelable(false); 
        dialog.show();
    }

    private void initActivityResultLaunchers() {
        pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) launchUCrop(uri); }
        );
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (!isGranted) showNotificationWarningDialog();
        });
    }
    
    public void dismissGoogleLoadingDialog() {
        if (googleSignInLoadingDialog != null && googleSignInLoadingDialog.isShowing()) {
            googleSignInLoadingDialog.dismiss();
            googleSignInLoadingDialog = null;
        }
    }

    private void initializeLogic() {
        mAuth = FirebaseAuth.getInstance();
        updateFcmToken();
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        credentialManager = CredentialManager.create(getApplicationContext());

        // Keep every account-related view synchronized with Firebase's actual auth state.
        authStateListener = firebaseAuth -> {
            if (isFinishing() || isDestroyed()) return;
            refreshAccountUi();
            FirebaseUser activeUser = firebaseAuth.getCurrentUser();
            if (activeUser != null) {
                FirebaseMessaging.getInstance().subscribeToTopic("logged_in_users");
            } else {
                FirebaseMessaging.getInstance().unsubscribeFromTopic("logged_in_users");
            }
        };
        
        adMobAdsHelper = AdMobAdsHelper.getInstance(this);
        adMobAdsHelper.setCurrentLanguage(currentLang);
        adMobAdsHelper.initialize(() -> runOnUiThread(() -> adMobAdsHelper.loadInterstitialAdSilent()));
        
        setupUI();
        setupFirebaseAndNetwork();
        loadProfileImage();
        loadUserHeaderInfo();
        loadDynamicSetsCounts();
        setupFrameLayouts();
        setupSmartBanner();
        
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAndRequestNotifications, 2000);
        if (mAuth.getCurrentUser() != null) FirebaseMessaging.getInstance().subscribeToTopic("logged_in_users");
    }

    public void loadUserHeaderInfo() {
        TextView tvProfileTopTitle = findViewById(R.id.tvProfileTopTitle);
        TextView tvProfileSubtitle = findViewById(R.id.tvProfileSubtitle);
        if (tvProfileSubtitle != null) {
            tvProfileSubtitle.setText(tr("مرحباً 👋🏻", "Welcome 👋🏻"));
        }
        if (tvProfileTopTitle == null || mAuth == null) return;

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            tvProfileTopTitle.setText(tr("تسجيل الدخول 👑", "Login 👑"));
            return;
        }

        final String uid = user.getUid();
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.trim().isEmpty()) {
            tvProfileTopTitle.setText(displayName.trim() + " 👑");
            return; // Firebase Auth already has the current name; avoid an unnecessary DB round-trip.
        }

        tvProfileTopTitle.setText(tr("ZEEKO ORG LTD 👑", "ZEEKO ORG LTD 👑"));
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("displayName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isDestroyed() || isFinishing()) return;
                        FirebaseUser current = mAuth.getCurrentUser();
                        if (current == null || !uid.equals(current.getUid())) return;
                        String dbName = snapshot.getValue(String.class);
                        if (dbName != null && !dbName.trim().isEmpty()) {
                            tvProfileTopTitle.setText(dbName.trim() + " 👑");
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    /** Refreshes the avatar and account name immediately after sign-in/sign-out. */
    public void refreshAccountUi() {
        if (isFinishing() || isDestroyed() || mAuth == null) return;
        loadUserHeaderInfo();
        loadProfileImage();
        ProfileDialogHelper.refreshAccountUi(this);
    }

    private void showGoogleAuthBlocker() {
        if (isFinishing() || isDestroyed()) return;
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null) return;
        if (googleAuthBlocker != null && googleAuthBlocker.getParent() != null) {
            googleAuthBlocker.bringToFront();
            return;
        }

        View blocker = new View(this);
        blocker.setBackgroundColor(Color.parseColor("#66000000"));
        blocker.setClickable(true);
        blocker.setFocusable(true);
        blocker.setOnTouchListener((v, event) -> true);
        blocker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        blocker.setAlpha(0f);
        content.addView(blocker, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        blocker.bringToFront();
        googleAuthBlocker = blocker;
        blocker.animate().alpha(1f).setDuration(120L).start();
    }

    private void hideGoogleAuthBlocker() {
        View blocker = googleAuthBlocker;
        googleAuthBlocker = null;
        if (blocker == null) return;
        blocker.animate().cancel();
        if (blocker.getParent() instanceof ViewGroup) {
            ((ViewGroup) blocker.getParent()).removeView(blocker);
        }
    }

    private void loadDynamicSetsCounts() {
        String[] nodes = {
            "exclusive_sets_v2",
            "zeeko_v4",
            "setat_shabi",
            "dabakat",
            "iraq_sets_v2",
            "egept",
            "ehtrafe",
            "dzir",
            "sori",
            "yeme",
            "them_20"
        };

        int[] countViewIds = {
            R.id.tvCountExclusive,
            R.id.tvCountSetat2,
            R.id.tvCountSetat3,
            R.id.tvCountSetat5,
            R.id.tvCountSetat6,
            R.id.tvCountSetat7,
            R.id.tvCountSetat8,
            R.id.tvCountSetat9,
            R.id.tvCountSetat10,
            R.id.tvCountSetat11,
            R.id.tvCountSetat12
        };

        for (int i = 0; i < nodes.length; i++) {
            final int viewId = countViewIds[i];
            final boolean isTheme = (i == nodes.length - 1);
            TextView tvCount = findViewById(viewId);
            if (tvCount == null) continue;

            FirebaseDatabase.getInstance().getReference(nodes[i])
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isDestroyed() || isFinishing()) return;
                        long count = snapshot.getChildrenCount();
                        if (count > 0) {
                            String unit = isTheme ? tr("ثيم", "Theme") : tr("سيت", "Set");
                            tvCount.setText("+" + count + " " + unit);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
        }
    }

    private void setupFrameLayouts() {}

    private void setupSmartBanner() {
        View blurSmartBanner = findViewById(R.id.blurSmartBanner);
        TextView tvSmartBannerText = findViewById(R.id.tvSmartBannerText);
        View btnSmartBanner = findViewById(R.id.btnSmartBanner);

        if (blurSmartBanner == null || tvSmartBannerText == null || btnSmartBanner == null) return;

        blurSmartBanner.setVisibility(View.GONE); 

        DatabaseReference smartBannerRef = FirebaseDatabase.getInstance().getReference("smart_banner");
        smartBannerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isDestroyed() || !snapshot.exists()) return;

                boolean isVisible = false;
                if (snapshot.hasChild("is_visible")) {
                    Object visObj = snapshot.child("is_visible").getValue();
                    if (visObj != null && String.valueOf(visObj).equalsIgnoreCase("true")) {
                        isVisible = true;
                    }
                }

                if (!isVisible) {
                    blurSmartBanner.setVisibility(View.GONE);
                    return;
                }

                blurSmartBanner.setVisibility(View.VISIBLE);
                btnSmartBanner.setClickable(true); 

                String msgAr = snapshot.hasChild("text_ar") ? String.valueOf(snapshot.child("text_ar").getValue()) : "";
                String msgEn = snapshot.hasChild("text_en") ? String.valueOf(snapshot.child("text_en").getValue()) : "";
                String color = snapshot.hasChild("text_color") ? String.valueOf(snapshot.child("text_color").getValue()) : "#FFFFFF";
                String actionType = snapshot.hasChild("action_type") ? String.valueOf(snapshot.child("action_type").getValue()) : "";
                String actionValue = snapshot.hasChild("action_value") ? String.valueOf(snapshot.child("action_value").getValue()) : "";

                String displayText = "en".equals(currentLang) && !msgEn.isEmpty() ? msgEn : msgAr;
                tvSmartBannerText.setText(displayText.replace("null", "").replace("\"", "").trim());
                tvSmartBannerText.setSelected(true); 

                try {
                    int parsedColor = Color.parseColor(color.replace("null", "").replace("\"", "").trim());
                    tvSmartBannerText.setTextColor(parsedColor);
                } catch (Exception e) {
                    tvSmartBannerText.setTextColor(Color.WHITE);
                }

                btnSmartBanner.setOnClickListener(v -> {
                    String aType = actionType.replace("null", "").replace("\"", "").trim();
                    String aValue = actionValue.replace("null", "").replace("\"", "").trim();
                    
                    if (aType.equalsIgnoreCase("link") && !aValue.isEmpty()) openUrlInBrowser(aValue);
                    else if (aType.equalsIgnoreCase("section") && !aValue.isEmpty()) {
                        try {
                            int sectionId = Integer.parseInt(aValue);
                            navigateToActivity(sectionId);
                        } catch (NumberFormatException e) {}
                    } else navigateWithoutAd(OrgPianoActivity.class);
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void checkAndRequestNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                
                SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                boolean isPermissionRequestedBefore = prefs.getBoolean("notification_requested_before", false);

                if (!isPermissionRequestedBefore) {
                    prefs.edit().putBoolean("notification_requested_before", true).apply();
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    if (shouldShowRationaleForPermission()) showNotificationWarningDialog();
                    else showNotificationWarningDialog();
                }
            }
        }
    }
    
    private boolean shouldShowRationaleForPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        return false;
    }

    private void showNotificationWarningDialog() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isWarningShown = prefs.getBoolean("notification_warning_shown", false);
        
        if (isWarningShown || isFinishing()) return;

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.75f);
        }

        View baseView = LayoutInflater.from(this).inflate(R.layout.dialog_base_glass, null);
        dialog.setContentView(baseView);
        
        FrameLayout blurCenterWindow = baseView.findViewById(R.id.blurCenterWindow);

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(tr("تنبيه هام جداً! ⚠️", "Important Warning! ⚠️"));
            tvTitle.setTextColor(Color.parseColor("#FF4C4C"));
            tvTitle.setShadowLayer(4f, 1f, 1f, Color.parseColor("#FF000000"));
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        float density = getResources().getDisplayMetrics().density;

        TextView msg = new TextView(this);
        String msgAr = "لقد قمت برفض السماح للإشعارات، هذا يعني أنك:\n\n❌ لن تتلقى إشعارات السيتات الحصرية الجديدة.\n❌ لن تصلك رسائل دعم المتصدرين أو الجوائز.\n❌ ستفقد جزءاً كبيراً من متعة ومميزات التطبيق.\n\nهل أنت متأكد من قرارك برفض الإشعارات؟";
        String msgEn = "You have denied notifications permission, this means:\n\n❌ You will miss out on new exclusive sets.\n❌ You won't receive support or leaderboard rewards.\n❌ You will miss a big part of the app experience.\n\nAre you sure about denying notifications?";
        
        msg.setText(tr(msgAr, msgEn));
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(14); 
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(2 * density, 1); 
        msg.setShadowLayer(4f, 1f, 1f, Color.parseColor("#FF000000"));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = (int)(15 * density); 
        msg.setLayoutParams(p);
        dynamicContainer.addView(msg);

        LinearLayout btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.VERTICAL);
        btnContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        try {
            View btnAllow = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, btnContainer, false);
            LinearLayout.LayoutParams btnParams1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnParams1.bottomMargin = (int)(8 * density); 
            btnAllow.setLayoutParams(btnParams1);
            
            TextView tvBtnAllow = btnAllow.findViewById(R.id.tvButtonText);
            tvBtnAllow.setText(tr("تفعيل الإشعارات", "Enable"));
            tvBtnAllow.setTextColor(Color.parseColor("#00FF00")); 
            
            btnAllow.setOnClickListener(v -> {
                dialog.dismiss();
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    if (shouldShowRationaleForPermission()) requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                    else {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                }
            });
            btnContainer.addView(btnAllow);
        } catch (Exception e) {}

        try {
            View btnDeny = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, btnContainer, false);
            LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnDeny.setLayoutParams(btnParams2);
            
            TextView tvBtnDeny = btnDeny.findViewById(R.id.tvButtonText);
            tvBtnDeny.setText(tr("أنا متأكد (رفض)", "I'm sure (Deny)"));
            tvBtnDeny.setTextColor(Color.parseColor("#A0A0A0")); 
            
            btnDeny.setOnClickListener(v -> {
                prefs.edit().putBoolean("notification_warning_shown", true).apply(); 
                dialog.dismiss();
            });
            btnContainer.addView(btnDeny);
        } catch (Exception e) {}

        dynamicContainer.addView(btnContainer);
        dialog.setCancelable(false); 
        dialog.show();
    }

    public void startGoogleSignIn() {
        if (isFinishing() || isDestroyed()) return;
        if (credentialManager == null) {
            credentialManager = CredentialManager.create(getApplicationContext());
        }

        // Lock the underlying screen immediately; this is an unobtrusive touch shield,
        // not a loading dialog. It stays until Google/Firebase finishes or the flow is cancelled.
        dismissGoogleLoadingDialog();
        showGoogleAuthBlocker();
        try {
            GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build();

            credentialManager.getCredentialAsync(
                    this,
                    request,
                    new android.os.CancellationSignal(),
                    ContextCompat.getMainExecutor(this),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            if (isFinishing() || isDestroyed()) return;
                            handleGoogleCredential(result.getCredential());
                        }

                        @Override
                        public void onError(@NonNull GetCredentialException e) {
                            dismissGoogleLoadingDialog();
                            hideGoogleAuthBlocker();
                            android.util.Log.e("GoogleSignIn", "Credential Manager sign-in failed", e);
                            // User cancellation is normal; keep the login dialog usable without an error toast.
                            if (e instanceof GetCredentialCancellationException) return;
                            Toast.makeText(MainActivity.this,
                                    tr("تعذر تسجيل الدخول عبر جوجل. حاول مرة أخرى.",
                                       "Google sign-in could not be completed. Please try again."),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        } catch (RuntimeException e) {
            dismissGoogleLoadingDialog();
            hideGoogleAuthBlocker();
            android.util.Log.e("GoogleSignIn", "Unable to start Credential Manager", e);
            Toast.makeText(this, tr("تعذر بدء تسجيل الدخول بجوجل", "Could not start Google Sign-In"), Toast.LENGTH_LONG).show();
        }
    }

    private void handleGoogleCredential(@NonNull Credential credential) {
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            try {
                GoogleIdTokenCredential googleCredential =
                        GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                String idToken = googleCredential.getIdToken();
                if (idToken == null || idToken.trim().isEmpty()) {
                    hideGoogleAuthBlocker();
                    Toast.makeText(this, tr("لم يتم استلام رمز الدخول من جوجل.",
                            "Google did not return an ID token."), Toast.LENGTH_LONG).show();
                    return;
                }
                firebaseAuthWithGoogle(idToken);
            } catch (Exception e) {
                hideGoogleAuthBlocker();
                android.util.Log.e("GoogleSignIn", "Unable to parse Google ID token credential", e);
                Toast.makeText(this, tr("تعذر قراءة بيانات حساب جوجل.",
                        "Could not read Google account credentials."), Toast.LENGTH_LONG).show();
            }
        } else {
            hideGoogleAuthBlocker();
            android.util.Log.w("GoogleSignIn", "Credential Manager returned an unsupported credential type: "
                    + credential.getType());
            Toast.makeText(this, tr("نوع تسجيل الدخول غير مدعوم.",
                    "Unsupported sign-in credential type."), Toast.LENGTH_LONG).show();
        }
    }

    /** Clears provider-side credential state after the user explicitly signs out. */
    public void clearGoogleCredentialState() {
        if (credentialManager == null) {
            credentialManager = CredentialManager.create(getApplicationContext());
        }
        try {
            credentialManager.clearCredentialStateAsync(
                    new ClearCredentialStateRequest(),
                    new android.os.CancellationSignal(),
                    ContextCompat.getMainExecutor(getApplicationContext()),
                    new CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override
                        public void onResult(Void result) {
                            android.util.Log.d("GoogleSignIn", "Credential state cleared after sign-out");
                        }

                        @Override
                        public void onError(@NonNull ClearCredentialException e) {
                            android.util.Log.w("GoogleSignIn", "Could not clear provider credential state", e);
                        }
                    });
        } catch (RuntimeException e) {
            android.util.Log.w("GoogleSignIn", "Unable to clear provider credential state", e);
        }
    }

    public void pickProfileImage() { pickImageLauncher.launch("image/*"); }

    private void launchUCrop(Uri sourceUri) {
        try {
            Uri destinationUri = Uri.fromFile(new File(getCacheDir(), "cropped_" + System.currentTimeMillis() + ".jpg"));
            UCrop.Options options = new UCrop.Options();
            options.setToolbarColor(Color.parseColor("#0A0F14"));
            options.setToolbarWidgetColor(Color.parseColor("#C5A059"));
            options.setToolbarTitle(tr("قص الصورة", "Crop Image"));
            options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
            options.setCompressionQuality(90);
            options.setHideBottomControls(false);
            options.setFreeStyleCropEnabled(true);
            options.setCircleDimmedLayer(true);
            options.setShowCropFrame(true);
            options.setShowCropGrid(true);
            startActivityForResult(UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1).withMaxResultSize(500, 500).withOptions(options).getIntent(this), UCrop.REQUEST_CROP);
        } catch (Exception e) {}
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                Dialog loadingDialog = DialogHelper.showLoadingDialog(this, currentLang, 
                    tr("جاري رفع الصورة...", "Uploading image..."));
                executorService.execute(() -> {
                    try {
                        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
                        boundsOptions.inJustDecodeBounds = true;
                        InputStream boundsStream = getContentResolver().openInputStream(resultUri);
                        BitmapFactory.decodeStream(boundsStream, null, boundsOptions);
                        if (boundsStream != null) boundsStream.close();

                        int reqSize = 250;
                        int inSampleSize = 1;
                        if (boundsOptions.outHeight > reqSize || boundsOptions.outWidth > reqSize) {
                            final int halfHeight = boundsOptions.outHeight / 2;
                            final int halfWidth = boundsOptions.outWidth / 2;
                            while ((halfHeight / inSampleSize) >= reqSize && (halfWidth / inSampleSize) >= reqSize) {
                                inSampleSize *= 2;
                            }
                        }

                        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
                        decodeOptions.inSampleSize = inSampleSize;
                        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        InputStream decodeStream = getContentResolver().openInputStream(resultUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions);
                        if (decodeStream != null) decodeStream.close();

                        if (bitmap != null) {
                            Bitmap resized = Bitmap.createScaledBitmap(bitmap, 250, 250, true);
                            String base64 = bitmapToBase64(resized);
                            FirebaseUser user = mAuth.getCurrentUser();
                            String uid = (user != null) ? user.getUid() : "guest";
                            saveProfileImageAsJpg(resized, uid);
                            saveProfileImageToFirebase(base64);
                            runOnUiThread(() -> {
                                loadProfileImage(); 
                                ProfileDialogHelper.updateProfileImageInDialog(this, uid);
                                loadingDialog.dismiss();
                                Toast.makeText(this, tr("تم تحديث الصورة بنجاح", "Profile image updated"), Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {}
                });
            }
        }
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private void saveProfileImageAsJpg(Bitmap bitmap, String uid) {
        try {
            File imageFile = new File(getFilesDir(), "avatar_" + uid + ".jpg");
            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
        } catch (Exception e) {}
    }

    public void loadProfileImage() { 
        imgProfilePic = findViewById(R.id.imgProfilePic);
        if (imgProfilePic == null) return;
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) DialogHelper.loadProfileImageIntoView(this, imgProfilePic, user.getUid());
        else DialogHelper.loadProfileImageIntoView(this, imgProfilePic);
    }

    private void saveProfileImageToFirebase(String base64) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            FirebaseDatabase.getInstance().getReference("user_avatars").child(uid).setValue(base64);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("lastUpdated", System.currentTimeMillis());
            FirebaseDatabase.getInstance().getReference("user_avatars_metadata").child(uid).setValue(metadata);
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (idToken == null || idToken.trim().isEmpty()) {
            dismissGoogleLoadingDialog();
            hideGoogleAuthBlocker();
            Toast.makeText(this, tr("رمز تسجيل الدخول من جوجل غير صالح", "Google ID token is missing"), Toast.LENGTH_LONG).show();
            return;
        }
        // Firebase authentication runs silently behind the same touch shield.
        mAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnCompleteListener(this, task -> {
                hideGoogleAuthBlocker();
                dismissGoogleLoadingDialog();
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        DialogHelper.saveAccountToPrefs(this, user.getUid(), user.getEmail(), user.getDisplayName());
                        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
                        userRef.child("displayName").setValue(user.getDisplayName());
                        userRef.child("email").setValue(user.getEmail());
                        FirebaseMessaging.getInstance().subscribeToTopic("logged_in_users");
                    }
                    updateFcmToken();
                    // authStateListener refreshes the home header, avatar and open profile UI.
                } else {
                    Exception error = task.getException();
                    android.util.Log.e("GoogleSignIn", "Firebase sign-in with Google credential failed", error);
                    String detail = error != null ? error.getLocalizedMessage() : null;
                    Toast.makeText(this, tr("فشل ربط حساب جوجل بفايربيس" + (detail == null ? "" : ": " + detail),
                            "Firebase sign-in failed" + (detail == null ? "" : ": " + detail)), Toast.LENGTH_LONG).show();
                }
            });
    }

    private void updateFcmToken() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    String token = task.getResult();
                    String uid = user.getUid();
                    FirebaseDatabase.getInstance().getReference("users").child(uid).child("fcm_token").setValue(token);
                    if (DEV_UID.equals(uid)) {
                        FirebaseDatabase.getInstance().getReference("admin_data").child("fcm_token").setValue(token);
                    }
                }
            });
        }
    }

    private void setupUI() {
        mainContentView = findViewById(R.id.mainContentView);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            ViewCompat.setOnApplyWindowInsetsListener(mainContentView, (v, insets) -> {
                View topBar = findViewById(R.id.topBarContainer);
                View bottomNavBar = findViewById(R.id.blurBottomNavBar);
                float d = getResources().getDisplayMetrics().density;

                int topInset = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top;
                int bottomInset = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                                | WindowInsetsCompat.Type.ime()).bottom;

                if (topBar != null) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) topBar.getLayoutParams();
                    params.topMargin = topInset + (int) (6 * d);
                    topBar.setLayoutParams(params);
                }
                if (bottomNavBar != null) {
                    ViewGroup.MarginLayoutParams bottomParams = (ViewGroup.MarginLayoutParams) bottomNavBar.getLayoutParams();
                    bottomParams.bottomMargin = bottomInset + (int) (12 * d);
                    bottomNavBar.setLayoutParams(bottomParams);
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(mainContentView);
        }

        TextView tvLangTopTitle = findViewById(R.id.tvLangTopTitle);
        if (tvLangTopTitle != null) tvLangTopTitle.setText(tr("العربية", "English"));

        TextView tvOrgVipBtnText = findViewById(R.id.tvOrgVipBtnText);
        if (tvOrgVipBtnText != null) tvOrgVipBtnText.setText(tr("أورج الملكي 2027 👑", "ORG Royal 2027 👑"));

        TextView tvOrgVipSubText = findViewById(R.id.tvOrgVipSubText);
        if (tvOrgVipSubText != null) tvOrgVipSubText.setText(tr("مع أجمل الأصوات والإيقاعات", "With the finest sounds & rhythms"));

        TextView tvOrgVipTopTitle = findViewById(R.id.tvOrgVipTopTitle);
        if (tvOrgVipTopTitle != null) tvOrgVipTopTitle.setText(tr("إبدأ العزف الآن", "Play Now"));

        TextView tvExclusiveSetTitle = findViewById(R.id.tvExclusiveSetTitle);
        if (tvExclusiveSetTitle != null) tvExclusiveSetTitle.setText(tr("سيتات حصرية منوعة 🔥", "Various Exclusive Sets 🔥"));
        
        TextView tvSearchHint = findViewById(R.id.tvSearchHint);
        if (tvSearchHint != null) tvSearchHint.setText(tr("ابحث عن سيتات أو إيقاعات أو أصوات ...", "Search for musical sets..."));

        TextView tvFavoritesText = findViewById(R.id.tvFavoritesText);
        if (tvFavoritesText != null) tvFavoritesText.setText(tr("المفضلة", "Favorites"));
        
        TextView tvHowToDownloadText = findViewById(R.id.tvHowToDownloadText);
        if (tvHowToDownloadText != null) tvHowToDownloadText.setText(tr("طريقة تحميل\nالسيتات 🤔", "How to Download\nSets 🤔"));
        
        TextView tvHowToAddText = findViewById(R.id.tvHowToAddText);
        if (tvHowToAddText != null) tvHowToAddText.setText(tr("طريقة إضافة\nالسيتات 🤔", "How to Add\nSets 🤔"));

        TextView tvSectionSetsTitle = findViewById(R.id.tvSectionSetsTitle);
        if (tvSectionSetsTitle != null) tvSectionSetsTitle.setText(tr("مجموعات السيتات", "Musical Sets"));

        TextView tvSectionPopularTitle = findViewById(R.id.tvSectionPopularTitle);
        if (tvSectionPopularTitle != null) tvSectionPopularTitle.setText(tr("الأكثر شعبية والشروحات", "Popular & Tutorials"));

        TextView tvPopularText = findViewById(R.id.tvPopularText);
        if (tvPopularText != null) tvPopularText.setText(tr("رائج 🔥\nالأكثر إنتشاراً", "Trending 🔥\nMost Popular"));

        TextView tvChatTitle = findViewById(R.id.tvChatTitle);
        if (tvChatTitle != null) tvChatTitle.setText(tr("الدعم", "Support"));
        
        TextView tvNotifTitle = findViewById(R.id.tvNotifTitle);
        if (tvNotifTitle != null) tvNotifTitle.setText(tr("الإشعارات", "Alerts"));
        
        TextView tvShareTitle = findViewById(R.id.tvShareTitle);
        if (tvShareTitle != null) tvShareTitle.setText(tr("مشاركة", "Share"));

        MovingBorderView orgVipMovingBorder = findViewById(R.id.orgVipMovingBorder);
        if (orgVipMovingBorder != null) {
            orgVipMovingBorder.setCornerRadius(18f); 
            orgVipMovingBorder.setGlowColor("#FFD700"); 
        }

        findViewById(R.id.btnLangTopBar).setOnClickListener(v -> DialogHelper.showLanguageDialog(this, currentLang, null));
        
        findViewById(R.id.btnProfileClick).setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                DialogHelper.showLoginDialog(this, currentLang);
            } else {
                ProfileDialogHelper.showProfileDialog(this, currentLang);
            }
        });
        
        View btnOrgVipClick = findViewById(R.id.btnOrgVipClick);
        if (btnOrgVipClick != null) {
            btnOrgVipClick.setOnTouchListener(getTouchListener());
            btnOrgVipClick.setOnClickListener(v -> {
                v.setEnabled(false); 
                pianoLoadingDialog = DialogHelper.showLoadingDialog(this, currentLang, tr("جاري تجهيز الأورج الملكي...", "Preparing Royal ORG..."));
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    navigateWithoutAd(OrgPianoActivity.class);
                    if (pianoLoadingDialog != null) pianoLoadingDialog.dismiss();
                    v.setEnabled(true); 
                }, 50); 
            });
        }

        View btnGlobalSearch = findViewById(R.id.btnGlobalSearch);
        if (btnGlobalSearch != null) btnGlobalSearch.setOnClickListener(v -> navigateWithoutAd(SearchActivity.class));
        
        findViewById(R.id.btnNotificationsClick).setOnClickListener(v -> navigateWithoutAd(AlertActivity.class));
        findViewById(R.id.btnChatClick).setOnClickListener(v -> showLoadingAndNavigate());
        findViewById(R.id.btnShareClick).setOnClickListener(v -> shareApp()); 
        
        View btnFavoritesClick = findViewById(R.id.btnFavoritesClick);
        if (btnFavoritesClick != null) {
            btnFavoritesClick.setOnClickListener(v -> {
                if (mAuth.getCurrentUser() != null) navigateWithoutAd(FavoritesActivity.class);
                else DialogHelper.showLoginDialog(this, currentLang);
            });
        }

        View btnPopularClick = findViewById(R.id.btnPopularClick);
        if (btnPopularClick != null) {
            btnPopularClick.setOnTouchListener(getTouchListener());
            btnPopularClick.setOnClickListener(v -> navigateWithAd(PopularActivity.class));
        }

        View btnHowToDownloadClick = findViewById(R.id.btnHowToDownloadClick);
        if (btnHowToDownloadClick != null) {
            btnHowToDownloadClick.setOnTouchListener(getTouchListener());
            btnHowToDownloadClick.setOnClickListener(v -> openUrlInBrowser("https://youtube.com/shorts/HhbkHfp3Rjw?feature=share"));
        }
        
        View btnHowToAddClick = findViewById(R.id.btnHowToAddClick);
        if (btnHowToAddClick != null) {
            btnHowToAddClick.setOnTouchListener(getTouchListener());
            btnHowToAddClick.setOnClickListener(v -> openUrlInBrowser("https://youtu.be/zzMtxeyvoT0"));
        }

        View btnHero = findViewById(R.id.btnNewSetExclusive);
        if (btnHero != null) {
            btnHero.setOnTouchListener(getTouchListener());
            btnHero.setOnClickListener(v -> navigateWithAd(NewActivity.class));
        }

        int[] bIds = {R.id.btnSetat2, R.id.btnSetat3, R.id.btnSetat5, R.id.btnSetat6, R.id.btnSetat7, R.id.btnSetat8, R.id.btnSetat9, R.id.btnSetat10, R.id.btnSetat11, R.id.btnSetat12};
        int[] tvIds = {R.id.tvSetat2, R.id.tvSetat3, R.id.tvSetat5, R.id.tvSetat6, R.id.tvSetat7, R.id.tvSetat8, R.id.tvSetat9, R.id.tvSetat10, R.id.tvSetat11, R.id.tvSetat12};
        String[] arT = {"سيتات عالمية", "سيتات شعبية", "سيتات دبكات", "سيتات عراقي", "سيتات مصري", "سيتات إحترافية", "سيتات جزائري", "سيتات سوري", "سيتات يمني", "ثيمات الأورج"};
        String[] enT = {"World Sets", "Popular Sets", "Dabkat Sets", "Iraqi Sets", "Egyptian Sets", "Professional Sets", "Algerian Sets", "Syrian Sets", "Yemeni Sets", "Org Themes"};
        int[] targetActs = {2, 3, 5, 6, 7, 8, 9, 10, 11, 12};

        for (int i=0; i<bIds.length; i++) {
            final int targetIdx = targetActs[i];
            View layout = findViewById(bIds[i]);
            TextView tvText = findViewById(tvIds[i]);
            
            if (layout != null && tvText != null) {
                tvText.setText(tr(arT[i], enT[i]));
                layout.setOnTouchListener(getTouchListener());
                layout.setOnClickListener(v -> navigateToActivity(targetIdx));
            }
        }

        applyCardImagesDirection();
    }

    private void applyCardImagesDirection() {
        float dirScale = "en".equals(currentLang) ? -1f : 1f;
        int[] bgImgIds = {
            R.id.imgOrgHeroBg,
            R.id.imgBgExclusive,
            R.id.imgBgSetat2, R.id.imgBgSetat3,
            R.id.imgBgSetat5, R.id.imgBgSetat6,
            R.id.imgBgSetat7, R.id.imgBgSetat8,
            R.id.imgBgSetat9, R.id.imgBgSetat10,
            R.id.imgBgSetat11, R.id.imgBgSetat12
        };
        for (int id : bgImgIds) {
            View img = findViewById(id);
            if (img != null) img.setScaleX(dirScale);
        }
    }
    
    private View.OnTouchListener getTouchListener() {
        return (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start(); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: v.animate().scaleX(1f).scaleY(1f).setDuration(150).start(); break;
            }
            return false;
        };
    }
    
    public void showLoadingAndNavigate() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { 
            DialogHelper.showLoginDialog(this, currentLang); 
            return; 
        }
        final Dialog loadingDialog = DialogHelper.showLoadingDialog(this, currentLang, tr("جاري تهيئة المحادثة...", "Initializing chat..."));
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
            String uid = user.getUid();
            if (DEV_UID.equals(uid)) navigateWithoutAd(AdminChatsListActivity.class);
            else {
                Intent intent = new Intent(this, SupportChatActivity.class);
                intent.putExtra("room_uid", uid); 
                intent.putExtra("is_admin", false);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        }, 800);
    }

    private void setupFirebaseAndNetwork() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel("high_priority_channel", "General Notifications", android.app.NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true); channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500});
            android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true);
        new Handler(Looper.getMainLooper()).postDelayed(() -> registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
                if (activeNetworkInfo == null || !activeNetworkInfo.isConnected()) {
                    startActivity(new Intent(getApplicationContext(), MainactivityActivity.class)); finish();
                }
            }
        }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)), 1000);
    }

    public void openUrlInBrowser(String url) {
        try { new CustomTabsIntent.Builder().setToolbarColor(Color.parseColor("#0A0F14")).build().launchUrl(this, Uri.parse(url)); }
        catch (Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
    }

    private void shareApp() {
        Intent si = new Intent(Intent.ACTION_SEND); si.setType("text/plain");
        si.putExtra(Intent.EXTRA_TEXT, tr("شاركني العزف في أقوى أورج! رابط التطبيق:\nhttps://play.google.com/store/apps/details?id=com.zeekoltd.setat.pro", "Join me in the best ORG! App link:\nhttps://play.google.com/store/apps/details?id=com.zeekoltd.setat.pro"));
        startActivity(Intent.createChooser(si, "Share via"));
    }

    private void applyLanguage() {
        String savedLang = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("app_language", "ar");
        if (!savedLang.equals(currentLang)) {
            currentLang = savedLang;
        }
        Locale locale = new Locale(currentLang);
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        if ("en".equals(currentLang)) config.setLayoutDirection(Locale.ENGLISH);
        else config.setLayoutDirection(Locale.forLanguageTag("ar"));
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private void navigateWithAd(Class<?> targetActivity) {
        Intent intent = new Intent(this, targetActivity);
        if (adSkipCounter == 0 && adMobAdsHelper != null && adMobAdsHelper.isInterstitialReady()) {
            adMobAdsHelper.showInterstitialAd(() -> {
                adSkipCounter = 1; 
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                adMobAdsHelper.loadInterstitialAdSilent();
            });
        } else {
            adSkipCounter++;
            if (adSkipCounter > 2) adSkipCounter = 0; 
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            if (adMobAdsHelper != null && !adMobAdsHelper.isInterstitialReady()) {
                adMobAdsHelper.loadInterstitialAdSilent();
            }
        }
    }

    private void navigateWithoutAd(Class<?> targetActivity) {
        Intent intent = new Intent(this, targetActivity);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void navigateToActivity(int i) {
        Class<?> t = null;
        boolean requiresAd = true;
        switch(i) {
            case 1: t = OrgPianoActivity.class; requiresAd = false; break;
            case 2: t = NewsetatActivity.class; break; 
            case 3: t = SetatshabiActivity.class; break; 
            case 4: t = NewActivity.class; break; 
            case 5: t = SetatdabakatActivity.class; break; 
            case 6: t = SetatiraqActivity.class; break; 
            case 7: t = SetategeptActivity.class; break; 
            case 8: t = SetatehtrafeActivity.class; break; 
            case 9: t = DzirActivity.class; break; 
            case 10: t = SoriActivity.class; break; 
            case 11: t = YemeActivity.class; break; 
            case 12: t = Them2026Activity.class; break; 
        }
        if(t != null) {
            if (requiresAd) navigateWithAd(t);
            else navigateWithoutAd(t);
        }
    }

    @Override public void onBackPressed() { DialogHelper.showExitDialog(this, currentLang); }
    
    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth != null && authStateListener != null) {
            mAuth.addAuthStateListener(authStateListener);
        }
    }

    @Override 
    protected void onResume() { 
        super.onResume(); 
        String savedLang = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("app_language", "ar");
        if (!savedLang.equals(currentLang)) {
            refreshLanguageSmoothly();
        }
        if(adMobAdsHelper != null) adMobAdsHelper.onResume();
        // Initial account UI is loaded in initializeLogic(); auth changes are handled
        // by authStateListener, avoiding duplicate image/database work on every resume.
    }
    
    @Override 
    protected void onStop() {
        super.onStop();
        if (mAuth != null && authStateListener != null) {
            mAuth.removeAuthStateListener(authStateListener);
        }
        if (pianoLoadingDialog != null && pianoLoadingDialog.isShowing()) {
            pianoLoadingDialog.dismiss();
            pianoLoadingDialog = null;
        }
    }
    
    @Override 
    protected void onDestroy() { 
        super.onDestroy();
        hideGoogleAuthBlocker();
        dismissGoogleLoadingDialog();
        if (mAuth != null && authStateListener != null) {
            mAuth.removeAuthStateListener(authStateListener);
        }
        if(adMobAdsHelper != null) adMobAdsHelper.onDestroy(); 
        if(executorService != null) executorService.shutdown();
    }
    
    public String tr(String ar, String en) { return "en".equals(currentLang) ? en : ar; }
}
