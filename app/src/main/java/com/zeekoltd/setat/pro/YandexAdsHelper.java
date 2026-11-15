package com.zeekoltd.setat.pro;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import com.yandex.mobile.ads.banner.BannerAdEventListener;
import com.yandex.mobile.ads.banner.BannerAdSize;
import com.yandex.mobile.ads.banner.BannerAdView;
import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.common.YandexAds;
import com.yandex.mobile.ads.interstitial.InterstitialAd;
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener;
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader;
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener;
import com.yandex.mobile.ads.rewarded.Reward;
import com.yandex.mobile.ads.rewarded.RewardedAd;
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener;
import com.yandex.mobile.ads.rewarded.RewardedAdLoader;
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class YandexAdsHelper {
    private static YandexAdsHelper instance;
    private static volatile boolean sdkInitialized;

    public static YandexAdsHelper getInstance(Activity activity) {
        if (instance == null) instance = new YandexAdsHelper(activity);
        else {
            instance.activityRef = new WeakReference<>(activity);
            instance.density = activity.getResources().getDisplayMetrics().density;
        }
        return instance;
    }

    public static boolean isInitialized() { return instance != null; }

    public static void destroyInstance() {
        if (instance != null) {
            instance.destroyAll();
            instance = null;
        }
    }

    // Official Yandex demo placements for testing the Yandex SDK integration.
    // Replace these with the production R-M-... placements supplied by Yandex Easy Monetization.
    public static final String INTERSTITIAL_AD_UNIT_ID = "demo-interstitial-yandex";
    public static final String REWARDED_AD_UNIT_ID = "demo-rewarded-yandex";
    public static final String BANNER_AD_UNIT_ID = "demo-banner-yandex";

    private WeakReference<Activity> activityRef;
    private float density;
    private final Handler handler;
    private volatile boolean isSdkInitialized = sdkInitialized;
    private volatile boolean isInitializing;
    private final ArrayList<Runnable> pendingInitTasks = new ArrayList<>();
    private String currentLang = "ar";

    private InterstitialAdLoader interstitialAdLoader;
    private InterstitialAd mInterstitialAd;
    private boolean isInterstitialLoaded;
    private boolean isInterstitialLoading;
    private RewardedAdLoader rewardedAdLoader;
    private RewardedAd mRewardedAd;
    private boolean isRewardLoaded;
    private boolean isRewardedLoading;
    private boolean userEarnedReward;
    private String pendingUrlAfterAd = "";
    private boolean isShowingAdDialog;
    private Dialog currentAdDialog;
    private OnRewardEarnedListener pendingRewardListener;
    private BannerAdView mBannerAdView;
    private FrameLayout bannerContainer;
    private Runnable bannerRetryRunnable;
    private int bannerRetryCount;
    private boolean isDialogLoading;
    private boolean isDialogTimedOut;
    private Dialog blockingOverlay;

    public interface OnAdsInitializedListener { void onInitialized(); }
    public interface OnRewardEarnedListener { void onRewardEarned(String pendingUrl); }

    private YandexAdsHelper(Activity activity) {
        activityRef = new WeakReference<>(activity);
        density = activity.getResources().getDisplayMetrics().density;
        handler = new Handler(Looper.getMainLooper());
    }

    private String tr(String arabic, String english) { return "en".equals(currentLang) ? english : arabic; }
    public void setCurrentLanguage(String lang) { if (lang != null) currentLang = lang; }
    public String getCurrentLanguage() { return currentLang; }
    public Activity getActivity() { return activityRef != null ? activityRef.get() : null; }

    public void initialize(OnAdsInitializedListener listener) {
        if (listener != null) pendingInitTasks.add(listener::onInitialized);
        if (isSdkInitialized || sdkInitialized) {
            isSdkInitialized = true;
            executePendingTasks();
            return;
        }
        if (isInitializing) return;
        isInitializing = true;
        Activity activity = getActivity();
        if (activity == null) { isInitializing = false; pendingInitTasks.clear(); return; }
        boolean userConsent = activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("yandex_ads_consent", false);
        YandexAds.setUserConsent(userConsent);
        YandexAds.initialize(activity.getApplicationContext(), () -> {
            sdkInitialized = true;
            isSdkInitialized = true;
            isInitializing = false;
            handler.post(this::executePendingTasks);
        });
    }

    private void executePendingTasks() {
        ArrayList<Runnable> tasks = new ArrayList<>(pendingInitTasks);
        pendingInitTasks.clear();
        for (Runnable task : tasks) task.run();
    }

    public boolean isInterstitialLoading() { return isInterstitialLoading; }

    public void loadInterstitialAdSilent() {
        if (!isSdkInitialized) { initialize(this::loadInterstitialAdSilent); return; }
        if (isInterstitialLoaded || isInterstitialLoading) return;
        Activity activity = getActivity();
        if (activity == null) return;
        isInterstitialLoading = true;
        if (interstitialAdLoader == null) interstitialAdLoader = new InterstitialAdLoader(activity.getApplicationContext());
        interstitialAdLoader.loadAd(new AdRequest.Builder(INTERSTITIAL_AD_UNIT_ID).build(), new InterstitialAdLoadListener() {
            @Override public void onAdLoaded(@NonNull InterstitialAd ad) {
                mInterstitialAd = ad; isInterstitialLoaded = true; isInterstitialLoading = false;
                android.util.Log.d("YandexAds", "Interstitial loaded successfully");
            }
            @Override public void onAdFailedToLoad(@NonNull AdRequestError error) {
                mInterstitialAd = null; isInterstitialLoaded = false; isInterstitialLoading = false;
                android.util.Log.e("YandexAds", "Interstitial failed to load: " + error.getDescription());
            }
        });
    }

    public void showInterstitialAd(final Runnable onAdClosed) {
        Activity activity = getActivity();
        if (mInterstitialAd == null || !isInterstitialLoaded || activity == null || !isActivityValid()) {
            if (onAdClosed != null) onAdClosed.run();
            return;
        }
        InterstitialAd ad = mInterstitialAd;
        ad.setAdEventListener(new InterstitialAdEventListener() {
            private void finish() {
                ad.setAdEventListener(null);
                if (mInterstitialAd == ad) mInterstitialAd = null;
                isInterstitialLoaded = false;
                if (onAdClosed != null) onAdClosed.run();
            }
            @Override public void onAdShown() { mInterstitialAd = null; isInterstitialLoaded = false; }
            @Override public void onAdFailedToShow(@NonNull AdError error) {
                android.util.Log.e("YandexAds", "Interstitial failed to show: " + error.getDescription());
                finish();
            }
            @Override public void onAdDismissed() { finish(); }
            @Override public void onAdClicked() { }
            @Override public void onAdImpression(@Nullable ImpressionData data) { }
        });
        ad.show(activity);
    }

    public boolean isInterstitialReady() { return isInterstitialLoaded && mInterstitialAd != null; }

    private void loadRewardedAd() {
        if (!isSdkInitialized) { initialize(this::loadRewardedAd); return; }
        if (isRewardLoaded || isRewardedLoading) return;
        Activity activity = getActivity();
        if (activity == null) return;
        if (rewardedAdLoader == null) rewardedAdLoader = new RewardedAdLoader(activity.getApplicationContext());
        isRewardedLoading = true;
        rewardedAdLoader.loadAd(new AdRequest.Builder(REWARDED_AD_UNIT_ID).build(), new RewardedAdLoadListener() {
            @Override public void onAdLoaded(@NonNull RewardedAd ad) {
                if (isDialogTimedOut) { isRewardLoaded = false; isRewardedLoading = false; return; }
                mRewardedAd = ad; isRewardLoaded = true; isRewardedLoading = false;
            }
            @Override public void onAdFailedToLoad(@NonNull AdRequestError error) {
                mRewardedAd = null; isRewardLoaded = false; isRewardedLoading = false;
            }
        });
    }

    public void showRewardedAd(OnRewardEarnedListener rewardListener) {
        pendingRewardListener = rewardListener;
        Activity activity = getActivity();
        if (mRewardedAd == null || !isRewardLoaded) {
            if (activity != null) activity.runOnUiThread(this::showAdLoadingDialog);
            return;
        }
        if (activity == null || !isActivityValid()) return;
        RewardedAd ad = mRewardedAd;
        ad.setAdEventListener(new RewardedAdEventListener() {
            @Override public void onAdShown() { mRewardedAd = null; isRewardLoaded = false; }
            @Override public void onAdFailedToShow(@NonNull AdError error) {
                clearRewarded(ad);
                Activity current = getActivity();
                if (current != null) current.runOnUiThread(YandexAdsHelper.this::showAdLoadErrorDialog);
            }
            @Override public void onAdDismissed() {
                Activity current = getActivity();
                if (current != null) current.runOnUiThread(YandexAdsHelper.this::showBlockingOverlay);
                if (userEarnedReward) {
                    handler.postDelayed(() -> {
                        Activity a = getActivity();
                        if (a != null && pendingRewardListener != null) {
                            pendingRewardListener.onRewardEarned(pendingUrlAfterAd);
                            handler.postDelayed(YandexAdsHelper.this::hideBlockingOverlay, 300);
                        }
                    }, 250);
                    userEarnedReward = false;
                } else hideBlockingOverlay();
                clearRewarded(ad);
                Activity a = getActivity();
                if (a != null) a.runOnUiThread(() -> {
                    safeDismissDialog(currentAdDialog); currentAdDialog = null; isShowingAdDialog = false;
                });
            }
            @Override public void onAdClicked() { }
            @Override public void onAdImpression(@Nullable ImpressionData data) { }
            @Override public void onRewarded(@NonNull Reward reward) { userEarnedReward = true; }
        });
        ad.show(activity);
    }

    private void clearRewarded(RewardedAd ad) {
        ad.setAdEventListener(null);
        if (mRewardedAd == ad) mRewardedAd = null;
        isRewardLoaded = false;
        isRewardedLoading = false;
    }

    public boolean isRewardedReady() { return isRewardLoaded && mRewardedAd != null; }

    public void destroyInterstitial() {
        if (mInterstitialAd != null) mInterstitialAd.setAdEventListener(null);
        mInterstitialAd = null;
        isInterstitialLoaded = false;
        isInterstitialLoading = false;
        interstitialAdLoader = null;
    }

    public void destroyRewarded() {
        if (mRewardedAd != null) mRewardedAd.setAdEventListener(null);
        mRewardedAd = null; isRewardLoaded = false; isRewardedLoading = false;
        rewardedAdLoader = null; pendingRewardListener = null; isDialogLoading = false; isDialogTimedOut = false;
    }

    public void loadBannerAd(FrameLayout container, ImageView placeholder) {
        Activity activity = getActivity();
        if (activity == null || !isSdkInitialized || container == null) return;
        destroyBanner();
        bannerRetryCount = 0;
        mBannerAdView = new BannerAdView(activity);
        bannerContainer = container;
        // Match the reference app's adaptive inline banner size and the existing 350x50dp slot.
        mBannerAdView.setAdSize(BannerAdSize.inline(activity, 350, 50));
        mBannerAdView.setBannerAdEventListener(new BannerAdEventListener() {
            @Override public void onAdLoaded() {
                if (placeholder != null) placeholder.setVisibility(View.GONE);
                bannerRetryCount = 0;
                if (bannerRetryRunnable != null) handler.removeCallbacks(bannerRetryRunnable);
                android.util.Log.d("YandexAds", "Banner loaded successfully");
            }
            @Override public void onAdFailedToLoad(@NonNull AdRequestError error) {
                if (placeholder != null) placeholder.setVisibility(View.VISIBLE);
                android.util.Log.e("YandexAds", "Banner failed to load: " + error.getDescription());
                if (bannerRetryRunnable != null) handler.removeCallbacks(bannerRetryRunnable);
                if (bannerRetryCount < 2) {
                    long delay = bannerRetryCount == 0 ? 60_000L : 120_000L;
                    bannerRetryCount++;
                    bannerRetryRunnable = () -> {
                        if (mBannerAdView != null && isActivityValid()) {
                            mBannerAdView.loadAd(new AdRequest.Builder(BANNER_AD_UNIT_ID).build());
                        }
                    };
                    handler.postDelayed(bannerRetryRunnable, delay);
                } else {
                    bannerRetryCount = 0;
                }
            }
            @Override public void onAdClicked() { }
            @Override public void onImpression(@Nullable ImpressionData data) { }
        });
        container.addView(mBannerAdView);
        mBannerAdView.loadAd(new AdRequest.Builder(BANNER_AD_UNIT_ID).build());
    }

    public void destroyBanner() {
        if (bannerRetryRunnable != null) {
            handler.removeCallbacks(bannerRetryRunnable);
            bannerRetryRunnable = null;
        }
        if (mBannerAdView != null) {
            if (bannerContainer != null) bannerContainer.removeView(mBannerAdView);
            mBannerAdView.setBannerAdEventListener(null);
            mBannerAdView.destroy();
            mBannerAdView = null;
            bannerContainer = null;
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

        FrameLayout blurCenterWindow = baseView.findViewById(R.id.blurCenterWindow);

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
        View cancelBtnView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, dynamicContainer, false);
        TextView tvCancel = cancelBtnView.findViewById(R.id.tvButtonText);
        tvCancel.setText(tr("إلـغـاء", "Cancel"));
        dynamicContainer.addView(cancelBtnView);

        View.OnTouchListener buttonTouchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                        break;
                }
                return false;
            }
        };

        watchBtnView.setOnTouchListener(buttonTouchListener);
        cancelBtnView.setOnTouchListener(buttonTouchListener);

        cancelBtnView.setOnClickListener(v -> {
            isShowingAdDialog = false;
            pendingUrlAfterAd = "";
            pendingRewardListener = null;
            currentAdDialog = null;
            safeDismissDialog(adDialog);
        });

        watchBtnView.setOnClickListener(v -> showRewardedAd(rewardListener));

        safeShowDialog(adDialog);
    }

    // ============================================================
    // 5. نافذة جاري تحميل الإعلان (مؤقت 10 ثواني كحد أقصى)
    // ============================================================
    private void showAdLoadingDialog() {
        Activity activity = getActivity();
        if (activity == null || !isActivityValid()) return;

        final Dialog loadingDialog = createBaseGlassDialog(activity, null, tr("جاري تـحـميل الإعـــلان...", "Loading ad..."));
        loadingDialog.setCancelable(false);

        LinearLayout dynamicContainer = loadingDialog.findViewById(R.id.dialogDynamicContainer);

        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleLarge);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams((int)(50 * density), (int)(50 * density));
        pbParams.topMargin = (int)(10 * density);
        progressBar.setLayoutParams(pbParams);
        progressBar.setIndeterminate(true);
        progressBar.getIndeterminateDrawable().setColorFilter(Color.parseColor("#00FFFF"), android.graphics.PorterDuff.Mode.SRC_IN);
        
        dynamicContainer.addView(progressBar);

        safeShowDialog(loadingDialog);
        
        isDialogTimedOut = false;
        isDialogLoading = true;
        
        // 🚀 بدء تحميل الإعلان الآن
        if (!isRewardLoaded && !isRewardedLoading) {
            loadRewardedAd();
        }

        // 🚀 مؤقت الإلغاء النهائي (Timeout) بعد 10 ثواني بالضبط
        final Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isActivityValid()) return;
                
                if (loadingDialog.isShowing() && isRewardedLoading && !isRewardLoaded) {
                    isDialogTimedOut = true;
                    isRewardedLoading = false;
                    isDialogLoading = false;
                    
                    safeDismissDialog(loadingDialog);
                    showAdLoadErrorDialog();
                }
            }
        };
        handler.postDelayed(timeoutRunnable, 15000); 

        // الفاحص المستمر لمعرفة ما إذا كان الإعلان قد أصبح جاهزاً
        final Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isActivityValid() || isDialogTimedOut) return;

                if (loadingDialog.isShowing()) {
                    if (isRewardLoaded) {
                        handler.removeCallbacks(timeoutRunnable);
                        safeDismissDialog(loadingDialog);
                        isDialogLoading = false;
                        showRewardedAd(pendingRewardListener);
                    } else if (!isRewardedLoading) { 
                        handler.removeCallbacks(timeoutRunnable);
                        safeDismissDialog(loadingDialog);
                        isDialogLoading = false;
                        showAdLoadErrorDialog();
                    } else {
                        handler.postDelayed(this, 1000); 
                    }
                } else {
                    handler.removeCallbacks(timeoutRunnable);
                    isDialogLoading = false;
                }
            }
        };
        handler.postDelayed(checkRunnable, 1000);
    }

    // ============================================================
    // 6. نافذة خطأ تحميل الإعلان
    // ============================================================
    private void showAdLoadErrorDialog() {
        Activity activity = getActivity();
        if (activity == null || !isActivityValid()) return;
        
        isDialogLoading = false;
        isDialogTimedOut = false;

        final Dialog errorDialog = createBaseGlassDialog(activity, null, 
            tr("فـشــل تـحـمـيـل الإعــلان 😥\nحاول مرة أخرى لاحقاً", "Failed to load ad 😥\nPlease try again later"));
        errorDialog.setCancelable(true);

        LinearLayout dynamicContainer = errorDialog.findViewById(R.id.dialogDynamicContainer);

        View okBtnView = LayoutInflater.from(activity).inflate(R.layout.item_dialog_button, dynamicContainer, false);
        TextView tvOk = okBtnView.findViewById(R.id.tvButtonText);
        tvOk.setText(tr("موافق", "OK"));
        dynamicContainer.addView(okBtnView);

        okBtnView.setOnClickListener(v -> {
            safeDismissDialog(errorDialog);
            
            if (currentAdDialog != null && currentAdDialog.isShowing()) {
                safeDismissDialog(currentAdDialog);
            }
            
            isShowingAdDialog = false;
            currentAdDialog = null;
            pendingUrlAfterAd = "";
            pendingRewardListener = null;
        });

        safeShowDialog(errorDialog);
    }

    // ============================================================
    // 7. دوال مساعدة للحاجب والتصفح
    // ============================================================
    private void showBlockingOverlay() {
        Activity activity = getActivity();
        if (activity == null || !isActivityValid()) return;
        
        blockingOverlay = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        blockingOverlay.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        );
        blockingOverlay.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        blockingOverlay.setCancelable(false);
        
        FrameLayout overlayLayout = new FrameLayout(activity);
        overlayLayout.setBackgroundColor(Color.parseColor("#E6000000"));
        overlayLayout.setClickable(true);
        overlayLayout.setOnTouchListener((v, event) -> true);
        
        ProgressBar spinningCircle = new ProgressBar(activity, null, android.R.attr.progressBarStyleLarge);
        int circleSize = (int)(55 * density);
        FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(circleSize, circleSize);
        circleParams.gravity = Gravity.CENTER;
        spinningCircle.setLayoutParams(circleParams);
        spinningCircle.setIndeterminate(true);
        spinningCircle.getIndeterminateDrawable().setColorFilter(
            Color.parseColor("#00FFFF"), 
            android.graphics.PorterDuff.Mode.SRC_IN
        );
        
        overlayLayout.addView(spinningCircle);
        blockingOverlay.setContentView(overlayLayout);
        
        safeShowDialog(blockingOverlay);
    }

    private void hideBlockingOverlay() {
        if (blockingOverlay != null) {
            safeDismissDialog(blockingOverlay);
            blockingOverlay = null;
        }
    }

    public void openDownloadUrl(String url) {
        Activity activity = getActivity();
        if (activity == null || !isActivityValid()) return;
        try {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setToolbarColor(Color.parseColor("#012638"));
            builder.setSecondaryToolbarColor(Color.parseColor("#03330C"));
            builder.setStartAnimations(activity, android.R.anim.fade_in, android.R.anim.fade_out);
            builder.setExitAnimations(activity, android.R.anim.fade_in, android.R.anim.fade_out);
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(activity, Uri.parse(url));
        } catch (Exception e) {
            try {
                Intent downloadIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                activity.startActivity(downloadIntent);
            } catch (Exception ex) {
                Toast.makeText(activity, tr("خطأ في فتح رابط التحميل", "Error opening download link"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public boolean isShowingAdDialog() {
        return isShowingAdDialog;
    }

    public void dismissAdDialog() {
        if (currentAdDialog != null) {
            safeDismissDialog(currentAdDialog);
            currentAdDialog = null;
        }
        isShowingAdDialog = false;
        pendingUrlAfterAd = "";
        pendingRewardListener = null;
    }

    // ============================================================
    // 8. التنظيف والموت
    // ============================================================
    public void onResume() {}
    public void onDestroy() { dismissAdDialog(); }

    private void destroyAll() {
        destroyBanner();
        destroyInterstitial();
        destroyRewarded();
        isDialogLoading = false;
        isDialogTimedOut = false;
        handler.removeCallbacksAndMessages(null);
        hideBlockingOverlay();
        dismissAdDialog();
        isSdkInitialized = false;
        isInitializing = false;
        pendingInitTasks.clear();
    }

    // ============================================================
    // 9. حماية الانهيارات
    // ============================================================
    private boolean isActivityValid() {
        Activity activity = getActivity();
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void safeShowDialog(Dialog dialog) {
        try {
            if (isActivityValid() && dialog != null && !dialog.isShowing()) {
                dialog.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void safeDismissDialog(Dialog dialog) {
        try {
            if (isActivityValid() && dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
