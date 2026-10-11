package com.zeeko.setat.org2026;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;

// 🚀 استدعاءات مكتبة Google AdMob
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.lang.ref.WeakReference;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class AdMobAdsHelper {

    // ============================================================
    // Singleton Instance
    // ============================================================
    private static AdMobAdsHelper instance;

    public static AdMobAdsHelper getInstance(Activity activity) {
        if (instance == null) {
            instance = new AdMobAdsHelper(activity);
        } else {
            instance.activityRef = new WeakReference<>(activity);
            instance.density = activity.getResources().getDisplayMetrics().density;
        }
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public static void destroyInstance() {
        if (instance != null) {
            instance.destroyAll();
            instance = null;
        }
    }

    // ============================================================
    // 🚀 ثوابت معرفات الوحدات الإعلانية التجريبية من جوجل (Test IDs)
    // لا يتم جلبها من فايربيس نهائياً حسب طلبك
    // ============================================================
    public static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712";
    public static final String REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";
    public static final String BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"; 

    // ============================================================
    // المتغيرات العامة
    // ============================================================
    private WeakReference<Activity> activityRef;
    private float density;
    private final Handler handler;
    
    private boolean isSdkInitialized = false;
    private boolean isInitializing = false;
    private final java.util.ArrayList<Runnable> pendingInitTasks = new java.util.ArrayList<>();
    
    private String currentLang = "ar";

    // متغيرات الإعلان البيني
    private InterstitialAd mInterstitialAd;
    private boolean isInterstitialLoaded = false;
    private boolean isInterstitialLoading = false;

    // متغيرات إعلان المكافأة
    private RewardedAd mRewardedAd;
    private boolean isRewardLoaded = false;
    private boolean isRewardedLoading = false; 
    private boolean userEarnedReward = false;
    private String pendingUrlAfterAd = "";
    private boolean isShowingAdDialog = false;
    private Dialog currentAdDialog = null;
    private OnRewardEarnedListener pendingRewardListener;

    // 🚀 متغير البانر
    private AdView mBannerAdView;

    // متغيرات التحكم الزمني
    private boolean isDialogLoading = false;
    private boolean isDialogTimedOut = false;

    // متغير الحاجب (Blocking Overlay)
    private Dialog blockingOverlay;

    // ============================================================
    // واجهات المستمعين (Callbacks)
    // ============================================================
    public interface OnAdsInitializedListener {
        void onInitialized();
    }

    public interface OnRewardEarnedListener {
        void onRewardEarned(String pendingUrl);
    }

    // ============================================================
    // البناء (Constructor)
    // ============================================================
    private AdMobAdsHelper(Activity activity) {
        this.activityRef = new WeakReference<>(activity);
        this.density = activity.getResources().getDisplayMetrics().density;
        this.handler = new Handler(Looper.getMainLooper());
    }

    private String tr(String arabicText, String englishText) {
        return "en".equals(currentLang) ? englishText : arabicText;
    }

    public void setCurrentLanguage(String lang) {
        this.currentLang = lang;
    }

    public String getCurrentLanguage() {
        return currentLang;
    }

    public Activity getActivity() {
        return activityRef != null ? activityRef.get() : null;
    }

    // ============================================================
    // 1. التهيئة الأولية لـ AdMob
    // ============================================================
    public void initialize(OnAdsInitializedListener listener) {
        if (listener != null) {
            pendingInitTasks.add(() -> listener.onInitialized());
        }

        if (isSdkInitialized) {
            executePendingTasks();
            return;
        }

        if (isInitializing) return;

        isInitializing = true;
        Activity activity = getActivity();
        if (activity == null) return;
        
        // 🚀 تهيئة Google Mobile Ads
        MobileAds.initialize(activity, initializationStatus -> {
            isSdkInitialized = true;
            isInitializing = false;
            
            Activity act = getActivity();
            if (act != null) {
                act.runOnUiThread(this::executePendingTasks);
            }
        });
    }

    private void executePendingTasks() {
        for (Runnable task : pendingInitTasks) {
            task.run();
        }
        pendingInitTasks.clear();
    }

    // ============================================================
    // 2. الإعلانات البينية 
    // تم حذف التبريد (Cooldown) كما طلبت
    // ============================================================
    public boolean isInterstitialLoading() {
        return isInterstitialLoading;
    }

    public void loadInterstitialAdSilent() {
        if (!isSdkInitialized) {
            initialize(this::loadInterstitialAdSilent);
            return;
        }

        if (isInterstitialLoaded || isInterstitialLoading) return;

        isInterstitialLoading = true;
        Activity activity = getActivity();
        if (activity == null) {
            isInterstitialLoading = false;
            return;
        }

        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(activity, INTERSTITIAL_AD_UNIT_ID, adRequest, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                mInterstitialAd = interstitialAd;
                isInterstitialLoaded = true;
                isInterstitialLoading = false;
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                mInterstitialAd = null;
                isInterstitialLoaded = false;
                isInterstitialLoading = false;
            }
        });
    }

    public void showInterstitialAd(final Runnable onAdClosed) {
        Activity activity = getActivity();
        if (mInterstitialAd != null && isInterstitialLoaded && activity != null && isActivityValid()) {
            
            mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    mInterstitialAd = null;
                    isInterstitialLoaded = false;
                    if (onAdClosed != null) onAdClosed.run();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    mInterstitialAd = null;
                    isInterstitialLoaded = false;
                    if (onAdClosed != null) onAdClosed.run();
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    mInterstitialAd = null; 
                }
            });
            
            mInterstitialAd.show(activity);
            
        } else {
            if (onAdClosed != null) onAdClosed.run();
        }
    }

    public boolean isInterstitialReady() {
        return isInterstitialLoaded && mInterstitialAd != null;
    }

    // ============================================================
    // 3. إعلانات المكافأة (تحميل فقط عند الطلب 100%)
    // ============================================================
    private void loadRewardedAd() {
        if (!isSdkInitialized) {
            initialize(this::loadRewardedAd);
            return;
        }

        if (isRewardLoaded || isRewardedLoading) return;

        Activity activity = getActivity();
        if (activity == null) return;

        isRewardedLoading = true;

        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(activity, REWARDED_AD_UNIT_ID, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                if (isDialogTimedOut) {
                    isRewardLoaded = false;
                    isRewardedLoading = false;
                    return;
                }
                mRewardedAd = rewardedAd;
                isRewardLoaded = true;
                isRewardedLoading = false;
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                mRewardedAd = null;
                isRewardLoaded = false;
                isRewardedLoading = false;
            }
        });
    }

    public void showRewardedAd(OnRewardEarnedListener rewardListener) {
        this.pendingRewardListener = rewardListener;

        Activity activity = getActivity();
        
        // 🚀 تحميل الإعلان الآن وفقط الآن (عندما يطلب المستخدم المشاهدة)
        if (mRewardedAd == null || !isRewardLoaded) {
            if (activity != null) {
                activity.runOnUiThread(this::showAdLoadingDialog);
            }
            return;
        }

        if (activity != null && isActivityValid()) {
            mRewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    mRewardedAd = null;
                    isRewardLoaded = false;
                    Activity act = getActivity();
                    if (act != null) {
                        act.runOnUiThread(() -> showAdLoadErrorDialog());
                    }
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    mRewardedAd = null;
                    isRewardLoaded = false;
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    Activity act = getActivity();
                    if (act != null) {
                        act.runOnUiThread(() -> showBlockingOverlay());
                    }
                    
                    if (userEarnedReward) {
                        act = getActivity();
                        if (act != null) {
                            handler.postDelayed(() -> {
                                Activity a = getActivity();
                                if (a != null && pendingRewardListener != null) {
                                    pendingRewardListener.onRewardEarned(pendingUrlAfterAd);
                                    handler.postDelayed(() -> hideBlockingOverlay(), 300);
                                }
                            }, 250);
                        }
                        userEarnedReward = false;
                    } else {
                        hideBlockingOverlay();
                    }
                    
                    mRewardedAd = null;
                    isRewardLoaded = false;
                    
                    act = getActivity();
                    if (act != null) {
                        act.runOnUiThread(() -> {
                            safeDismissDialog(currentAdDialog);
                            currentAdDialog = null;
                            isShowingAdDialog = false;
                        });
                    }
                }
            });

            mRewardedAd.show(activity, new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                    Activity act = getActivity();
                    if (act != null) {
                        act.runOnUiThread(() -> { 
                            userEarnedReward = true; 
                        });
                    }
                }
            });
        }
    }

    public boolean isRewardedReady() {
        return isRewardLoaded && mRewardedAd != null;
    }

    public void destroyRewarded() {
        mRewardedAd = null;
        isRewardLoaded = false;
        isRewardedLoading = false;
        pendingRewardListener = null;
        isDialogLoading = false;
        isDialogTimedOut = false;
    }

    // ============================================================
    // 3.5 البانر (AdMob)
    // ============================================================
    public void loadBannerAd(FrameLayout container, ImageView placeholder, boolean isBannerEnabled) {
        Activity activity = getActivity();
        if (activity == null || !isSdkInitialized) return;

        if (!isBannerEnabled) {
            destroyBanner();
            if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
            return;
        }

        if (mBannerAdView != null) {
            container.removeView(mBannerAdView);
            mBannerAdView.destroy();
            mBannerAdView = null;
        }

        mBannerAdView = new AdView(activity);
        mBannerAdView.setAdSize(AdSize.BANNER);
        mBannerAdView.setAdUnitId(BANNER_AD_UNIT_ID);

        mBannerAdView.setAdListener(new com.google.android.gms.ads.AdListener() {
            @Override
            public void onAdLoaded() {
                if (placeholder != null) placeholder.setVisibility(View.GONE);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
            }
        });

        container.addView(mBannerAdView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mBannerAdView.loadAd(adRequest);
    }

    public void destroyBanner() {
        if (mBannerAdView != null) {
            mBannerAdView.destroy();
            mBannerAdView = null;
        }
    }

    // ============================================================
    // دوال بناء النوافذ الزجاجية الديناميكية (بدون تعديل التصميم)
    // ============================================================
    
    private Dialog createBaseGlassDialog(Activity activity, String title, String description) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.75f);
        }

        View baseView = LayoutInflater.from(activity).inflate(R.layout.dialog_base_glass, null);
        dialog.setContentView(baseView);

        BlurView blurCenterWindow = baseView.findViewById(R.id.blurCenterWindow);
        View decorView = activity.getWindow().getDecorView();
        ViewGroup rootView = (ViewGroup) decorView.findViewById(android.R.id.content);
        
        if (blurCenterWindow != null && rootView != null) {
            blurCenterWindow.setupWith(rootView, new RenderScriptBlur(activity))
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

        if (title != null && !title.isEmpty()) {
            if (tvTitle != null) {
                tvTitle.setVisibility(View.VISIBLE);
                tvTitle.setText(title);
            }
        } else {
            if (tvTitle != null) tvTitle.setVisibility(View.GONE);
        }

        if (description != null && !description.isEmpty()) {
            if (tvDesc != null) {
                tvDesc.setVisibility(View.VISIBLE);
                tvDesc.setText(description);
            }
        } else {
            if (tvDesc != null) tvDesc.setVisibility(View.GONE);
        }

        return dialog;
    }

    // ============================================================
    // 4. نافذة طلب الإعلان (تصميم زجاجي بدون انتظار/كوول داون)
    // ============================================================

    public void showAdDialog(final String url, final OnRewardEarnedListener rewardListener) {
        Activity activity = getActivity();
        if (activity == null || !isActivityValid()) return;

        isShowingAdDialog = true;
        pendingUrlAfterAd = url;
        this.pendingRewardListener = rewardListener;
        
        final Dialog adDialog = createBaseGlassDialog(activity, null, 
            tr("للتحميل يجب عليك مشاهدة إعلان قصير.هل تريد الاستمرار والتحميل؟",
               "To download, you must watch a short ad.Do you want to continue and download?"));

        currentAdDialog = adDialog;
        adDialog.setCancelable(false);

        LinearLayout dynamicContainer = adDialog.findViewById(R.id.dialogDynamicContainer);

        // زر المشاهدة (فعال فوراً)
        View watchBtnView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, dynamicContainer, false);
        final TextView tvWatch = watchBtnView.findViewById(R.id.tvButtonText);
        tvWatch.setText(tr("مـشـاهـدة", "Watch Ad"));
        dynamicContainer.addView(watchBtnView);

        // زر الإلغاء
        View cancelBtnView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_but
