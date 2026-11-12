package com.zeekoltd.setat.pro;

import android.animation.AnimatorSet;
import android.widget.FrameLayout;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
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

// استيرادات مكتبة Media3 الجديدة بدلاً من ExoPlayer2 و MediaPlayer
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FavoritesActivity extends AppCompatActivity {


    private static final String CACHE_PREFS = "app_cache";
    private static final String KEY_FAV_SETS_JSON = "fav_sets_list_v2";
    
    private String currentLang = "ar";
    private SharedPreferences langPrefs;
    private AdMobAdsHelper adMobAdsHelper;
    
    private DatabaseReference favRef;
    private DatabaseReference likesRef;
    private FirebaseAuth mAuth;
    private static final String DEV_UID = "GrWIoyYfgCVU3BJjZzfu2T0PYdo2";

    private ViewPager2 viewPagerSets;
    private SetPagerAdapter adapter;
    private List<SetModelV2> setsList;
    private TextView tvTitle, tvEmptyState;

    // --- Media3 ExoPlayer & Cache ---
    private ExoPlayer exoPlayer;
    private int currentExoPosition = -1;

    private ViewGroup appRootView;

    // 🌟 Debounce للسحب السريع في شاشة المفضلة 🌟
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;

    // نافذة التحميل الأولى
    private Dialog initialLoadingDialog;

    // قوائم التخزين المؤقت للإعجابات والمفضلة
    private Set<String> likedSetIds = new HashSet<>();
    private Set<String> favoriteSetIds = new HashSet<>();

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
        // تم حذف الإعلان البيني من زر الرجوع حفاظاً على سياسات جوجل 2026 🚨
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyLanguage();
        EdgeToEdgeHelper.enable(getWindow());
        setContentView(R.layout.favorites_activity);
        
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        
        appRootView = findViewById(R.id.mainContentView);
        tvEmptyState = findViewById(R.id.tvEmptyState); // تهيئة مبكرة لتجنب الـ Null
        
        // 🌟 التعديل الأول: السماح بدخول الشاشة وتنبيه المستخدم فقط دون طرده 🌟
        if (user == null) {
            Toast.makeText(this, tr("قم بتسجيل الدخول لرؤية المفضلة", "Login to view favorites"), Toast.LENGTH_SHORT).show();
            if(tvEmptyState != null) {
                tvEmptyState.setVisibility(View.VISIBLE);
                tvEmptyState.setText(tr("قم بتسجيل الدخول لرؤية سيتاتك المفضلة", "Please log in to view your favorite sets"));
            }
            // لا يوجد finish() هنا
        } else {
            favRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("favorite_sets");
            likesRef = FirebaseDatabase.getInstance().getReference("liked_sets");
            initialLoadingDialog = DialogHelper.showLoadingDialog(this, currentLang, tr("جاري تحميل المفضلة...", "Loading Favorites..."));
            loadUserPreferences(); 
            loadSetsFromFirebase();
        }

        initializeLogic();
        setupGlobalFrameLayouts();
        
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adMobAdsHelper != null && adMobAdsHelper.isShowingAdDialog()) return;
                goBackToMain();
            }
        });
    }

    private void dismissInitialLoading() {
        if (initialLoadingDialog != null && initialLoadingDialog.isShowing()) {
            initialLoadingDialog.dismiss();
            initialLoadingDialog = null;
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
        
        adMobAdsHelper = AdMobAdsHelper.getInstance(this);
        adMobAdsHelper.setCurrentLanguage(currentLang);
        
        setsList = new ArrayList<>();
        viewPagerSets = findViewById(R.id.viewPagerSets);
        viewPagerSets.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        
        ViewGroup brightTarget = findViewById(R.id.brightBackgroundTarget);
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
                    if (debounceRunnable != null) {
                        debounceHandler.removeCallbacks(debounceRunnable);
                    }
                    if (exoPlayer != null && exoPlayer.isPlaying()) {
                        exoPlayer.pause();
                    }
                }
            }
        });

        tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) {
            tvTitle.setText(tr("المفضلة", "Favorites"));
        }
        
        TextView tvListBtn = findViewById(R.id.tvListBtn);
        if(tvListBtn != null) tvListBtn.setText(tr("عرض قائمة السيتات", "Sets List"));

        findViewById(R.id.btnBack).setOnClickListener(v -> goBackToMain());
        View btnListSets = findViewById(R.id.btnListSets);
        if (btnListSets != null) {
            btnListSets.setOnClickListener(v -> showSetsListDialog());
        }

        loadSetsFromCache();

        adMobAdsHelper.initialize(() -> runOnUiThread(() -> {}));
    }

    private void setupGlobalFrameLayouts() {}

    private void saveSetsToCache() {
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray();
            for (SetModelV2 set : setsList) {
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
                jsonArray.put(obj);
            }
            getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).edit().putString(KEY_FAV_SETS_JSON, jsonArray.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadSetsFromCache() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            checkEmptyState();
            return;
        }

        try {
            String json = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).getString(KEY_FAV_SETS_JSON, null);
            if (json != null) {
                org.json.JSONArray jsonArray = new org.json.JSONArray(json);
                setsList.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    org.json.JSONObject obj = jsonArray.getJSONObject(i);
                    SetModelV2 set = new SetModelV2();
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
                    set.isPlaying = false;
                    set.isLoading = false;
                    setsList.add(set);
                }
                if (!setsList.isEmpty()) {
                    Collections.sort(setsList, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                    adapter.notifyDataSetChanged();
                    checkEmptyState();
                    dismissInitialLoading();
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
                    
                    SetModelV2 currentSet = setsList.get(currentExoPosition);

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
        
        SetModelV2 set = setsList.get(position);
        
        initExoPlayer();
        
        if (currentExoPosition != -1 && currentExoPosition != position) {
            SetModelV2 oldSet = setsList.get(currentExoPosition);
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

    public void setLoadingState(int position, boolean isLoading) {
        if(position >= 0 && position < setsList.size()){
            SetModelV2 set = setsList.get(position);
            if (set.isLoading != isLoading) {
                set.isLoading = isLoading;
                adapter.notifyItemChanged(position, "PLAYBACK_STATE");
            }
        }
    }

    public void setPlayingState(int position, boolean isPlaying) {
        if(position >= 0 && position < setsList.size()){
            SetModelV2 set = setsList.get(position);
            if (set.isPlaying != isPlaying) {
                set.isPlaying = isPlaying;
                adapter.notifyItemChanged(position, "PLAYBACK_STATE");
            }
        }
    }

    private void loadSetsFromFirebase() {
        if (favRef == null) return;
        
        favRef.orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int oldSize = setsList.size(); 
                
                String activeSetId = null;
                boolean activeIsPlaying = false;
                boolean activeIsLoading = false;

                if (currentExoPosition != -1 && currentExoPosition < setsList.size()) {
                    SetModelV2 activeSet = setsList.get(currentExoPosition);
                    activeSetId = activeSet.set_id;
                    activeIsPlaying = activeSet.isPlaying;
                    activeIsLoading = activeSet.isLoading;
                }

                setsList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    SetModelV2 set = child.getValue(SetModelV2.class);
                    if (set != null && set.is_active) {
                        set.set_id = child.getKey();
                        
                        if (activeSetId != null && activeSetId.equals(set.set_id)) {
                            set.isPlaying = activeIsPlaying;
                            set.isLoading = activeIsLoading;
                        } else {
                            set.isPlaying = false;
                            set.isLoading = false;
                        }
                        
                        setsList.add(set);
                    }
                }
                Collections.sort(setsList, (a, b) -> Long.compare(b.timestamp, a.timestamp));

                int newSize = setsList.size(); 

                if (activeSetId != null) {
                    boolean found = false;
                    for (int i = 0; i < setsList.size(); i++) {
                        if (setsList.get(i).set_id.equals(activeSetId)) {
                            currentExoPosition = i;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        currentExoPosition = -1;
                        if (exoPlayer != null) exoPlayer.stop();
                    }
                }

                if (oldSize != newSize) {
                    adapter.notifyDataSetChanged(); 
                } else if (currentExoPosition == -1) {
                    adapter.notifyDataSetChanged(); 
                } else {
                    adapter.notifyItemRangeChanged(0, setsList.size(), "LIKE_FAV_PAYLOAD"); 
                }
                
                saveSetsToCache();
                checkEmptyState();
                dismissInitialLoading();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                dismissInitialLoading();
            }
        });
    }

    private void checkEmptyState() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText(tr("قم بتسجيل الدخول لرؤية سيتاتك المفضلة", "Please log in to view your favorite sets"));
        } else if (setsList.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText(tr("لا يوجد سيتات في المفضلة بعد", "No favorite sets yet"));
        } else {
            tvEmptyState.setVisibility(View.GONE);
        }
    }

    private void showSetsListDialog() {
        if (setsList.isEmpty()) {
            Toast.makeText(this, tr("المفضلة فارغة", "Favorites are empty"), Toast.LENGTH_SHORT).show();
            return;
        }
        
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
            tvTitle.setText(tr("قائمة المفضلة", "Favorites List"));
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
        dismissInitialLoading();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        if (debounceHandler != null && debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
            setPlayingState(currentExoPosition, false);
        }
    }

    public static class SetModelV2 {
        public String set_id;
        public String title_ar;
        public String title_en;
        public String description_ar;
        public String description_en;
        public String download_url;
        public String audio_preview_url;
        public long timestamp;
        public int likes_count;
        public boolean is_active = true;
        
        public transient boolean isPlaying = false;
        public transient boolean isLoading = false;
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
            SetModelV2 set = setsList.get(position);
            FirebaseUser user = mAuth.getCurrentUser();
            
            String title = "en".equals(currentLang) && !set.title_en.isEmpty() ? set.title_en : set.title_ar;
            String desc = "en".equals(currentLang) && set.description_en != null && !set.description_en.isEmpty() ? set.description_en : set.description_ar;
            
            holder.tvSetTitle.setText(title);
            holder.tvSetDescription.setText(desc);
            holder.tvLikesCount.setText(String.valueOf(set.likes_count));

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
                    Toast.makeText(FavoritesActivity.this, tr("قم بتسجيل الدخول للإعجاب", "Please login to like"), Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (likedSetIds.contains(set.set_id)) {
                    likesRef.child(user.getUid()).child(set.set_id).removeValue();
                    likedSetIds.remove(set.set_id);
                    holder.btnLike.setColorFilter(Color.WHITE);
                    holder.tvLikesCount.setText(String.valueOf(set.likes_count - 1));
                    set.likes_count -= 1; 
                } else {
                    likesRef.child(user.getUid()).child(set.set_id).setValue(true);
                    likedSetIds.add(set.set_id);
                    holder.btnLike.setColorFilter(Color.RED);
                    holder.tvLikesCount.setText(String.valueOf(set.likes_count + 1));
                    set.likes_count += 1;
                }
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) adapter.notifyItemChanged(pos, "LIKE_FAV_PAYLOAD");
            });
            
            holder.btnFavorite.setOnClickListener(v -> {
                if (user == null) {
                    Toast.makeText(FavoritesActivity.this, tr("قم بتسجيل الدخول لحفظ السيت", "Please login to save"), Toast.LENGTH_SHORT).show();
                    return;
                }
                
                favRef.child(set.set_id).removeValue();
                favoriteSetIds.remove(set.set_id);
                Toast.makeText(FavoritesActivity.this, tr("تم إزالة السيت من المفضلة", "Set removed from favorites"), Toast.LENGTH_SHORT).show();
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
                SetModelV2 set = setsList.get(position);
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
            
            SmoothWaveView smoothWave; // العنصر الانسيابي الجديد بدلاً من الخطوط المتقطعة
            
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

                if(tvPreviewLabel != null) tvPreviewLabel.setText(tr("معاينة السيت", "Preview Set"));
                if(tvDownloadLabel != null) tvDownloadLabel.setText(tr("تحميل السيت", "Download Set"));
                if(tvPlayStatus != null) tvPlayStatus.setText(tr("تشغيل", "Play"));

                DeveloperAvatarLoader.load(imgDevIcon);

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

            public void drawPlaybackUI(SetModelV2 set) {
                if (set.isLoading) {
                    audioLoadingBar.setVisibility(View.VISIBLE);
                    btnPlayPause.setVisibility(View.GONE);
                    if (tvPlayStatus != null) tvPlayStatus.setText(tr("جاري التحميل...", "Loading..."));
                    stopVisualizer();
                } else {
                    audioLoadingBar.setVisibility(View.GONE);
                    btnPlayPause.setVisibility(View.VISIBLE);
                    if (set.isPlaying) {
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                        if (tvPlayStatus != null) tvPlayStatus.setText(tr("توقف", "Pause"));
                        startVisualizer();
                    } else {
                        btnPlayPause.setImageResource(R.drawable.ic_play_glow);
                        if (tvPlayStatus != null) tvPlayStatus.setText(tr("تشغيل", "Play"));
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
        List<SetModelV2> list;
        OnItemClickListener listener;
        public SimpleSetsAdapter(List<SetModelV2> list, OnItemClickListener listener) {
            this.list = list; this.listener = listener;
        }
        @NonNull @Override
        public SimpleVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_button, parent, false);
            return new SimpleVH(view);
        }
        @Override
        public void onBindViewHolder(@NonNull SimpleVH holder, int position) {
            SetModelV2 set = list.get(position);
            String text = "en".equals(currentLang) ? set.title_en : set.title_ar;
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