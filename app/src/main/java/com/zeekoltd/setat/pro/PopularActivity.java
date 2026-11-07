package com.zeekoltd.setat.pro;

import android.animation.AnimatorSet;
import android.widget.FrameLayout;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PopularActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ad_frequency_prefs";
    private static final String CACHE_PREFS = "app_cache";
    private static final String KEY_POPULAR_SETS_JSON = "popular_sets_list_v1";

    private String currentLang = "ar";
    private SharedPreferences langPrefs;
    private AdMobAdsHelper adMobAdsHelper;

    private DatabaseReference likesRef;
    private FirebaseAuth mAuth;
    private static final String DEV_UID = "GrWIoyYfgCVU3BJjZzfu2T0PYdo2";

    private ViewPager2 viewPagerSets;
    private SetPagerAdapter adapter;
    private List<NewActivity.SetModelV2> setsList;
    private TextView tvTitle;

    // --- Skeleton UI ---
    private View skeletonView;
    private ObjectAnimator skeletonAnimator;

    // --- Media3 ExoPlayer ---
    private ExoPlayer exoPlayer;
    private int currentExoPosition = -1;

    private ViewGroup appRootView;
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;

    private Set<String> likedSetIds = new HashSet<>();
    private Set<String> favoriteSetIds = new HashSet<>();

    private View tutorialOverlay;
    private AnimatorSet tutorialAnimator;
    
    // قائمة بالعقد (الأقسام) التي سيتم جلب السيتات منها
    private final String[] ALL_CATEGORIES = {
            "exclusive_sets_v2", "zeeko_v4", "setat_shabi", "dabakat",
            "iraq_sets_v2", "egept", "ehtrafe", "dzir", "sori", "yeme", "them_20"
    };

    public String tr(String arabicText, String englishText) {
        return "en".equals(currentLang) ? englishText : arabicText;
    }

    private void applyLanguage() {
        langPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentLang = langPrefs.getString("app_language", "ar");
    }

    private void goBackToMain() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        startActivity(new Intent(PopularActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyLanguage();
        EdgeToEdgeHelper.enable(getWindow());
        setContentView(R.layout.popularactivity);

        mAuth = FirebaseAuth.getInstance();
        likesRef = FirebaseDatabase.getInstance().getReference("liked_sets");
        appRootView = findViewById(R.id.popularContentView);

        initializeLogic();
        setupGlobalFrameLayouts();
        loadUserPreferences();
        checkAndShowTutorial();
        
        showSkeletonLoading();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adMobAdsHelper != null && adMobAdsHelper.isShowingAdDialog()) return;
                goBackToMain();
            }
        });
    }

    private void showSkeletonLoading() {
        if (viewPagerSets != null) {
            viewPagerSets.setVisibility(View.INVISIBLE);
        }
        
        ViewGroup parent = findViewById(R.id.brightBackgroundTargetPop);
        if (parent == null) parent = appRootView;
        
        if (skeletonView == null) {
            skeletonView = LayoutInflater.from(this).inflate(R.layout.item_set_skeleton, parent, false);
            parent.addView(skeletonView);
            
            skeletonAnimator = ObjectAnimator.ofFloat(skeletonView, "alpha", 0.5f, 1f);
            skeletonAnimator.setDuration(800);
            skeletonAnimator.setRepeatMode(ValueAnimator.REVERSE);
            skeletonAnimator.setRepeatCount(ValueAnimator.INFINITE);
            skeletonAnimator.start();
        }
        skeletonView.setVisibility(View.VISIBLE);
    }

    private void hideSkeletonLoading() {
        if (skeletonView != null && skeletonView.getVisibility() == View.VISIBLE) {
            if (skeletonAnimator != null) skeletonAnimator.cancel();
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

    private void checkAndShowTutorial() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean hasShownTutorial = prefs.getBoolean("has_shown_swipe_tutorial_pop", false);

        if (!hasShownTutorial) {
            tutorialOverlay = findViewById(R.id.tutorialOverlayPop);
            if (tutorialOverlay != null) {
                tutorialOverlay.setVisibility(View.VISIBLE);
                tutorialOverlay.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_DOWN) {
                        tutorialOverlay.setVisibility(View.GONE);
                        if (tutorialAnimator != null) tutorialAnimator.cancel();
                        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putBoolean("has_shown_swipe_tutorial_pop", true).apply();
                    }
                    return true;
                });
                
                TextView tvTutorialLabel = findViewById(R.id.tvTutorialLabelPop);
                if (tvTutorialLabel != null) {
                    tvTutorialLabel.setText(tr("اسحب للأعلى لاستكشاف التريند", "Swipe up to explore trends"));
                }

                ImageView imgHandSwipe = findViewById(R.id.imgHandSwipePop);
                ImageView imgSwipeArrows = findViewById(R.id.imgSwipeArrowsPop);
                
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

    private void loadUserPreferences() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        likesRef.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                likedSetIds.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    likedSetIds.add(child.getKey());
                }
                if (adapter != null) adapter.notifyItemRangeChanged(0, adapter.getItemCount(), "LIKE_FAV_PAYLOAD");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("favorite_sets")
           .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                favoriteSetIds.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    favoriteSetIds.add(child.getKey());
                }
                if (adapter != null) adapter.notifyItemRangeChanged(0, adapter.getItemCount(), "LIKE_FAV_PAYLOAD");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void initializeLogic() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            ViewCompat.setOnApplyWindowInsetsListener(appRootView, (v, insets) -> {
                float d = getResources().getDisplayMetrics().density;
                View blurTopBar = findViewById(R.id.blurTopBarPop);
                if (blurTopBar != null) {
                    ViewGroup.MarginLayoutParams paramsTop = (ViewGroup.MarginLayoutParams) blurTopBar.getLayoutParams();
                    paramsTop.topMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top + (int)(15*d);
                    blurTopBar.setLayoutParams(paramsTop);
                }
                if (viewPagerSets != null) {
                    ViewGroup.MarginLayoutParams paramsBottom = (ViewGroup.MarginLayoutParams) viewPagerSets.getLayoutParams();
                    paramsBottom.bottomMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime()).bottom;
                    viewPagerSets.setLayoutParams(paramsBottom);
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(appRootView);
        }

        adMobAdsHelper = AdMobAdsHelper.getInstance(this);
        adMobAdsHelper.setCurrentLanguage(currentLang);

        setsList = new ArrayList<>();
        viewPagerSets = findViewById(R.id.viewPagerSetsPop);
        viewPagerSets.setOrientation(ViewPager2.ORIENTATION_VERTICAL);

        ViewGroup brightTarget = findViewById(R.id.brightBackgroundTargetPop);
        if (brightTarget == null) brightTarget = appRootView;

        adapter = new SetPagerAdapter(brightTarget);
        viewPagerSets.setAdapter(adapter);

        viewPagerSets.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
                debounceRunnable = () -> playAudioForPosition(position);
                debounceHandler.postDelayed(debounceRunnable, 150); 
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
                    if (exoPlayer != null && exoPlayer.isPlaying()) exoPlayer.pause();
                    
                    if (tutorialOverlay != null && tutorialOverlay.getVisibility() == View.VISIBLE) {
                        tutorialOverlay.setVisibility(View.GONE);
                        if (tutorialAnimator != null) tutorialAnimator.cancel();
                        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putBoolean("has_shown_swipe_tutorial_pop", true).apply();
                    }
                }
            }
        });

        tvTitle = findViewById(R.id.tvTitlePop);
        if (tvTitle != null) {
            tvTitle.setText(tr("رائج 🔥", "Trending 🔥"));
        }

        TextView tvListBtn = findViewById(R.id.tvListBtnPop);
        if(tvListBtn != null) tvListBtn.setText(tr("عرض السيتات الشائعة", "View Trending Sets"));

        findViewById(R.id.btnBackPop).setOnClickListener(v -> goBackToMain());
        View btnListSets = findViewById(R.id.btnListSetsPop);
        if (btnListSets != null) {
            btnListSets.setOnClickListener(v -> showSetsListDialog());
        }

        loadSetsFromCache();
        loadTop10PopularSets(); // جلب السيتات من جميع الأقسام

        adMobAdsHelper.initialize(() -> {
            runOnUiThread(() -> adMobAdsHelper.loadInterstitialAdSilent());
        });
    }

    private void setupGlobalFrameLayouts() {}

    private void saveSetsToCache() {
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray();
            for (NewActivity.SetModelV2 set : setsList) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("set_id", set.set_id);
                obj.put("title_ar", set.title_ar);
                obj.put("title_en", set.title_en);
                obj.put("description_ar", set.description_ar);
                obj.put("description_en", set.description_en);
                obj.put("download_url", set.download_url);
                obj.put("audio_preview_url", set.audio_preview_url);
                obj.put("timestamp", set.timestamp);
                obj.put("likes_count", set.likes_count);
                obj.put("is_active", set.is_active);
                // تم إضافة عقدة المصدر لكي نعرف القسم الذي أتى منه السيت عند تحديث اللايكات
                obj.put("source_node", set.source_node != null ? set.source_node : "");
                jsonArray.put(obj);
            }
            getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).edit().putString(KEY_POPULAR_SETS_JSON, jsonArray.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadSetsFromCache() {
        try {
            String json = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).getString(KEY_POPULAR_SETS_JSON, null);
            if (json != null) {
                org.json.JSONArray jsonArray = new org.json.JSONArray(json);
                setsList.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    org.json.JSONObject obj = jsonArray.getJSONObject(i);
                    NewActivity.SetModelV2 set = new NewActivity.SetModelV2();
                    set.set_id = obj.optString("set_id");
                    set.title_ar = obj.optString("title_ar");
                    set.title_en = obj.optString("title_en");
                    set.description_ar = obj.optString("description_ar");
                    set.description_en = obj.optString("description_en");
                    set.download_url = obj.optString("download_url");
                    set.audio_preview_url = obj.optString("audio_preview_url");
                    set.timestamp = obj.optLong("timestamp");
                    set.likes_count = obj.optInt("likes_count");
                    set.is_active = obj.optBoolean("is_active", true);
                    set.source_node = obj.optString("source_node", "");
                    set.isPlaying = false;
                    set.isLoading = false;
                    setsList.add(set);
                }
                if (!setsList.isEmpty()) {
                    adapter.notifyDataSetChanged();
                    hideSkeletonLoading();
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void initExoPlayer() {
        if (exoPlayer == null) {
            HttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();

            CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory()
                    .setCache(CacheManager.getInstance(this))
                    .setUpstreamDataSourceFactory(httpFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR); 

            exoPlayer = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(cacheFactory)) 
                    .build();

            exoPlayer.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (currentExoPosition == -1 || setsList.isEmpty() || currentExoPosition >= setsList.size()) return;
                    
                    NewActivity.SetModelV2 currentSet = setsList.get(currentExoPosition);

                    if (state == Player.STATE_READY) {
                        if (currentSet.isLoading || !currentSet.isPlaying) {
                            currentSet.isLoading = false;
                            currentSet.isPlaying = true;
                            adapter.notifyItemChanged(currentExoPosition, "PLAYBACK_STATE");
                        }
                    } else if (state == Player.STATE_ENDED) {
                        if (currentSet.isPlaying || currentSet.isLoading) {
                            currentSet.isPlaying = false;
                            currentSet.isLoading = false;
                            adapter.notifyItemChanged(currentExoPosition, "PLAYBACK_STATE");
                        }
                    } else if (state == Player.STATE_BUFFERING) {
                        if (!currentSet.isLoading) {
                            currentSet.isLoading = true;
                            adapter.notifyItemChanged(currentExoPosition, "PLAYBACK_STATE");
                        }
                    }
                }
            });
        }
    }

    private void playAudioForPosition(int position) {
        if (setsList.isEmpty() || position >= setsList.size()) return;
        
        NewActivity.SetModelV2 set = setsList.get(position);
        
        initExoPlayer();
        
        if (currentExoPosition != -1 && currentExoPosition != position) {
            NewActivity.SetModelV2 oldSet = setsList.get(currentExoPosition);
            if (oldSet.isPlaying || oldSet.isLoading) {
                oldSet.isPlaying = false;
                oldSet.isLoading = false;
                adapter.notifyItemChanged(currentExoPosition, "PLAYBACK_STATE");
            }
        }
        currentExoPosition = position;
        
        exoPlayer.stop();

        if (set.audio_preview_url == null || set.audio_preview_url.isEmpty()) {
            if (set.isLoading || set.isPlaying) {
                set.isLoading = false;
                set.isPlaying = false;
                adapter.notifyItemChanged(position, "PLAYBACK_STATE");
            }
            return;
        }

        MediaItem item = MediaItem.fromUri(set.audio_preview_url);
        exoPlayer.setMediaItem(item);
        exoPlayer.prepare();
        exoPlayer.play();
    }

    public void toggleAudioPlayback(int position) {
        if (currentExoPosition == position && exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
            setPlayingState(position, false);
        } else {
            playAudioForPosition(position);
        }
    }

    public void setPlayingState(int position, boolean isPlaying) {
        if(position >= 0 && position < setsList.size()){
            NewActivity.SetModelV2 set = setsList.get(position);
            if (set.isPlaying != isPlaying) {
                set.isPlaying = isPlaying;
                adapter.notifyItemChanged(position, "PLAYBACK_STATE");
            }
        }
    }

    // 🌟 جلب السيتات من جميع الأقسام، دمجها، وتصفيتها للحصول على أعلى 10 سيتات 🌟
    private void loadTop10PopularSets() {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        List<Task<DataSnapshot>> tasks = new ArrayList<>();

        for (String node : ALL_CATEGORIES) {
            tasks.add(db.getReference(node).get());
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            List<NewActivity.SetModelV2> allSets = new ArrayList<>();
            int idx = 0;
            for (Object result : results) {
                DataSnapshot snapshot = (DataSnapshot) result;
                String sourceNode = ALL_CATEGORIES[idx++];
                for (DataSnapshot child : snapshot.getChildren()) {
                    NewActivity.SetModelV2 set = child.getValue(NewActivity.SetModelV2.class);
                    if (set != null && set.is_active) {
                        set.set_id = child.getKey();
                        set.source_node = sourceNode; // حفظ المصدر لتحديث اللايكات في العقدة الصحيحة
                        set.isPlaying = false;
                        set.isLoading = false;
                        allSets.add(set);
                    }
                }
            }

            // ترتيب تنازلي حسب الإعجابات
            Collections.sort(allSets, (a, b) -> Integer.compare(b.likes_count, a.likes_count));

            // أخذ أعلى 10 سيتات فقط
            setsList.clear();
            for (int i = 0; i < Math.min(10, allSets.size()); i++) {
                setsList.add(allSets.get(i));
            }

            adapter.notifyDataSetChanged();
            saveSetsToCache();
            hideSkeletonLoading();
        }).addOnFailureListener(e -> {
            hideSkeletonLoading();
        });
    }

    private void showSetsListDialog() {
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

        ViewGroup targetView = decorView.findViewById(R.id.brightBackgroundTargetPop);
        if (targetView == null) {
            targetView = (ViewGroup) decorView.findViewById(android.R.id.content);
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
            tvTitle.setText(tr("قائمة السيتات الشائعة", "Trending Sets List"));
        }

        LinearLayout dynamicContainer = baseView.findViewById(R.id.dialogDynamicContainer);
        if (dynamicContainer != null) {
            RecyclerView recyclerList = new RecyclerView(this);
            recyclerList.setLayoutManager(new LinearLayoutManager(this));

            SimpleSetsAdapter simpleAdapter = new SimpleSetsAdapter(setsList, position -> {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideSkeletonLoading();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        if (debounceHandler != null && debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
        if (tutorialAnimator != null) tutorialAnimator.cancel();
        if (skeletonAnimator != null) skeletonAnimator.cancel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
            setPlayingState(currentExoPosition, false);
        }
    }

    private class SetPagerAdapter extends RecyclerView.Adapter<SetPagerAdapter.SetViewHolder> {

        private ViewGroup rootBlurTarget;

        public SetPagerAdapter(ViewGroup root) {
            this.rootBlurTarget = root;
        }

        @NonNull
        @Override
        public SetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_set_new, parent, false);
            return new SetViewHolder(view, rootBlurTarget);
        }

        @Override
        public void onBindViewHolder(@NonNull SetViewHolder holder, int position) {
            NewActivity.SetModelV2 set = setsList.get(position);
            FirebaseUser user = mAuth.getCurrentUser();

            String title = "en".equals(currentLang) && !set.title_en.isEmpty() ? set.title_en : set.title_ar;
            String desc = "en".equals(currentLang) && set.description_en != null && !set.description_en.isEmpty() ? set.description_en : set.description_ar;

            holder.tvSetTitle.setText(title);
            holder.tvSetDescription.setText(desc);
            holder.tvLikesCount.setText(String.valueOf(set.likes_count));

            // وضع تاج (ترتيب السيت) في زاوية الشاشة لتوضيح أنه تريند
            holder.tvPreviewLabel.setText("#" + (position + 1));
            holder.tvPreviewLabel.setTextColor(Color.parseColor("#FFD700"));

            holder.drawPlaybackUI(set);

            if (user != null) {
                if (likedSetIds.contains(set.set_id)) {
                    holder.btnLike.setColorFilter(Color.RED);
                } else {
                    holder.btnLike.setColorFilter(Color.WHITE);
                }

                if (favoriteSetIds.contains(set.set_id)) {
                    holder.btnFavorite.setColorFilter(Color.parseColor("#FFD700"));
                } else {
                    holder.btnFavorite.setColorFilter(Color.WHITE);
                }
            }

            holder.btnLike.setOnClickListener(v -> {
                if (user == null) {
                    Toast.makeText(PopularActivity.this, tr("قم بتسجيل الدخول للإعجاب", "Please login to like"), Toast.LENGTH_SHORT).show();
                    return;
                }

                // 🌟 تحديث اللايكات في العقدة الأصلية الخاصة بالسيت بناءً على `source_node` 🌟
                String targetNode = (set.source_node != null && !set.source_node.isEmpty()) ? set.source_node : "exclusive_sets_v2";
                DatabaseReference targetSetRef = FirebaseDatabase.getInstance().getReference(targetNode);

                if (likedSetIds.contains(set.set_id)) {
                    likesRef.child(user.getUid()).child(set.set_id).removeValue();
                    likedSetIds.remove(set.set_id);
                    targetSetRef.child(set.set_id).child("likes_count").setValue(set.likes_count - 1);
                    holder.btnLike.setColorFilter(Color.WHITE);
                    holder.tvLikesCount.setText(String.valueOf(set.likes_count - 1));
                    set.likes_count -= 1;
                } else {
                    likesRef.child(user.getUid()).child(set.set_id).setValue(true);
                    likedSetIds.add(set.set_id);
                    targetSetRef.child(set.set_id).child("likes_count").setValue(set.likes_count + 1);
                    holder.btnLike.setColorFilter(Color.RED);
                    holder.tvLikesCount.setText(String.valueOf(set.likes_count + 1));
                    set.likes_count += 1;
                }
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) adapter.notifyItemChanged(pos, "LIKE_FAV_PAYLOAD");
            });

            holder.btnFavorite.setOnClickListener(v -> {
                if (user == null) {
                    Toast.makeText(PopularActivity.this, tr("قم بتسجيل الدخول لحفظ السيت", "Please login to save"), Toast.LENGTH_SHORT).show();
                    return;
                }

                DatabaseReference favRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("favorite_sets").child(set.set_id);
                if (favoriteSetIds.contains(set.set_id)) {
                    favRef.removeValue();
                    favoriteSetIds.remove(set.set_id);
                    holder.btnFavorite.setColorFilter(Color.WHITE);
                    Toast.makeText(PopularActivity.this, tr("تم الإزالة من المفضلة", "Removed from favorites"), Toast.LENGTH_SHORT).show();
                } else {
                    favRef.setValue(set);
                    favoriteSetIds.add(set.set_id);
                    holder.btnFavorite.setColorFilter(Color.parseColor("#FFD700"));
                    Toast.makeText(PopularActivity.this, tr("تم الحفظ بنجاح", "Saved successfully"), Toast.LENGTH_SHORT).show();
                }
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) adapter.notifyItemChanged(pos, "LIKE_FAV_PAYLOAD");
            });

            holder.btnDownloadSet.setOnClickListener(v -> {
                if (exoPlayer != null && exoPlayer.isPlaying()) toggleAudioPlayback(position);
                adMobAdsHelper.showAdDialog(set.download_url, pendingUrl -> adMobAdsHelper.openDownloadUrl(pendingUrl));
            });

            holder.btnPlayPause.setOnClickListener(v -> toggleAudioPlayback(position));
        }

        @Override
        public int getItemCount() { return setsList.size(); }

        @Override
        public void onBindViewHolder(@NonNull SetViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (payloads.isEmpty()) {
                super.onBindViewHolder(holder, position, payloads);
            } else {
                NewActivity.SetModelV2 set = setsList.get(position);
                FirebaseUser user = mAuth.getCurrentUser();
                
                for (Object payload : payloads) {
                    if ("PLAYBACK_STATE".equals(payload)) {
                        holder.drawPlaybackUI(set);
                    } else if ("LIKE_FAV_PAYLOAD".equals(payload)) {
                        if (user != null) {
                            holder.btnLike.setColorFilter(likedSetIds.contains(set.set_id) ? Color.RED : Color.WHITE);
                            holder.btnFavorite.setColorFilter(favoriteSetIds.contains(set.set_id) ? Color.parseColor("#FFD700") : Color.WHITE);
                        }
                        holder.tvLikesCount.setText(String.valueOf(set.likes_count));
                    }
                }
            }
        }

        class SetViewHolder extends RecyclerView.ViewHolder {
            TextView tvSetTitle, tvSetDescription, tvLikesCount;
            ImageView btnLike, btnFavorite, btnPlayPause;
            View btnDownloadSet;
            ProgressBar audioLoadingBar;
            
            SmoothWaveView smoothWave;
            
            MovingBorderView centerMovingBorder;
            TextView tvPreviewLabel, tvDownloadLabel, tvPlayStatus;
            ImageView imgDevIcon;

            public SetViewHolder(@NonNull View itemView, ViewGroup root) {
                super(itemView);
                tvSetTitle = itemView.findViewById(R.id.tvSetTitle);
                tvSetDescription = itemView.findViewById(R.id.tvSetDescription);
                tvLikesCount = itemView.findViewById(R.id.tvLikesCount);
                btnLike = itemView.findViewById(R.id.btnLike);
                btnFavorite = itemView.findViewById(R.id.btnFavorite);
                btnPlayPause = itemView.findViewById(R.id.btnPlayPause);
                btnDownloadSet = itemView.findViewById(R.id.btnDownloadSet);
                audioLoadingBar = itemView.findViewById(R.id.audioLoadingBar);
                centerMovingBorder = itemView.findViewById(R.id.centerMovingBorder);
                tvPreviewLabel = itemView.findViewById(R.id.tvPreviewLabel);
                tvDownloadLabel = itemView.findViewById(R.id.tvDownloadLabel);
                tvPlayStatus = itemView.findViewById(R.id.tvPlayStatus);
                imgDevIcon = itemView.findViewById(R.id.imgDevIcon);
                
                smoothWave = itemView.findViewById(R.id.smoothWave);

                if(tvDownloadLabel != null) tvDownloadLabel.setText(tr("تحميل السيت", "Download Set"));
                if(tvPlayStatus != null) tvPlayStatus.setText(tr("تشغيل", "Play"));

                FirebaseDatabase.getInstance().getReference("user_avatars").child(DEV_UID).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            try {
                                String base64 = snapshot.getValue(String.class);
                                byte[] decoded = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                                Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                                imgDevIcon.setImageBitmap(bmp);
                            } catch (Exception e) { imgDevIcon.setImageResource(R.drawable.ic_default_user); }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

                FrameLayout blurCenter = itemView.findViewById(R.id.blurCenterWindow);
                FrameLayout blurDev = itemView.findViewById(R.id.blurDevIcon);
                FrameLayout blurLike = itemView.findViewById(R.id.blurLikeBtn);
                FrameLayout blurFav = itemView.findViewById(R.id.blurFavBtn);
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

            public void drawPlaybackUI(NewActivity.SetModelV2 set) {
                if (set.isLoading) {
                    audioLoadingBar.setVisibility(View.VISIBLE);
                    btnPlayPause.setVisibility(View.GONE);
                    tvPlayStatus.setText(tr("جاري التحميل...", "Loading..."));
                    stopVisualizer();
                } else {
                    audioLoadingBar.setVisibility(View.GONE);
                    btnPlayPause.setVisibility(View.VISIBLE);
                    if (set.isPlaying) {
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                        tvPlayStatus.setText(tr("توقف", "Pause"));
                        startVisualizer();
                    } else {
                        btnPlayPause.setImageResource(R.drawable.ic_play_glow);
                        tvPlayStatus.setText(tr("تشغيل", "Play"));
                        stopVisualizer();
                    }
                }
            }

            public void startVisualizer() {
                if (smoothWave != null) smoothWave.startAnimation();
            }

            public void stopVisualizer() {
                if (smoothWave != null) smoothWave.stopAnimation();
            }
        }
    }

    private class SimpleSetsAdapter extends RecyclerView.Adapter<SimpleSetsAdapter.SimpleVH> {
        List<NewActivity.SetModelV2> list;
        OnItemClickListener listener;
        public SimpleSetsAdapter(List<NewActivity.SetModelV2> list, OnItemClickListener listener) {
            this.list = list; this.listener = listener;
        }
        @NonNull @Override
        public SimpleVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_button, parent, false);
            return new SimpleVH(view);
        }
        @Override
        public void onBindViewHolder(@NonNull SimpleVH holder, int position) {
            NewActivity.SetModelV2 set = list.get(position);
            String text = "en".equals(currentLang) ? set.title_en : set.title_ar;
            holder.tv.setText((position + 1) + ". " + text); // إظهار ترتيب التريند في القائمة
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