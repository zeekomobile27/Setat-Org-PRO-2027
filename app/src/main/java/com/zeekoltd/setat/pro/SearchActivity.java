package com.zeekoltd.setat.pro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

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
import java.util.Locale;
import java.util.Set;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;

public class SearchActivity extends AppCompatActivity {

    private String currentLang = "ar";
    private SharedPreferences langPrefs;
    private AdMobAdsHelper adMobAdsHelper;

    private DatabaseReference likesRef;
    private FirebaseAuth mAuth;
    public static final String DEV_UID = "GrWIoyYfgCVU3BJjZzfu2T0PYdo2";

    private ViewPager2 viewPagerSets;
    private SearchSetPagerAdapter adapter;
    private List<SetModelSearch> allSetsMaster = new ArrayList<>();
    private List<SetModelSearch> filteredSetsList = new ArrayList<>();
    
    private EditText etSearchInput;
    private ImageView btnClearSearch;
    private ProgressBar pbLoadingGlobal;
    private TextView tvNoResults;
    private ViewGroup appRootView;

    // أقسام قاعدة البيانات التي سيبحث فيها التطبيق
    private final String[] SEARCH_NODES = {
        "exclusive_sets_v2", "iraq_sets_v2", "dabakat", "sori", 
        "egept", "dzir", "setat_shabi", "yeme", "ehtrafe"
    };
    private int loadedNodesCount = 0;

    // ExoPlayer Manager
    private ExoPlayer exoPlayer;
    private int currentExoPosition = -1;
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;

    private Set<String> likedSetIds = new HashSet<>();
    private Set<String> favoriteSetIds = new HashSet<>();

    public String tr(String arabicText, String englishText) {
        return "en".equals(currentLang) ? englishText : arabicText;
    }

