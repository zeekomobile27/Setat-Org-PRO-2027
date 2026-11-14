package com.zeekoltd.setat.pro;

import android.animation.AnimatorSet;
import android.widget.FrameLayout;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Them2026Activity extends AppCompatActivity {

    private static final String PREFS_NAME = "ad_frequency_prefs";
    private static final String CACHE_PREFS = "app_cache";
    private static final String KEY_SETS_JSON = "them2026_themes_list_v2"; // مفتاح كاش مستقل

    private String currentLang = "ar";
    private SharedPreferences langPrefs;
    private YandexAdsHelper yandexAdsHelper;

    private DatabaseReference setsRef;
    private FirebaseAuth mAuth;
    private static final String DEV_UID = "GrWIoyYfgCVU3BJjZzfu2T0PYdo2";

    private ViewPager2 viewPagerSets;
    private ThemePagerAdapter adapter;
    private List<ThemeModel> themesList;
    private TextView tvTitle;

    private int titleClickCount = 0;
    private Handler titleClickHandler;
    private Runnable titleClickRunnable;

    private ViewGroup appRootView;

    // --- Skeleton UI ---
    private View skeletonView;
    private ObjectAnimator skeletonAnimator;

    private View tutorialOverlay;
    private AnimatorSet tutorialAnimator;

    public String tr(String arabicText, String englishText) {
        return "en".equals(currentLang) ? englishText : arabicText;
    }

    private void applyLanguage() {
        langPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentLang = langPrefs.getString("app_language", "ar");
    }

    private void goBackToMain() {
        // تم حذف الإعلان من زر الرجوع حفاظاً على سياسات جوجل 2026 🚨
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyLanguage();
        EdgeToEdgeHelper.enable(getWindow());
        setContentView(R.layout.them2026);

        titleClickHandler = new Handler(Looper.getMainLooper());

        mAuth = FirebaseAuth.getInstance();
        setsRef = FirebaseDatabase.getInstance().getReference("them_20"); 
        appRootView = findViewById(R.id.mainContentView);

        initializeLogic();
        setupGlobalFrameLayouts();
        checkAndShowTutorial();

        // 🌟 إظهار تأثير الـ Skeleton Loading عند بدء التشغيل 🌟
        showSkeletonLoading();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (yandexAdsHelper != null && yandexAdsHelper.isShowingAdDialog()) return;
                goBackToMain();
            }
        });
    }

    private void checkAndShowTutorial() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean hasShownTutorial = prefs.getBoolean("has_shown_swipe_tutorial", false);

        if (!hasShownTutorial) {
            tutorialOverlay = findViewById(R.id.tutorialOverlay);
            if (tutorialOverlay != null) {
                tutorialOverlay.setVisibility(View.VISIBLE);

                // 🌟 الحل الجذري لمشكلة التمرير: إخفاء الشاشة عند لمسها أو محاولة سحبها مباشرة 🌟
                tutorialOverlay.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_DOWN) {
                        tutorialOverlay.setVisibility(View.GONE);
                        if (tutorialAnimator != null) {
                            tutorialAnimator.cancel();
                        }
                        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putBoolean("has_shown_swipe_tutorial", true).apply();
                    }
                    return true;
                });
                
                TextView tvTutorialLabel = findViewById(R.id.tvTutorialLabel);
                if (tvTutorialLabel != null) {
                    tvTutorialLabel.setText(tr("اسحب للأعلى لاستكشاف المزيد", "Swipe up to explore more"));
                }

                ImageView imgHandSwipe = findViewById(R.id.imgHandSwipe);
                ImageView imgSwipeArrows = findViewById(R.id.imgSwipeArrows);
                
                ObjectAnimator handAnimY = ObjectAnimator.ofFloat(imgHandSwipe, "translationY", 0f, -300f);
                handAnimY.setDuration(2500);
                handAnimY.setRepeatCount(ValueAnimator.INFINITE);
                
                ObjectAnimator handAlpha = ObjectAnimator.ofFloat(imgHandSwipe, "alpha", 1f, 0f);
                handAlpha.setDuration(2500);
                handAlpha.setRepeatCount(ValueAnimator.INFINITE);
                
                ObjectAnimator arrowsAlpha = ObjectAnimator.ofFloat(imgSwipeArrows, "alpha", 0.3f, 1f, 0.3f);
                arrowsAlpha.setDuration(2500);
                arrowsAlpha.setRepeatCount(ValueAnimator.INFINITE);

                tutorialAnimator = new AnimatorSet();
                tutorialAnimator.playTogether(handAnimY, handAlpha, arrowsAlpha);
                tutorialAnimator.start();
            }
        }
    }

    // 🌟 دالة إظهار الهيكل الوهمي 🌟
    private void showSkeletonLoading() {
        if (viewPagerSets != null) {
            viewPagerSets.setVisibility(View.INVISIBLE);
        }
        
        ViewGroup parent = findViewById(R.id.brightBackgroundTarget);
        if (parent == null) parent = appRootView;
        
        if (skeletonView == null) {
            skeletonView = LayoutInflater.from(this).inflate(R.layout.item_theme_skeleton, parent, false);
            parent.addView(skeletonView);
            
            // إضافة تأثير الوميض (Shimmer)
            skeletonAnimator = ObjectAnimator.ofFloat(skeletonView, "alpha", 0.5f, 1f); // 🌟 توضيح التأثير والشفافية
            skeletonAnimator.setDuration(800);
            skeletonAnimator.setRepeatMode(ValueAnimator.REVERSE);
            skeletonAnimator.setRepeatCount(ValueAnimator.INFINITE);
            skeletonAnimator.start();
        }
        skeletonView.setVisibility(View.VISIBLE);
    }

    // 🌟 دالة إخفاء الهيكل الوهمي 🌟
    private void hideSkeletonLoading() {
        if (skeletonView != null && skeletonView.getVisibility() == View.VISIBLE) {
            if (skeletonAnimator != null) {
                skeletonAnimator.cancel();
            }
            // إخفاء الـ Skeleton بتأثير تلاشي جميل وإظهار المحتوى الحقيقي
            skeletonView.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                skeletonView.setVisibility(View.GONE);
                if (viewPagerSets != null) {
                    viewPagerSets.setAlpha(0f);
                    viewPagerSets.setVisibility(View.VISIBLE);
                    viewPagerSets.animate().alpha(1f).setDuration(400).start();
                }
            }).start();
        }
    }

    private void initializeLogic() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            ViewCompat.setOnApplyWindowInsetsListener(appRootView, (v, insets) -> {
                View blurTopBarNew = findViewById(R.id.blurTopBarNew);
                if (blurTopBarNew != null) {
                    float d = getResources().getDisplayMetrics().density;
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) blurTopBarNew.getLayoutParams();
                    params.topMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top + (int)(15*d);
                    blurTopBarNew.setLayoutParams(params);
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(appRootView);
        }

        yandexAdsHelper = YandexAdsHelper.getInstance(this);
        yandexAdsHelper.setCurrentLanguage(currentLang);

        themesList = new ArrayList<>();
        viewPagerSets = findViewById(R.id.viewPagerSets);
        viewPagerSets.setOrientation(ViewPager2.ORIENTATION_VERTICAL);

        ViewGroup brightTarget = findViewById(R.id.brightBackgroundTarget);
        if (brightTarget == null) brightTarget = appRootView;

        adapter = new ThemePagerAdapter(brightTarget);
        viewPagerSets.setAdapter(adapter);

        viewPagerSets.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    if (tutorialOverlay != null && tutorialOverlay.getVisibility() == View.VISIBLE) {
                        tutorialOverlay.setVisibility(View.GONE);
                        if (tutorialAnimator != null) {
                            tutorialAnimator.cancel();
                        }
                        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putBoolean("has_shown_swipe_tutorial", true).apply();
                    }
                }
            }
        });

        tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) {
            tvTitle.setText(tr("ثيمات الأورج", "Org Themes"));
            tvTitle.setOnClickListener(v -> {
                titleClickCount++;
                if (titleClickRunnable != null) titleClickHandler.removeCallbacks(titleClickRunnable);
                titleClickRunnable = () -> titleClickCount = 0;
                titleClickHandler.postDelayed(titleClickRunnable, 2000);
                if (titleClickCount >= 7) {
                    titleClickCount = 0;
                    showDeveloperPanel();
                }
            });
        }

        TextView tvListBtn = findViewById(R.id.tvListBtn);
        if(tvListBtn != null) tvListBtn.setText(tr("عرض قائمة الثيمات", "Themes List"));

        findViewById(R.id.btnBack).setOnClickListener(v -> goBackToMain());
        View btnListSets = findViewById(R.id.btnListSets);
        if (btnListSets != null) {
            btnListSets.setOnClickListener(v -> showThemesListDialog());
        }

        loadThemesFromCache();
        loadThemesFromFirebase();

        yandexAdsHelper.initialize(() -> {
            runOnUiThread(() -> {
                yandexAdsHelper.loadInterstitialAdSilent();
            });
        });
    }

    private void setupGlobalFrameLayouts() {}

    private void saveThemesToCache() {
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray();
            for (ThemeModel theme : themesList) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("set_id", theme.set_id);
                obj.put("title_ar", theme.title_ar);
                obj.put("title_en", theme.title_en);
                obj.put("description_ar", theme.description_ar);
                obj.put("description_en", theme.description_en);
                obj.put("download_url", theme.download_url);
                obj.put("image_preview_url", theme.image_preview_url); 
                obj.put("image_preview_url_2", theme.image_preview_url_2); 
                obj.put("timestamp", theme.timestamp);
                obj.put("is_active", theme.is_active);
                jsonArray.put(obj);
            }
            getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).edit().putString(KEY_SETS_JSON, jsonArray.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadThemesFromCache() {
        try {
            String json = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).getString(KEY_SETS_JSON, null);
            if (json != null) {
                org.json.JSONArray jsonArray = new org.json.JSONArray(json);
                themesList.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    org.json.JSONObject obj = jsonArray.getJSONObject(i);
                    ThemeModel theme = new ThemeModel();
                    theme.set_id = obj.optString("set_id");
                    theme.title_ar = obj.optString("title_ar");
                    theme.title_en = obj.optString("title_en");
                    theme.description_ar = obj.optString("description_ar");
                    theme.description_en = obj.optString("description_en");
                    theme.download_url = obj.optString("download_url");
                    theme.image_preview_url = obj.optString("image_preview_url");
                    theme.image_preview_url_2 = obj.optString("image_preview_url_2");
                    theme.timestamp = obj.optLong("timestamp");
                    theme.is_active = obj.optBoolean("is_active", true);
                    themesList.add(theme);
                }
                if (!themesList.isEmpty()) {
                    Collections.sort(themesList, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                    adapter.notifyDataSetChanged();
                    hideSkeletonLoading();
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadThemesFromFirebase() {
        setsRef.orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                themesList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ThemeModel theme = child.getValue(ThemeModel.class);
                    if (theme != null && theme.is_active) {
                        theme.set_id = child.getKey();
                        themesList.add(theme);
                    }
                }
                Collections.sort(themesList, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                adapter.notifyDataSetChanged(); 
                saveThemesToCache();
                hideSkeletonLoading();
                if(themesList.isEmpty()) {
                    hideSkeletonLoading();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                hideSkeletonLoading();
            }
        });
    }

    private void showThemesListDialog() {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
        }

        View baseView = LayoutInflater.from(this).inflate(R.layout.dialog_base_glass, null);
        dialog.setContentView(baseView);

        FrameLayout blurCenterWindow = baseView.findViewById(R.id.blurCenterWindow);

        MovingBorderView centerMovingBorder = baseView.findViewById(R.id.centerMovingBorder);
        if (centerMovingBorder != null) {
            centerMovingBorder.setCornerRadius(14f);
            centerMovingBorder.setGlowColor("#00FFFF");
            centerMovingBorder.setContinuousMode(true);
        }

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(tr("قائمة الثيمات", "Themes List"));
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        if (dynamicContainer != null) {
            RecyclerView recyclerList = new RecyclerView(this);
            recyclerList.setLayoutManager(new LinearLayoutManager(this));

            SimpleThemesAdapter simpleAdapter = new SimpleThemesAdapter(themesList, position -> {
                dialog.dismiss();
                viewPagerSets.setCurrentItem(position, true);
            });
            recyclerList.setAdapter(simpleAdapter);

            float d = getResources().getDisplayMetrics().density;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int)(250*d));
            recyclerList.setLayoutParams(params);

            dynamicContainer.addView(recyclerList);

            View btnCloseView = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, dynamicContainer, false);
            TextView tvCloseText = btnCloseView.findViewById(R.id.tvButtonText);
            if (tvCloseText != null) {
                tvCloseText.setText(tr("إغلاق", "Close"));
            }
            btnCloseView.setOnClickListener(v -> dialog.dismiss());

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnParams.topMargin = (int)(10 * d);
            dynamicContainer.addView(btnCloseView, btnParams);
        }

        dialog.setCancelable(true);
        dialog.show();
    }

    private void showDeveloperPanel() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || !DEV_UID.equals(user.getUid())) return;

        final float density = getResources().getDisplayMetrics().density;
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
        }

        View baseView = LayoutInflater.from(this).inflate(R.layout.dialog_base_glass, null);
        dialog.setContentView(baseView);

        FrameLayout blurCenterWindow = baseView.findViewById(R.id.blurCenterWindow);

        MovingBorderView centerMovingBorder = baseView.findViewById(R.id.centerMovingBorder);
        if (centerMovingBorder != null) {
            centerMovingBorder.setCornerRadius(14f);
            centerMovingBorder.setGlowColor("#00FFFF");
            centerMovingBorder.setContinuousMode(true);
        }

        TextView tvTitle = baseView.findViewById(R.id.tvDialogTitle);
        if (tvTitle != null) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(tr("لوحة المطور", "Developer Panel"));
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        if (dynamicContainer != null) {
            EditText etTitleAr = createDevField(density, tr("عنوان عربي", "Ar Title"));
            EditText etTitleEn = createDevField(density, tr("عنوان إنجليزي", "En Title"));
            EditText etDescAr = createDevField(density, tr("وصف الثيم (عربي)", "Ar Description"));
            EditText etDescEn = createDevField(density, tr("وصف الثيم (إنجليزي)", "En Description"));
            EditText etUrl = createDevField(density, tr("رابط التحميل", "Download URL"));
            EditText etImage1 = createDevField(density, tr("رابط الصورة الأولى", "Theme Image 1 URL"));
            EditText etImage2 = createDevField(density, tr("رابط الصورة الثانية", "Theme Image 2 URL"));

            dynamicContainer.addView(etTitleAr);
            dynamicContainer.addView(etTitleEn);
            dynamicContainer.addView(etDescAr);
            dynamicContainer.addView(etDescEn);
            dynamicContainer.addView(etUrl);
            dynamicContainer.addView(etImage1);
            dynamicContainer.addView(etImage2);

            View btnPublishView = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, dynamicContainer, false);
            TextView tvPublishText = btnPublishView.findViewById(R.id.tvButtonText);
            if (tvPublishText != null) {
                tvPublishText.setText(tr("نشر في ثيمات الأورج", "Publish (Themes)"));
                tvPublishText.setTextColor(Color.parseColor("#C5A059"));
            }

            btnPublishView.setOnClickListener(v -> {
                String key = setsRef.push().getKey();
                ThemeModel newTheme = new ThemeModel();
                newTheme.title_ar = etTitleAr.getText().toString().trim();
                newTheme.title_en = etTitleEn.getText().toString().trim();
                newTheme.description_ar = etDescAr.getText().toString().trim();
                newTheme.description_en = etDescEn.getText().toString().trim();
                newTheme.download_url = etUrl.getText().toString().trim();
                newTheme.image_preview_url = etImage1.getText().toString().trim();
                newTheme.image_preview_url_2 = etImage2.getText().toString().trim();
                newTheme.timestamp = System.currentTimeMillis();
                newTheme.is_active = true;

                if (newTheme.title_ar.isEmpty() || newTheme.download_url.isEmpty()) {
                    Toast.makeText(this, "أدخل العنوان والرابط", Toast.LENGTH_SHORT).show();
                    return;
                }
                setsRef.child(key).setValue(newTheme).addOnSuccessListener(a -> {
                    Toast.makeText(this, "تم النشر بنجاح!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
            dynamicContainer.addView(btnPublishView);

            View btnCloseView = LayoutInflater.from(this).inflate(R.layout.item_dialog_button, dynamicContainer, false);
            TextView tvCloseText = btnCloseView.findViewById(R.id.tvButtonText);
            if (tvCloseText != null) {
                tvCloseText.setText(tr("إغلاق", "Close"));
            }
            btnCloseView.setOnClickListener(v -> dialog.dismiss());
            dynamicContainer.addView(btnCloseView);
        }

        dialog.setCancelable(true);
        dialog.show();
    }

    private EditText createDevField(float d, String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.GRAY);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int)(45*d));
        params.bottomMargin = (int)(10*d);
        et.setLayoutParams(params);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(10 * d);
        bg.setStroke((int)(1 * d), Color.parseColor("#C5A059"));
        et.setBackground(bg);
        et.setPadding((int)(12*d), 0, (int)(12*d), 0);
        return et;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideSkeletonLoading();
        if (titleClickHandler != null && titleClickRunnable != null) titleClickHandler.removeCallbacks(titleClickRunnable);
        if (tutorialAnimator != null) tutorialAnimator.cancel();
        if (skeletonAnimator != null) skeletonAnimator.cancel();
    }

    public static class ThemeModel {
        public String set_id;
        public String title_ar;
        public String title_en;
        public String description_ar;
        public String description_en;
        public String download_url;
        public String image_preview_url; 
        public String image_preview_url_2;
        public long timestamp;
        public boolean is_active = true;
    }

    private class ThemePagerAdapter extends RecyclerView.Adapter<ThemePagerAdapter.SetViewHolder> {

        private ViewGroup rootBlurTarget;

        public ThemePagerAdapter(ViewGroup root) {
            this.rootBlurTarget = root;
        }

        @NonNull
        @Override
        public SetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme_new, parent, false);
            return new SetViewHolder(view, rootBlurTarget);
        }

        @Override
        public void onBindViewHolder(@NonNull SetViewHolder holder, int position) {
            ThemeModel theme = themesList.get(position);

            String title = "en".equals(currentLang) && !theme.title_en.isEmpty() ? theme.title_en : theme.title_ar;
            String desc = "en".equals(currentLang) && theme.description_en != null && !theme.description_en.isEmpty() ? theme.description_en : theme.description_ar;

            holder.tvSetTitle.setText(title);
            holder.tvSetDescription.setText(desc);

            // تحميل الصورة الأولى
            if (theme.image_preview_url != null && !theme.image_preview_url.isEmpty()) {
                holder.imageLoadingBar1.setVisibility(View.VISIBLE);
                Glide.with(holder.itemView.getContext())
                        .load(theme.image_preview_url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                holder.imageLoadingBar1.setVisibility(View.GONE);
                                return false;
                            }
                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                holder.imageLoadingBar1.setVisibility(View.GONE);
                                return false;
                            }
                        })
                        .into(holder.imgThemePreview1);
            } else {
                holder.imgThemePreview1.setImageResource(R.drawable.img_org_preview);
                holder.imageLoadingBar1.setVisibility(View.GONE);
            }

            // تحميل الصورة الثانية
            if (theme.image_preview_url_2 != null && !theme.image_preview_url_2.isEmpty()) {
                holder.imageLoadingBar2.setVisibility(View.VISIBLE);
                Glide.with(holder.itemView.getContext())
                        .load(theme.image_preview_url_2)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                holder.imageLoadingBar2.setVisibility(View.GONE);
                                return false;
                            }
                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                holder.imageLoadingBar2.setVisibility(View.GONE);
                                return false;
                            }
                        })
                        .into(holder.imgThemePreview2);
            } else {
                holder.imgThemePreview2.setImageResource(R.drawable.img_org_preview);
                holder.imageLoadingBar2.setVisibility(View.GONE);
            }

            holder.btnDownloadSet.setOnClickListener(v -> {
                yandexAdsHelper.showAdDialog(theme.download_url, pendingUrl -> yandexAdsHelper.openDownloadUrl(pendingUrl));
            });
        }

        @Override
        public int getItemCount() { return themesList.size(); }

        class SetViewHolder extends RecyclerView.ViewHolder {
            TextView tvSetTitle, tvSetDescription;
            View btnDownloadSet;
            
            MovingBorderView centerMovingBorder;
            TextView tvPreviewLabel, tvDownloadLabel;
            
            ImageView imgThemePreview1, imgThemePreview2;
            ProgressBar imageLoadingBar1, imageLoadingBar2;

            public SetViewHolder(@NonNull View itemView, ViewGroup root) {
                super(itemView);
                tvSetTitle = itemView.findViewById(R.id.tvSetTitle);
                tvSetDescription = itemView.findViewById(R.id.tvSetDescription);
                btnDownloadSet = itemView.findViewById(R.id.btnDownloadSet);
                centerMovingBorder = itemView.findViewById(R.id.centerMovingBorder);
                tvPreviewLabel = itemView.findViewById(R.id.tvPreviewLabel);
                tvDownloadLabel = itemView.findViewById(R.id.tvDownloadLabel);
                
                imgThemePreview1 = itemView.findViewById(R.id.imgThemePreview1);
                imageLoadingBar1 = itemView.findViewById(R.id.imageLoadingBar1);
                imgThemePreview2 = itemView.findViewById(R.id.imgThemePreview2);
                imageLoadingBar2 = itemView.findViewById(R.id.imageLoadingBar2);

                if(tvPreviewLabel != null) tvPreviewLabel.setText(tr("معاينة الثيم", "Preview Theme"));
                if(tvDownloadLabel != null) tvDownloadLabel.setText(tr("تحميل الثيم", "Download Theme"));

                FrameLayout blurCenter = itemView.findViewById(R.id.blurCenterWindow);
                FrameLayout blurBottom = itemView.findViewById(R.id.blurBottomInfo);
                FrameLayout blurDown = itemView.findViewById(R.id.blurDownloadBtn);

                if (root != null) {
                    
                    
                    
                }

                if (centerMovingBorder != null) {
                    centerMovingBorder.setCornerRadius(14f);
                    centerMovingBorder.setGlowColor("#00FFFF");
                    centerMovingBorder.setContinuousMode(true);
                }
            }
        }
    }

    private class SimpleThemesAdapter extends RecyclerView.Adapter<SimpleThemesAdapter.SimpleVH> {
        List<ThemeModel> list;
        OnItemClickListener listener;
        public SimpleThemesAdapter(List<ThemeModel> list, OnItemClickListener listener) {
            this.list = list; this.listener = listener;
        }
        @NonNull @Override
        public SimpleVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_button, parent, false);
            return new SimpleVH(view);
        }
        @Override
        public void onBindViewHolder(@NonNull SimpleVH holder, int position) {
            ThemeModel theme = list.get(position);
            String text = "en".equals(currentLang) ? theme.title_en : theme.title_ar;
            holder.tv.setText(text);
            holder.itemView.setOnClickListener(v -> listener.onClick(position));
        }
        @Override public int getItemCount() { return list.size(); }
        class SimpleVH extends RecyclerView.ViewHolder {
            TextView tv;
            public SimpleVH(@NonNull View itemView) {
                super(itemView);
                tv = itemView.findViewById(R.id.tvButtonText);
            }
        }
    }
    interface OnItemClickListener { void onClick(int position); }
}