    private void applyLanguage() {
        langPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentLang = langPrefs.getString("app_language", "ar");
        Locale locale = new Locale(currentLang);
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        if ("en".equals(currentLang)) config.setLayoutDirection(Locale.ENGLISH);
        else config.setLayoutDirection(Locale.forLanguageTag("ar"));
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private void goBackToMain() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        startActivity(new Intent(SearchActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyLanguage();
        EdgeToEdgeHelper.enable(getWindow());
        setContentView(R.layout.activity_search);

        mAuth = FirebaseAuth.getInstance();
        likesRef = FirebaseDatabase.getInstance().getReference("liked_sets");
        appRootView = findViewById(R.id.mainContentView);

        initializeLogic();
        setupGlobalBlurViews();
        loadUserPreferences();
        fetchAllSetsForSearch();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adMobAdsHelper != null && adMobAdsHelper.isShowingAdDialog()) return;
                goBackToMain();
            }
        });
    }

    private void initializeLogic() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            ViewCompat.setOnApplyWindowInsetsListener(appRootView, (v, insets) -> {
                float d = getResources().getDisplayMetrics().density;
                View blurTopBarNew = findViewById(R.id.blurTopBarNew);
                if (blurTopBarNew != null) {
                    ViewGroup.MarginLayoutParams paramsTop = (ViewGroup.MarginLayoutParams) blurTopBarNew.getLayoutParams();
                    paramsTop.topMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top + (int)(15*d);
                    blurTopBarNew.setLayoutParams(paramsTop);
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
        adMobAdsHelper.initialize(() -> runOnUiThread(() -> adMobAdsHelper.loadInterstitialAdSilent()));

        viewPagerSets = findViewById(R.id.viewPagerSets);
        viewPagerSets.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        ViewGroup brightTarget = findViewById(R.id.brightBackgroundTarget);
        if (brightTarget == null) brightTarget = appRootView;

        adapter = new SearchSetPagerAdapter(brightTarget);
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
                }
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> goBackToMain());
        
        etSearchInput = findViewById(R.id.etSearchInput);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        pbLoadingGlobal = findViewById(R.id.pbLoadingGlobal);
        tvNoResults = findViewById(R.id.tvNoResults);
        
        etSearchInput.setHint(tr("ابحث عن إسم السيت أو الوصف...", "Search for set name or description..."));
        
        btnClearSearch.setOnClickListener(v -> {
            etSearchInput.setText("");
            filterSets("");
        });

        etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) btnClearSearch.setVisibility(View.VISIBLE);
                else btnClearSearch.setVisibility(View.GONE);
                filterSets(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupGlobalBlurViews() {
        ViewGroup brightTarget = findViewById(R.id.brightBackgroundTarget);
        if (brightTarget == null) brightTarget = appRootView;
        if (brightTarget == null) return;
        BlurView blurTopBar = findViewById(R.id.blurTopBarNew);
        if (blurTopBar != null) blurTopBar.setupWith(brightTarget, new RenderScriptBlur(this)).setBlurRadius(15f).setBlurEnabled(true);
    }

    private void loadUserPreferences() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        likesRef.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                likedSetIds.clear();
                for (DataSnapshot child : snapshot.getChildren()) likedSetIds.add(child.getKey());
                if (adapter != null) adapter.notifyItemRangeChanged(0, adapter.getItemCount(), "LIKE_FAV_PAYLOAD");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("favorite_sets")
           .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                favoriteSetIds.clear();
                for (DataSnapshot child : snapshot.getChildren()) favoriteSetIds.add(child.getKey());
                if (adapter != null) adapter.notifyItemRangeChanged(0, adapter.getItemCount(), "LIKE_FAV_PAYLOAD");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // جلب جميع السيتات من جميع الأقسام في الخلفية
    private void fetchAllSetsForSearch() {
        loadedNodesCount = 0;
        allSetsMaster.clear();
        pbLoadingGlobal.setVisibility(View.VISIBLE);
        
        for (String node : SEARCH_NODES) {
            FirebaseDatabase.getInstance().getReference(node).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        SetModelSearch set = child.getValue(SetModelSearch.class);
                        if (set != null && set.is_active) {
                            set.set_id = child.getKey();
                            set.node_name = node; // حفظ مسار القسم للتعديل عليه (كالإعجابات)
                            allSetsMaster.add(set);
                        }
                    }
                    loadedNodesCount++;
                    if (loadedNodesCount == SEARCH_NODES.length) {
                        pbLoadingGlobal.setVisibility(View.GONE);
                        Collections.sort(allSetsMaster, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                        filterSets(etSearchInput.getText().toString());
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    loadedNodesCount++;
                    if (loadedNodesCount == SEARCH_NODES.length) pbLoadingGlobal.setVisibility(View.GONE);
                }
            });
        }
    }

    private void filterSets(String query) {
        if (exoPlayer != null) { exoPlayer.stop(); currentExoPosition = -1; }
        
        filteredSetsList.clear();
        String q = query.toLowerCase().trim();
        
        if (q.isEmpty()) {
            filteredSetsList.addAll(allSetsMaster);
        } else {
            for (SetModelSearch set : allSetsMaster) {
                boolean matchAr = set.title_ar != null && set.title_ar.toLowerCase().contains(q);
                boolean matchEn = set.title_en != null && set.title_en.toLowerCase().contains(q);
                boolean matchDescAr = set.description_ar != null && set.description_ar.toLowerCase().contains(q);
                boolean matchDescEn = set.description_en != null && set.description_en.toLowerCase().contains(q);
                
                if (matchAr || matchEn || matchDescAr || matchDescEn) {
                    SetModelSearch clonedSet = new SetModelSearch(set);
                    filteredSetsList.add(clonedSet);
                }
            }
        }
        
        if (filteredSetsList.isEmpty()) {
            tvNoResults.setVisibility(View.VISIBLE);
            viewPagerSets.setVisibility(View.GONE);
        } else {
            tvNoResults.setVisibility(View.GONE);
            viewPagerSets.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    private void initExoPlayer() {
        if (exoPlayer == null) {
            HttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
            CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory()
                    .setCache(CacheManager.getInstance(this))
                    .setUpstreamDataSourceFactory(httpFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR); 
            exoPlayer = new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(cacheFactory)).build();
            exoPlayer.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (currentExoPosition == -1 || filteredSetsList.isEmpty() || currentExoPosition >= filteredSetsList.size()) return;
                    SetModelSearch currentSet = filteredSetsList.get(currentExoPosition);
                    if (state == Player.STATE_READY) {
                        if (currentSet.isLoading || !currentSet.isPlaying) {
                            currentSet.isLoading = false; currentSet.isPlaying = true;
                            adapter.notifyItemChanged(currentExoPosition, "PLAYBACK_STATE");
                        }
                    } else if (state == Player.STATE_ENDED) {
                        if (currentSet.isPlaying || currentSet.isLoading) {
                            currentSet.isPlaying = false; currentSet.isLoading = false;
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
        if (filteredSetsList.isEmpty() || position >= filteredSetsList.size()) return;
        SetModelSearch set = filteredSetsList.get(position);
        initExoPlayer();
        
        if (currentExoPosition != -1 && currentExoPosition != position) {
            SetModelSearch oldSet = filteredSetsList.get(currentExoPosition);
            if (oldSet.isPlaying || oldSet.isLoading) {
                oldSet.isPlaying = false; oldSet.isLoading = false;
                adapter.notifyItemChanged(currentExoPosition, "PLAYBACK_STATE");
            }
        }
        currentExoPosition = position;
        exoPlayer.stop();

        if (set.audio_preview_url == null || set.audio_preview_url.isEmpty()) {
            if (set.isLoading || set.isPlaying) {
                set.isLoading = false; set.isPlaying = false;
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
            SetModelSearch set = filteredSetsList.get(position);
            set.isPlaying = false;
            adapter.notifyItemChanged(position, "PLAYBACK_STATE");
        } else playAudioForPosition(position);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
        if (debounceHandler != null && debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.pause();
            if (currentExoPosition >= 0 && currentExoPosition < filteredSetsList.size()) {
                filteredSetsList.get(currentExoPosition).isPlaying = false;
                adapter.notifyItemChanged(currentExoPosition, "PLAYBACK_STATE");
            }
        }
    }

    // نموذج بيانات مصمم خصيصاً للبحث ويحتوي على node_name
    public static class SetModelSearch {
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
        public transient String node_name; 

        public SetModelSearch() {}
        public SetModelSearch(SetModelSearch other) {
            this.set_id = other.set_id; this.title_ar = other.title_ar; this.title_en = other.title_en;
            this.description_ar = other.description_ar; this.description_en = other.description_en;
            this.download_url = other.download_url; this.audio_preview_url = other.audio_preview_url;
            this.timestamp = other.timestamp; this.likes_count = other.likes_count;
            this.is_active = other.is_active; this.node_name = other.node_name;
            this.isPlaying = false; this.isLoading = false;
        }
    }

    private class SearchSetPagerAdapter extends RecyclerView.Adapter<SearchSetPagerAdapter.SetViewHolder> {
        private ViewGroup rootBlurTarget;
        public SearchSetPagerAdapter(ViewGroup root) { this.rootBlurTarget = root; }

        @NonNull @Override
        public SetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_set_new, parent, false);
            return new SetViewHolder(view, rootBlurTarget);
        }

        @Override
        public void onBindViewHolder(@NonNull SetViewHolder holder, int position) {
            SetModelSearch set = filteredSetsList.get(position);
            FirebaseUser user = mAuth.getCurrentUser();

            String title = "en".equals(currentLang) && set.title_en != null && !set.title_en.isEmpty() ? set.title_en : set.title_ar;
            String desc = "en".equals(currentLang) && set.description_en != null && !set.description_en.isEmpty() ? set.description_en : set.description_ar;

            holder.tvSetTitle.setText(title);
            holder.tvSetDescription.setText(desc);
            holder.tvLikesCount.setText(String.valueOf(set.likes_count));

            holder.drawPlaybackUI(set);

            if (user != null) {
                holder.btnLike.setColorFilter(likedSetIds.contains(set.set_id) ? Color.RED : Color.WHITE);
                holder.btnFavorite.setColorFilter(favoriteSetIds.contains(set.set_id) ? Color.parseColor("#FFD700") : Color.WHITE);
            }

            holder.btnLike.setOnClickListener(v -> {
                if (user == null) {
                    Toast.makeText(SearchActivity.this, tr("قم بتسجيل الدخول للإعجاب", "Please login to like"), Toast.LENGTH_SHORT).show();
                    return;
                }
                DatabaseReference targetedSetRef = FirebaseDatabase.getInstance().getReference(set.node_name);

                if (likedSetIds.contains(set.set_id)) {
                    likesRef.child(user.getUid()).child(set.set_id).removeValue();
                    likedSetIds.remove(set.set_id);
                    targetedSetRef.child(set.set_id).child("likes_count").setValue(set.likes_count - 1);
                    holder.btnLike.setColorFilter(Color.WHITE);
                    holder.tvLikesCount.setText(String.valueOf(set.likes_count - 1));
                    set.likes_count -= 1;
                } else {
                    likesRef.child(user.getUid()).child(set.set_id).setValue(true);
                    likedSetIds.add(set.set_id);
                    targetedSetRef.child(set.set_id).child("likes_count").setValue(set.likes_count + 1);
                    holder.btnLike.setColorFilter(Color.RED);
                    holder.tvLikesCount.setText(String.valueOf(set.likes_count + 1));
                    set.likes_count += 1;
                }
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) adapter.notifyItemChanged(pos, "LIKE_FAV_PAYLOAD");
            });

            holder.btnFavorite.setOnClickListener(v -> {
                if (user == null) {
                    Toast.makeText(SearchActivity.this, tr("قم بتسجيل الدخول لحفظ السيت", "Please login to save"), Toast.LENGTH_SHORT).show();
                    return;
                }
                DatabaseReference favRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("favorite_sets").child(set.set_id);
                if (favoriteSetIds.contains(set.set_id)) {
                    favRef.removeValue();
                    favoriteSetIds.remove(set.set_id);
                    holder.btnFavorite.setColorFilter(Color.WHITE);
                    Toast.makeText(SearchActivity.this, tr("تم الإزالة من المفضلة", "Removed from favorites"), Toast.LENGTH_SHORT).show();
                } else {
                    favRef.setValue(set);
                    favoriteSetIds.add(set.set_id);
                    holder.btnFavorite.setColorFilter(Color.parseColor("#FFD700"));
                    Toast.makeText(SearchActivity.this, tr("تم الحفظ بنجاح", "Saved successfully"), Toast.LENGTH_SHORT).show();
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

        @Override public int getItemCount() { return filteredSetsList.size(); }

        @Override
        public void onBindViewHolder(@NonNull SetViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (payloads.isEmpty()) super.onBindViewHolder(holder, position, payloads);
            else {
                SetModelSearch set = filteredSetsList.get(position);
                FirebaseUser user = mAuth.getCurrentUser();
                for (Object payload : payloads) {
                    if ("PLAYBACK_STATE".equals(payload)) holder.drawPlaybackUI(set);
                    else if ("LIKE_FAV_PAYLOAD".equals(payload)) {
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
            TextView tvSetTitle, tvSetDescription, tvLikesCount, tvPlayStatus;
            ImageView btnLike, btnFavorite, btnPlayPause, imgDevIcon;
            View btnDownloadSet;
            ProgressBar audioLoadingBar;
            SmoothWaveView smoothWave;
            MovingBorderView centerMovingBorder;

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
                tvPlayStatus = itemView.findViewById(R.id.tvPlayStatus);
                imgDevIcon = itemView.findViewById(R.id.imgDevIcon);
                smoothWave = itemView.findViewById(R.id.smoothWave);

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

                BlurView blurCenter = itemView.findViewById(R.id.blurCenterWindow);
                BlurView blurDev = itemView.findViewById(R.id.blurDevIcon);
                BlurView blurLike = itemView.findViewById(R.id.blurLikeBtn);
                BlurView blurFav = itemView.findViewById(R.id.blurFavBtn);
                BlurView blurBottom = itemView.findViewById(R.id.blurBottomInfo);
                BlurView blurDown = itemView.findViewById(R.id.blurDownloadBtn);

                if (root != null) {
                    if (blurCenter != null) blurCenter.setupWith(root, new RenderScriptBlur(itemView.getContext())).setBlurRadius(15f).setBlurEnabled(true);
                    if (blurDev != null) blurDev.setupWith(root, new RenderScriptBlur(itemView.getContext())).setBlurRadius(15f).setBlurEnabled(true);
                    if (blurLike != null) blurLike.setupWith(root, new RenderScriptBlur(itemView.getContext())).setBlurRadius(15f).setBlurEnabled(true);
                    if (blurFav != null) blurFav.setupWith(root, new RenderScriptBlur(itemView.getContext())).setBlurRadius(15f).setBlurEnabled(true);
                    if (blurBottom != null) blurBottom.setupWith(root, new RenderScriptBlur(itemView.getContext())).setBlurRadius(15f).setBlurEnabled(true);
                    if (blurDown != null) blurDown.setupWith(root, new RenderScriptBlur(itemView.getContext())).setBlurRadius(10f).setBlurEnabled(true);
                }

                if (centerMovingBorder != null) {
                    centerMovingBorder.setCornerRadius(14f);
                    centerMovingBorder.setGlowColor("#00FFFF");
                    centerMovingBorder.setContinuousMode(true);
                }
            }

            public void drawPlaybackUI(SetModelSearch set) {
                if (set.isLoading) {
                    audioLoadingBar.setVisibility(View.VISIBLE);
                    btnPlayPause.setVisibility(View.GONE);
                    tvPlayStatus.setText(tr("جاري التحميل...", "Loading..."));
                    if (smoothWave != null) smoothWave.stopAnimation();
                } else {
                    audioLoadingBar.setVisibility(View.GONE);
                    btnPlayPause.setVisibility(View.VISIBLE);
                    if (set.isPlaying) {
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                        tvPlayStatus.setText(tr("توقف", "Pause"));
                        if (smoothWave != null) smoothWave.startAnimation();
                    } else {
                        btnPlayPause.setImageResource(R.drawable.ic_play_glow);
                        tvPlayStatus.setText(tr("تشغيل", "Play"));
                        if (smoothWave != null) smoothWave.stopAnimation();
                    }
                }
            }
        }
    }
}