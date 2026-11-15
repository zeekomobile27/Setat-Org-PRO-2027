package com.zeekoltd.setat.pro;

import androidx.core.view.WindowInsetsCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;

public class OrgPianoActivity extends AppCompatActivity {

    // Track the piano screen and its asynchronous sample loads together. The native
    // engine must stay alive until every worker has finished calling JNI.
    private static final Object AUDIO_LIFECYCLE_LOCK = new Object();
    private static int activePianoActivities = 0;
    private static int pendingAudioLoadBatches = 0;

    public String currentLang = "ar";
    
    public NativeAudioEngine audioEngine;
    public boolean engineReady = false;
    
    // 🚀 تهيئة الاعلانات
    private YandexAdsHelper yandexAdsHelper;

    // 🚀 المتغيرات النصية لتخزين العناوين (Caching) للحفاظ عليها بعد مسح الملفات وتسريع التنقل
    private String cachedSound1Name = "string";
    private String cachedSound2Name = "sound2";
    private String cachedRhythm1Name = "rhythm";
    private String cachedRhythm2Name = "rhythm2";

    public float[] notePitchRates = new float[12]; 
    public boolean[] detunedVisuals = new boolean[12]; 
    public final String[] notesNames = {"Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si"};
    
    public PianoView pianoView;
    private boolean isTarteelActive = false;
    private TextView btnTarteel;
    private LinkedList<Integer> monoNoteStack = new LinkedList<>();
    
    public TextView tvDashRhythmName, tvDashTempo, tvDashTrans;
    public TextView tvDashSound1, tvDashOctave1, tvDashVol1;
    
    private TextView btnRhythmPlayStop; 
    private TextView btnRhythmTempo;    
    private TextView btnRhythmPad1;     
    private TextView btnRhythmPad2;     
    private TextView btnRhythmIntro;    
    
    private TextView btnImportSet;
    private TextView btnCloudSets;
    
    private boolean isRhythmPlaying = false;
    private int activeRhythmIndex = 0; 
    private int pendingRhythmIndex = -1; 
    
    public float currentTempo = 1.0f; 
    public float currentKeysVolume = 1.0f; 
    public float currentRhythmVolume = 0.85f; 

    public int currentSoundSelection = 1; 
    public int currentRhythmSelection = 1;

    public int currentTranspose = 0;
    public int currentOctave = 0;
    private int[] activeMappedNotes = new int[256];
    private float[] activeMappedRates = new float[256];

    public static final String SETS_FOLDER_NAME = "ZEEKOORG/Setat";
    public String setsFolderPath;
    private boolean activeSetIsDefault = true;
    private String activeExternalSoundPrefix;
    private String activeExternalRhythmPrefix;

    private int devClickCount = 0;
    private Handler devClickHandler = new Handler(Looper.getMainLooper());
    private Runnable devClickRunnable;

    // 🚀 متغيرات تأثير الموجات الصوتية
    private View eqCyan1, eqGold1, eqCyan2, eqGold2, eqCyan3, eqGold3;
    private Handler eqHandler = new Handler(Looper.getMainLooper());
    private Runnable eqRunnable;

    private final Handler audioInitHandler = new Handler(Looper.getMainLooper());
    private Runnable audioInitRunnable;

    private Handler uiSyncHandler = new Handler(Looper.getMainLooper());
    private Runnable uiSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRhythmPlaying && audioEngine != null) {
                int cIdx = audioEngine.getActiveRhythmIndex();
                if (cIdx != activeRhythmIndex) {
                    activeRhythmIndex = cIdx;
                    pendingRhythmIndex = -1; 
                    updateRhythmVisuals(); 
                }
                uiSyncHandler.postDelayed(this, 100);
            }
        }
    };

    // 🚀 حماية الخط من التكبير عبر إعدادات النظام للحفاظ على التصميم
    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration config = newBase.getResources().getConfiguration();
        if (config.fontScale != 1.0f) {
            config.fontScale = 1.0f;
            super.attachBaseContext(newBase.createConfigurationContext(config));
        } else {
            super.attachBaseContext(newBase);
        }
    }

    // 🚀 دالة الخروج (تمت إزالة الإعلان منها لتجنب حظر جوجل)
    private void goBackToMain() {
        if (audioEngine != null) {
            for (int i = 0; i < 24; i++) audioEngine.releasePoly(i);
            audioEngine.releaseMono();
            audioEngine.stopRhythm();
        }
        // MainActivity is already below this screen in the task stack.
        finish();
    }

    private static void registerPianoActivity() {
        synchronized (AUDIO_LIFECYCLE_LOCK) {
            activePianoActivities++;
        }
    }

    private static void finishAudioLoad(NativeAudioEngine engine) {
        synchronized (AUDIO_LIFECYCLE_LOCK) {
            if (pendingAudioLoadBatches > 0) pendingAudioLoadBatches--;
            destroyAudioEngineIfIdle(engine);
        }
    }

    private static void destroyAudioEngineIfIdle(NativeAudioEngine engine) {
        if (activePianoActivities == 0 && pendingAudioLoadBatches == 0 && engine != null) {
            engine.destroy();
        }
    }

    private void loadAudioSetTracked(NativeAudioEngine engine, String s1, String s2,
                                    String r1, String r2, boolean preferPackaged,
                                    Runnable onLoaded) {
        synchronized (AUDIO_LIFECYCLE_LOCK) {
            pendingAudioLoadBatches++;
        }
        engine.loadFullSetAsync(this, s1, s2, r1, r2, preferPackaged, () -> {
            finishAudioLoad(engine);
            if (onLoaded != null) onLoaded.run();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPianoActivity();
        applyLanguage();
        applyPianoOrientationForCurrentWindow();
        EdgeToEdgeHelper.enable(getWindow());
        
        setContentView(R.layout.activity_org_piano);

        for (int i = 0; i < 12; i++) notePitchRates[i] = 1.0f;
        Arrays.fill(activeMappedNotes, -1);
        Arrays.fill(activeMappedRates, 1.0f);

        // 🚀 إنشاء مجلد السيتات الآمن (لا يحتاج صلاحيات اختراق من المستخدم)
        createGoldenFolder();

        // 🚀 تهيئة نظام الإعلانات وتحميل البانر فقط
        yandexAdsHelper = YandexAdsHelper.getInstance(this);
        yandexAdsHelper.setCurrentLanguage(currentLang);
        yandexAdsHelper.initialize(() -> {
            runOnUiThread(() -> {
                FrameLayout bannerContainer = findViewById(R.id.adBannerContainer);
                if (bannerContainer != null && bannerContainer.getChildCount() > 0) {
                    ImageView placeholder = (ImageView) bannerContainer.getChildAt(0);
                    yandexAdsHelper.loadBannerAd(bannerContainer, placeholder);
                }
            });
        });

        // 🚀 ربط زر هاتف الرجوع
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (yandexAdsHelper != null && yandexAdsHelper.isShowingAdDialog()) return;
                goBackToMain();
            }
        });

        // Preserve the active extracted set files so user-selected sets can be restored.
        // The built-in default remains the fallback when no external info/audio files exist.
        activeSetIsDefault = !hasExternalActiveSetFiles();
        if (activeSetIsDefault) {
            activeExternalSoundPrefix = null;
            activeExternalRhythmPrefix = null;
        } else {
            activeExternalSoundPrefix = detectSoundPrefix();
            activeExternalRhythmPrefix = detectRhythmPrefix();
        }

        initUI();
        initNativeAudioEngine(); 
        setupPianoView();
        hideSystemUI(); 
    }


    /**
     * Preserve the original landscape piano experience on handsets while allowing
     * tablets and large foldable displays to use the orientation chosen by the user.
     */
    private void applyPianoOrientationForCurrentWindow() {
        int smallestWidthDp = getResources().getConfiguration().smallestScreenWidthDp;
        int requested = smallestWidthDp >= 600
                ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if (getRequestedOrientation() != requested) {
            setRequestedOrientation(requested);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyPianoOrientationForCurrentWindow();
        View content = findViewById(android.R.id.content);
        if (content != null) content.requestLayout();
        if (pianoView != null) pianoView.requestLayout();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (pianoView != null && engineReady) {
            pianoView.setTouchEnabled(true);
        }
    }

    // 🚀 التنظيف والموت السريري للتطبيق عند الإغلاق التام
    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiSyncHandler.removeCallbacksAndMessages(null);
        eqHandler.removeCallbacksAndMessages(null);
        audioInitHandler.removeCallbacksAndMessages(null);
        if (pianoView != null) {
            pianoView.clearAllTouches();
        }
        synchronized (AUDIO_LIFECYCLE_LOCK) {
            if (activePianoActivities > 0) activePianoActivities--;
            destroyAudioEngineIfIdle(audioEngine);
            audioEngine = null;
        }
        if (yandexAdsHelper != null) {
            yandexAdsHelper.destroyBanner();
        }
        engineReady = false;
    }

    // 🚀 دالة المكنسة الذكية: تمسح الأصوات (ogg, json) وتتجاهل السيتات (zip, set) 
    private boolean hasExternalActiveSetFiles() {
        if (setsFolderPath == null) return false;
        File dir = new File(setsFolderPath);
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg")) return true;
        }
        return false;
    }

    public void showLoading(String text) {
        runOnUiThread(() -> {
            FrameLayout overlay = findViewById(R.id.loadingOverlay);
            View dashContent = findViewById(R.id.dashContent);
            TextView tvCenterText = findViewById(R.id.tvCenterLoadingText);
            
            if (overlay != null) overlay.setVisibility(View.VISIBLE);
            if (dashContent != null) dashContent.setVisibility(View.GONE);
            if (tvCenterText != null) {
                tvCenterText.setVisibility(View.VISIBLE);
                tvCenterText.setText(text != null && !text.isEmpty() ? text : tr("يرجى الانتظار...", "Please Wait...")); 
            }
        });
    }

    public void hideLoading() {
        runOnUiThread(() -> {
            FrameLayout overlay = findViewById(R.id.loadingOverlay);
            View dashContent = findViewById(R.id.dashContent);
            TextView tvCenterText = findViewById(R.id.tvCenterLoadingText);
            
            if (overlay != null) overlay.setVisibility(View.GONE);
            if (dashContent != null) dashContent.setVisibility(View.VISIBLE);
            if (tvCenterText != null) tvCenterText.setVisibility(View.GONE);
        });
    }

    private void createGoldenFolder() {
        // 🚀 التخزين الآمن المتوافق مع جوجل بلاي (لا يحتاج أي صلاحيات على أندرويد 11+)
        File goldenFolder = new File(getExternalFilesDir(null), SETS_FOLDER_NAME);
        if (!goldenFolder.exists()) {
            goldenFolder.mkdirs();
        }
        setsFolderPath = goldenFolder.getAbsolutePath();
    }

    private String detectSoundPrefixHelper(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    String res = detectSoundPrefixHelper(f);
                    if (res != null) return res;
                } else {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith("_01.ogg")) {
                        return f.getName().substring(0, name.lastIndexOf("_01.ogg"));
                    }
                }
            }
        }
        return null;
    }

    public String detectSoundPrefix() {
        if (setsFolderPath == null) return "string";
        String prefix = detectSoundPrefixHelper(new File(setsFolderPath));
        return prefix != null ? prefix : "string";
    }

    private String detectRhythmPrefixHelper(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    String res = detectRhythmPrefixHelper(f);
                    if (res != null) return res;
                } else {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith("_a.ogg")) {
                        return f.getName().substring(0, name.lastIndexOf("_a.ogg"));
                    }
                }
            }
        }
        return null;
    }

    public String detectRhythmPrefix() {
        if (setsFolderPath == null) return "rhythm";
        String prefix = detectRhythmPrefixHelper(new File(setsFolderPath));
        return prefix != null ? prefix : "rhythm";
    }

    public String getDisplayNameFromJson(String key, String defaultName) {
        if (activeSetIsDefault) {
            try {
                java.io.InputStream is = getAssets().open("info.json");
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                org.json.JSONObject json = new org.json.JSONObject(new String(buffer, "UTF-8"));
                if (json.has(key)) return json.getString(key);
            } catch (Exception e) {
                android.util.Log.e("ZeekoAudio", "خطأ في قراءة info.json من assets", e);
            }
        } else {
            File infoFile = new File(setsFolderPath, "info.json");
            if (infoFile.exists()) {
                try {
                    java.io.FileInputStream fis = new java.io.FileInputStream(infoFile);
                    byte[] buffer = new byte[fis.available()];
                    fis.read(buffer);
                    fis.close();
                    org.json.JSONObject json = new org.json.JSONObject(new String(buffer, "UTF-8"));
                    if (json.has(key)) return json.getString(key);
                } catch (Exception e) {
                    android.util.Log.e("ZeekoAudio", "خطأ في قراءة info.json الخارجي", e);
                }
            }
        }
        return defaultName;
    }

    // 🚀 دالة جديدة لتحديث الذاكرة المؤقتة للأسماء وقراءتها مرة واحدة فقط عند رفع السيت
    public void updateCachedNames() {
        String soundFallback1 = activeSetIsDefault ? "string" : externalSoundFallback();
        String soundFallback2 = activeSetIsDefault ? "saz" : externalSoundFallback();
        String rhythmFallback1 = activeSetIsDefault ? "dj27" : externalRhythmFallback();
        String rhythmFallback2 = activeSetIsDefault ? "salo" : externalRhythmFallback();
        cachedSound1Name = getDisplayNameFromJson("sound1", soundFallback1);
        cachedSound2Name = getDisplayNameFromJson("sound2", soundFallback2);
        cachedRhythm1Name = getDisplayNameFromJson("rhythm1", rhythmFallback1);
        cachedRhythm2Name = getDisplayNameFromJson("rhythm2", rhythmFallback2);
    }

    private String externalSoundFallback() {
        return activeExternalSoundPrefix != null && !activeExternalSoundPrefix.isEmpty()
                ? activeExternalSoundPrefix : "string";
    }

    private String externalRhythmFallback() {
        return activeExternalRhythmPrefix != null && !activeExternalRhythmPrefix.isEmpty()
                ? activeExternalRhythmPrefix : "rhythm";
    }

    // 🚀 تم التعديل: تقرأ الأسماء من الذاكرة المؤقتة بدلاً من ملف info.json لضمان السرعة والاحتفاظ بها بعد التنظيف
    public void updateDashboardNames() {
        TextView tvS1 = findViewById(R.id.tvDashSound1);
        TextView tvS2 = findViewById(R.id.tvDashSound2);
        TextView tvR1 = findViewById(R.id.tvDashRhythm1);
        TextView tvR2 = findViewById(R.id.tvDashRhythm2);
        
        View rowS1 = findViewById(R.id.rowSound1);
        View rowS2 = findViewById(R.id.rowSound2);
        View rowR1 = findViewById(R.id.rowRhythm1);
        View rowR2 = findViewById(R.id.rowRhythm2);

        if (tvS1 != null) {
            tvS1.setText(cachedSound1Name);
            tvS1.setTextColor(currentSoundSelection == 1 ? Color.parseColor("#C5A059") : Color.parseColor("#FFFFFF"));
        }
        if (tvS2 != null) {
            tvS2.setText(cachedSound2Name);
            tvS2.setTextColor(currentSoundSelection == 2 ? Color.parseColor("#C5A059") : Color.parseColor("#FFFFFF"));
        }
        if (tvR1 != null) {
            tvR1.setText(cachedRhythm1Name);
            tvR1.setTextColor(currentRhythmSelection == 1 ? Color.parseColor("#C5A059") : Color.parseColor("#FFFFFF"));
        }
        if (tvR2 != null) {
            tvR2.setText(cachedRhythm2Name);
            tvR2.setTextColor(currentRhythmSelection == 2 ? Color.parseColor("#C5A059") : Color.parseColor("#FFFFFF"));
        }

        if (rowS1 != null) rowS1.setAlpha(currentSoundSelection == 1 ? 1.0f : 0.65f);
        if (rowS2 != null) rowS2.setAlpha(currentSoundSelection == 2 ? 1.0f : 0.65f);
        if (rowR1 != null) rowR1.setAlpha(currentRhythmSelection == 1 ? 1.0f : 0.65f);
        if (rowR2 != null) rowR2.setAlpha(currentRhythmSelection == 2 ? 1.0f : 0.65f);
    }

    public void updateDashboardTrans() {
        TextView tvDashTrans = findViewById(R.id.tvDashTrans);
        TextView tvDashOctave1 = findViewById(R.id.tvDashOctave1);
        if (tvDashTrans != null) {
            String transSign = currentTranspose > 0 ? "+" : "";
            tvDashTrans.setText("b/# " + transSign + currentTranspose);
        }
        if (tvDashOctave1 != null) {
            String octSign = currentOctave > 0 ? "+" : "";
            tvDashOctave1.setText(tr("أوكتاف ", "octave ") + octSign + currentOctave);
        }
        
        TextView btnTranspose = findViewById(R.id.btnTranspose);
        if (btnTranspose != null) updateLedButtonVisual(btnTranspose, currentTranspose != 0, "#C5A059", 15);
        
        TextView btnOctave = findViewById(R.id.btnOctave);
        if (btnOctave != null) updateLedButtonVisual(btnOctave, currentOctave != 0, "#C5A059", 15);
    }
    
    public void updateDashboardTempo() {
        TextView tvDashTempo = findViewById(R.id.tvDashTempo);
        if (tvDashTempo != null) {
            tvDashTempo.setText(Math.round(currentTempo * 100) + "%");
        }
        TextView btnTempo = findViewById(R.id.ph_l_9);
        if (btnTempo != null) {
            updateLedButtonVisual(btnTempo, currentTempo != 1.0f, "#C5A059", 15);
        }
    }
    
    public void updateDashboardVolume() {}

    private String resolveExternalSamplePrefix(String metadataKey, String suffix, int bankIndex, String fallback) {
        // Some sets store sample prefixes in info.json; others store display labels.
        // Use metadata as a filename prefix only when the matching file exists.
        String metadataValue = getDisplayNameFromJson(metadataKey, "");
        if (metadataValue != null && !metadataValue.trim().isEmpty()
                && hasExternalSamplePrefix(metadataValue.trim(), suffix)) {
            return metadataValue.trim();
        }

        java.util.ArrayList<String> prefixes = findExternalSamplePrefixes(suffix);
        if (bankIndex >= 0 && bankIndex < prefixes.size()) {
            return prefixes.get(bankIndex);
        }
        if (!prefixes.isEmpty()) {
            return prefixes.get(0);
        }
        return fallback;
    }

    private boolean hasExternalSamplePrefix(String prefix, String suffix) {
        if (setsFolderPath == null || prefix == null || prefix.isEmpty()) return false;
        File file = new File(setsFolderPath, prefix + suffix);
        return file.isFile() && file.canRead();
    }

    private java.util.ArrayList<String> findExternalSamplePrefixes(String suffix) {
        java.util.ArrayList<String> prefixes = new java.util.ArrayList<>();
        if (setsFolderPath == null) return prefixes;
        File[] files = new File(setsFolderPath).listFiles();
        if (files == null) return prefixes;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            if (name.toLowerCase(java.util.Locale.ROOT).endsWith(suffix.toLowerCase(java.util.Locale.ROOT))) {
                String prefix = name.substring(0, name.length() - suffix.length());
                if (!prefixes.contains(prefix)) prefixes.add(prefix);
            }
        }
        return prefixes;
    }

    private void loadSetIntoMemory() {
        // The built-in samples have fixed resource prefixes. The info.json values are
        // display labels and must never decide which packaged audio resources are loaded.
        String s1 = activeSetIsDefault ? "string" : resolveExternalSamplePrefix("sound1", "_01.ogg", 0, externalSoundFallback());
        String s2 = activeSetIsDefault ? "saz" : resolveExternalSamplePrefix("sound2", "_01.ogg", 1, externalSoundFallback());
        String r1 = activeSetIsDefault ? "dj27" : resolveExternalSamplePrefix("rhythm1", "_a.ogg", 0, externalRhythmFallback());
        String r2 = activeSetIsDefault ? "salo" : resolveExternalSamplePrefix("rhythm2", "_a.ogg", 1, externalRhythmFallback());
        
        showLoading(tr("جاري قراءة الأصوات وتركيب السيت...", "Reading sounds and installing set..."));
        NativeAudioEngine loadingEngine = audioEngine;
        loadAudioSetTracked(loadingEngine, s1, s2, r1, r2, activeSetIsDefault, () -> {
            if (isFinishing() || isDestroyed() || audioEngine != loadingEngine) return;
            if (!loadingEngine.isAudioReady()) {
                engineReady = false;
                if (pianoView != null) pianoView.setTouchEnabled(false);
                hideLoading();
                android.util.Log.e("ZeekoAudio", "Audio initialization/loading failed; check ZeekoAudio logs for missing or undecodable samples.");
                Toast.makeText(this, tr("تعذر تحميل الأصوات. أعد فتح شاشة العزف أو تحقق من ملفات السيت.", "Audio samples could not be loaded. Reopen the piano screen or check the set files."), Toast.LENGTH_LONG).show();
                return;
            }
            engineReady = true;
            if (pianoView != null) pianoView.setTouchEnabled(true); 
            hideLoading();
            
            // 🌟 1. تحديث الذاكرة المؤقتة للأسماء وقراءتها من info.json قبل المسح 🌟
            updateCachedNames(); 
            // 🌟 2. تحديث الشاشة من الذاكرة المؤقتة 🌟
            updateDashboardNames(); 
            Toast.makeText(this, tr("تم تفعيل السيت بنجاح!", "Set activated successfully!"), Toast.LENGTH_SHORT).show();
        });
    }

    public void handleSelectedSetZipFromFile(File zipFile) {
        showLoading(tr("جاري استخراج السيت...\n", "Extracting Set...\n") + zipFile.getName());
        engineReady = false;

        SetInstaller.installFromZip(zipFile, setsFolderPath, currentLang, new SetInstaller.InstallListener() {
            @Override
            public void onProgress(String message) {
                if (!isFinishing() && !isDestroyed()) showLoading(message);
            }

            @Override
            public void onSuccess(String soundPrefix, String rhythmPrefix) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    activeSetIsDefault = false;
                    activeExternalSoundPrefix = soundPrefix;
                    activeExternalRhythmPrefix = rhythmPrefix;
                    loadSetIntoMemory();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    hideLoading();
                    engineReady = true;
                    Toast.makeText(OrgPianoActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    // 🚀 دالة جديدة وحصرية لمعالجة الملف المختار من مدير نظام الأندرويد دون كسر نظامك القديم
    private void importSetFromUriSafely(Uri uri) {
        showLoading(tr("جاري تجهيز الملف للتركيب...", "Preparing file for installation..."));
        engineReady = false;
        new Thread(() -> {
            try {
                java.io.InputStream in = getContentResolver().openInputStream(uri);
                File tempFile = new File(getCacheDir(), "temp_imported_set.zip");
                java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.flush();
                out.close();
                
                // بعد نسخ الملف بأمان، نرسله إلى نظام التركيب القديم الخاص بك دون أي تعديل عليه
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    handleSelectedSetZipFromFile(tempFile);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    hideLoading();
                    engineReady = true;
                    Toast.makeText(OrgPianoActivity.this, tr("حدث خطأ أثناء قراءة الملف!", "Error reading file!"), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // 🚀 التقاط الملف الراجع من مدير النظام
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 3000 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importSetFromUriSafely(data.getData());
        }
    }

    // 🚀 دالة التحميل المباشر والمجاني بدون إعلانات مكافأة 
    public void downloadAndInstallSet(String fileUrl, String setName) {
        showLoading(tr("جاري تحميل السيت...\n", "Downloading Set...\n") + setName);
        engineReady = false;

        SetInstaller.downloadAndInstall(fileUrl, setName, setsFolderPath, currentLang, new SetInstaller.InstallListener() {
            @Override
            public void onProgress(String message) {
                if (!isFinishing() && !isDestroyed()) showLoading(message);
            }

            @Override
            public void onSuccess(String soundPrefix, String rhythmPrefix) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    activeSetIsDefault = false;
                    activeExternalSoundPrefix = soundPrefix;
                    activeExternalRhythmPrefix = rhythmPrefix;
                    loadSetIntoMemory();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    hideLoading();
                    engineReady = true;
                    Toast.makeText(OrgPianoActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    public void requestRewardedSetDownload(String fileUrl, String setName) {
        if (yandexAdsHelper == null) {
            yandexAdsHelper = YandexAdsHelper.getInstance(this);
            yandexAdsHelper.setCurrentLanguage(currentLang);
        }
        yandexAdsHelper.showAdDialog(fileUrl, rewardedUrl -> downloadAndInstallSet(rewardedUrl, setName));
    }

    private void loadDefaultSet() {
        showLoading(tr("جاري استعادة الافتراضي...", "Restoring Default..."));
        engineReady = false;

        SetInstaller.restoreDefault(setsFolderPath, currentLang, new SetInstaller.InstallListener() {
            @Override
            public void onProgress(String message) {}

            @Override
            public void onSuccess(String soundPrefix, String rhythmPrefix) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    activeSetIsDefault = true;
                    activeExternalSoundPrefix = null;
                    activeExternalRhythmPrefix = null;
                    loadSetIntoMemory();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    engineReady = true;
                    hideLoading();
                    Toast.makeText(OrgPianoActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void resetTuning() {
        for (int i = 0; i < 12; i++) {
            notePitchRates[i] = 1.0f;
            detunedVisuals[i] = false;
        }
        if (pianoView != null) {
            pianoView.setDetunedNotes(detunedVisuals);
        }
        LinearLayout row = findViewById(R.id.tashreeqNotesRow);
        if (row != null) {
            for (int i = 0; i < 12; i++) {
                View v = row.getChildAt(i);
                if (v instanceof TextView) {
                    updateNoteBtnVisual((TextView) v, false);
                }
            }
        }
        Toast.makeText(this, tr("تم تصفير التشريق", "Tuning Reset"), Toast.LENGTH_SHORT).show();
    }

    // 🚀 تطبيق الترجمة الديناميكية لنصوص XML الثابتة
    private void applyTranslationsToUI() {
        TextView tvTashreeqLabel = findViewById(R.id.tvTashreeqLabel);
        if (tvTashreeqLabel != null) tvTashreeqLabel.setText(tr("التشريق", "Tune"));

        TextView tvLabelRhythms = findViewById(R.id.tvLabelRhythms);
        if (tvLabelRhythms != null) tvLabelRhythms.setText(tr("الإيقاعات", "Rhythms"));

        TextView tvLabelSounds = findViewById(R.id.tvLabelSounds);
        if (tvLabelSounds != null) tvLabelSounds.setText(tr("الأصوات", "Sounds"));

        TextView tvLabelRhythmCtrl = findViewById(R.id.tvLabelRhythmCtrl);
        if (tvLabelRhythmCtrl != null) tvLabelRhythmCtrl.setText(tr("تحكم الإيقاع", "Rhythm Control"));

        TextView tvLabelFX1 = findViewById(R.id.tvLabelFX1);
        if (tvLabelFX1 != null) tvLabelFX1.setText(tr(" ", " "));

        TextView tvLabelFX2 = findViewById(R.id.tvLabelFX2);
        if (tvLabelFX2 != null) tvLabelFX2.setText(tr("المؤثرات", "Effects"));

        TextView tvJoystickTitle = findViewById(R.id.tvJoystickTitle);
        if (tvJoystickTitle != null) tvJoystickTitle.setText(tr("اورج الملكي2027", "ORG Royal 27"));

        TextView tvCenterLoadingText = findViewById(R.id.tvCenterLoadingText);
        if (tvCenterLoadingText != null) tvCenterLoadingText.setText(tr("يرجى الانتظار...", "Please Wait..."));
    }

    // 🚀 تصميم العواميد الموسيقية
    private void setupEqVisual(View view, String colorStr) {
        if (view == null) return;
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadii(new float[]{4*d, 4*d, 4*d, 4*d, 0, 0, 0, 0}); 
        gd.setColor(Color.parseColor(colorStr));
        view.setBackground(gd);
    }

    private void updateEqHeight(View view, int height) {
        if (view != null) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = height;
            view.setLayoutParams(params);
        }
    }

    // 🚀 دالة تصميم الأزرار الزجاجية الذكية ذات إضاءة الشرطة الدقيقة (LED) مع نبض التوهج
    public void updateLedButtonVisual(View btn, boolean isActive, String colorHex, int crDp) {
        if (btn == null) return;

        // إيقاف أي أنيميشن سابق لكي لا يستهلك الموارد
        Object tag = btn.getTag();
        if (tag instanceof android.animation.ObjectAnimator) {
            ((android.animation.ObjectAnimator) tag).cancel();
            btn.setTag(null);
        }

        // الاعتماد على view.post للحصول على الأبعاد الدقيقة للزر بعد رسمه في الشاشة
        btn.post(() -> {
            if (isDestroyed() || isFinishing()) return;

            float d = getResources().getDisplayMetrics().density;
            int w = btn.getWidth();
            int h = btn.getHeight();
            if (w == 0 || h == 0) return;

            // 1. الخلفية الزجاجية الأساسية للزر
            GradientDrawable base = new GradientDrawable();
            base.setShape(GradientDrawable.RECTANGLE);
            base.setCornerRadius(crDp * d);
            base.setColor(Color.parseColor("#6607A38A"));
            base.setStroke((int)(1.5f * d), Color.parseColor(isActive ? colorHex : "#80FFFFFF"));
            
            // 2. هندسة الشرطة العلوية الدقيقة (Dash) مثل الأجهزة الحقيقية
            GradientDrawable dash = new GradientDrawable();
            dash.setShape(GradientDrawable.RECTANGLE);
            dash.setCornerRadius(1 * d);
            dash.setColor(isActive ? Color.WHITE : Color.parseColor("#22000000"));
            
            // 3. صورة التوهج المشعة (zeeko17)
            android.graphics.drawable.Drawable glowImage = null;
            if (isActive) {
                glowImage = ContextCompat.getDrawable(OrgPianoActivity.this, R.drawable.zeeko17);
                if (glowImage != null) {
                    glowImage = glowImage.mutate(); 
                }
            }
            
            android.graphics.drawable.Drawable[] layers;
            if (isActive && glowImage != null) {
                layers = new android.graphics.drawable.Drawable[]{base, glowImage, dash};
            } else {
                layers = new android.graphics.drawable.Drawable[]{base, dash};
            }
            
            android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(layers);
            
            // حسابات التموضع لشرطة الـ LED
            int dashW = (int)(10 * d); // عرض دقيق (شرطة)
            int dashH = (int)(3 * d);  // ارتفاع نحيف
            int topMargin = (int)(6 * d); // مسافة من أعلى الزر
            
            int dashHInset = (w - dashW) / 2;
            int dashBottomInset = h - topMargin - dashH;
            
            if (isActive && glowImage != null) {
                // حسابات التموضع لتوسيط نجمة التوهج فوق الشرطة
                int glowSize = (int)(34 * d); 
                int glowHInset = (w - glowSize) / 2;
                int glowTopMargin = topMargin + (dashH / 2) - (glowSize / 2);
                int glowBottomInset = h - glowTopMargin - glowSize;
                
                ld.setLayerInset(0, 0, 0, 0, 0); // الزجاج الخلفي
                ld.setLayerInset(1, glowHInset, glowTopMargin, glowHInset, glowBottomInset); // إشعاع النجمة
                ld.setLayerInset(2, dashHInset, topMargin, dashHInset, dashBottomInset); // الشرطة البيضاء المضيئة
                
                // 🚀 نبض التلاشي فقط لزر التشغيل (Play/Stop)
                if (btn.getId() == R.id.ph_l_10) {
                    android.animation.ObjectAnimator animator = android.animation.ObjectAnimator.ofInt(glowImage, "alpha", 255, 30);
                    animator.setDuration(700);
                    animator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                    animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    animator.start();
                    btn.setTag(animator);
                }
            } else {
                ld.setLayerInset(0, 0, 0, 0, 0);
                ld.setLayerInset(1, dashHInset, topMargin, dashHInset, dashBottomInset); // Index 1 is Dash when inactive
            }
            
            btn.setBackground(ld);
            
            if (btn instanceof TextView) {
                ((TextView) btn).setTextColor(isActive ? Color.parseColor(colorHex) : Color.WHITE);
                ((TextView) btn).setShadowLayer(5, 1, 1, Color.BLACK);
            }
        });
    }

    private void initUI() {
        applyTranslationsToUI();
        
        eqCyan1 = findViewById(R.id.eqCyan1);
        eqGold1 = findViewById(R.id.eqGold1);
        eqCyan2 = findViewById(R.id.eqCyan2);
        eqGold2 = findViewById(R.id.eqGold2);
        eqCyan3 = findViewById(R.id.eqCyan3);
        eqGold3 = findViewById(R.id.eqGold3);

        setupEqVisual(eqCyan1, "#00FFFF");
        setupEqVisual(eqGold1, "#C5A059");
        setupEqVisual(eqCyan2, "#00FFFF");
        setupEqVisual(eqGold2, "#C5A059");
        setupEqVisual(eqCyan3, "#00FFFF");
        setupEqVisual(eqGold3, "#C5A059");
        
        eqRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRhythmPlaying) {
                    float d = getResources().getDisplayMetrics().density;
                    int min = (int)(4 * d);
                    int max = (int)(45 * d); 
                    
                    updateEqHeight(eqCyan1, min + (int)(Math.random() * (max - min)));
                    updateEqHeight(eqGold1, min + (int)(Math.random() * (max - min)));
                    updateEqHeight(eqCyan2, min + (int)(Math.random() * (max - min)));
                    updateEqHeight(eqGold2, min + (int)(Math.random() * (max - min)));
                    updateEqHeight(eqCyan3, min + (int)(Math.random() * (max - min)));
                    updateEqHeight(eqGold3, min + (int)(Math.random() * (max - min)));
                    
                    eqHandler.postDelayed(this, 100);
                } else {
                    float d = getResources().getDisplayMetrics().density;
                    int min = (int)(4 * d);
                    updateEqHeight(eqCyan1, min);
                    updateEqHeight(eqGold1, min);
                    updateEqHeight(eqCyan2, min);
                    updateEqHeight(eqGold2, min);
                    updateEqHeight(eqCyan3, min);
                    updateEqHeight(eqGold3, min);
                }
            }
        };

        // 🌟 التأكد من تحديث المتغيرات المؤقتة لأول مرة عند الإقلاع لكي لا تعرض "null" 🌟
        updateCachedNames();
        updateDashboardNames();
        updateDashboardTrans();
        updateDashboardTempo();
        updateDashboardVolume();

        // 🚀 تعديل زر الرجوع ليعمل بنظام الإعلانات البينية
        findViewById(R.id.btnBack).setOnClickListener(v -> goBackToMain());
        
        View rowSound1 = findViewById(R.id.rowSound1);
        if (rowSound1 != null) {
            rowSound1.setOnClickListener(v -> {
                if (!engineReady) return;
                if (currentSoundSelection == 1) {
                    OrgDialogs.showSelectionPopup(this, true);
                } else {
                    currentSoundSelection = 1;
                    audioEngine.switchSoundBank(0); 
                    updateDashboardNames();
                }
            });
        }
        View rowSound2 = findViewById(R.id.rowSound2);
        if (rowSound2 != null) {
            rowSound2.setOnClickListener(v -> {
                if (!engineReady) return;
                if (currentSoundSelection == 2) {
                    OrgDialogs.showSelectionPopup(this, true);
                } else {
                    currentSoundSelection = 2;
                    audioEngine.switchSoundBank(1); 
                    updateDashboardNames();
                }
            });
        }
        
        View rowRhythm1 = findViewById(R.id.rowRhythm1);
        if (rowRhythm1 != null) {
            rowRhythm1.setOnClickListener(v -> { 
                if (!engineReady) return;
                if (currentRhythmSelection == 1) {
                    OrgDialogs.showSelectionPopup(this, false);
                } else {
                    currentRhythmSelection = 1;
                    audioEngine.switchRhythmBank(0);
                    updateDashboardNames();
                }
            });
        }
        View rowRhythm2 = findViewById(R.id.rowRhythm2);
        if (rowRhythm2 != null) {
            rowRhythm2.setOnClickListener(v -> { 
                if (!engineReady) return;
                if (currentRhythmSelection == 2) {
                    OrgDialogs.showSelectionPopup(this, false);
                } else {
                    currentRhythmSelection = 2;
                    audioEngine.switchRhythmBank(1);
                    updateDashboardNames();
                }
            });
        }
        
        btnImportSet = findViewById(R.id.btnImportSet);
        if (btnImportSet != null) {
            setupGlassButton(btnImportSet); // الزجاج العادي فقط كما طلبت
            btnImportSet.setText(tr("إضافة سيت", "Add Set"));
            btnImportSet.setOnClickListener(v -> {
                // 🚀 استدعاء مدير الملفات الرسمي من جوجل بأمان تام
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                String[] mimetypes = {"application/zip", "application/octet-stream", "application/x-zip-compressed", "multipart/x-zip"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
                startActivityForResult(intent, 3000);
            });
        }
        
        // 🚀 تحويل زر placeholder إلى زر معلومات احترافي (الزجاج العادي)
        View oldInfo = findViewById(R.id.btnPlaceholderTopRight2);
        TextView btnInfo = null;
        if (oldInfo != null) {
            if (!(oldInfo instanceof TextView)) {
                ViewGroup parent = (ViewGroup) oldInfo.getParent();
                int index = parent.indexOfChild(oldInfo);
                parent.removeView(oldInfo);
                btnInfo = new TextView(this);
                btnInfo.setId(R.id.btnPlaceholderTopRight2);
                btnInfo.setLayoutParams(oldInfo.getLayoutParams());
                btnInfo.setGravity(Gravity.CENTER);
                btnInfo.setTextSize(10);
                parent.addView(btnInfo, index);
            } else {
                btnInfo = (TextView) oldInfo;
            }
            btnInfo.setText(tr("معلومات", "Info"));
            btnInfo.setTextColor(Color.WHITE); // لكي لا يظهر بالأسود
            setupGlassButton(btnInfo); // الزجاج العادي فقط
            btnInfo.setOnClickListener(v -> OrgDialogs.showAppInfoDialog(this));
        }

        btnCloudSets = findViewById(R.id.ph_r_4);
        if (btnCloudSets != null) {
            setupGlassButton(btnCloudSets); // الزجاج العادي فقط
            btnCloudSets.setText(tr("تحميل سيتات", "Download Sets"));
            btnCloudSets.setTextColor(Color.parseColor("#C5A059"));
            btnCloudSets.setOnClickListener(v -> {
                if (engineReady) {
                    OrgDialogs.showCloudSetsDialog(this);
                }
            });
        }

        btnTarteel = findViewById(R.id.btnTarteel);
        if (btnTarteel != null) btnTarteel.setText(tr("ترتيل", "Tarteel"));
        updateTarteelBtnVisual();
        btnTarteel.setOnClickListener(v -> {
            if (!engineReady) return; 
            isTarteelActive = !isTarteelActive;
            updateTarteelBtnVisual();
            if (audioEngine != null) {
                for (int i = 0; i < 24; i++) audioEngine.releasePoly(i);
                audioEngine.releaseMono();
            }
            monoNoteStack.clear();
            if (pianoView != null) pianoView.clearAllTouches();
        });
        
        TextView btnResetTuning = findViewById(R.id.btnResetTuning);
        if (btnResetTuning != null) {
            setupGlassButton(btnResetTuning); // الزجاج العادي فقط
            btnResetTuning.setText(tr("بدون تشريق", "Reset Tune"));
            btnResetTuning.setOnClickListener(v -> resetTuning());
        }

        TextView btnDefaultSet = findViewById(R.id.btnDefaultSet);
        if (btnDefaultSet != null) {
            setupGlassButton(btnDefaultSet); // الزجاج العادي فقط
            btnDefaultSet.setText(tr("الإفتراضي", "Default Set"));
            btnDefaultSet.setOnClickListener(v -> { if(engineReady) loadDefaultSet(); });
        }

        TextView btnVolume = findViewById(R.id.btnVolume);
        if (btnVolume != null) {
            setupGlassButton(btnVolume); // الزجاج العادي فقط
            btnVolume.setText(tr("الصوت", "Volume"));
            btnVolume.setOnClickListener(v -> { if(engineReady) OrgDialogs.showVolumePopup(this); });
        }

        TextView btnTranspose = findViewById(R.id.btnTranspose);
        if (btnTranspose != null) {
            btnTranspose.setText(tr("ترانسبوس", "Transpose"));
            btnTranspose.setOnClickListener(v -> { if(engineReady) OrgDialogs.showTransposePopup(this); });
        }

        TextView btnOctave = findViewById(R.id.btnOctave);
        if (btnOctave != null) {
            btnOctave.setText(tr("أوكتاف", "Octave"));
            btnOctave.setOnClickListener(v -> { if(engineReady) OrgDialogs.showOctavePopup(this); });
        }

        setupPitchBendJoystick();

        btnRhythmIntro = findViewById(R.id.ph_l_6);
        btnRhythmPad2 = findViewById(R.id.ph_l_7);
        btnRhythmPad1 = findViewById(R.id.ph_l_8);
        btnRhythmTempo = findViewById(R.id.ph_l_9);
        btnRhythmPlayStop = findViewById(R.id.ph_l_10);
        
        if (btnRhythmIntro != null) btnRhythmIntro.setText(tr("فاصل", "Intro"));
        if (btnRhythmPad2 != null) btnRhythmPad2.setText(tr("باد 2", "Pad 2"));
        if (btnRhythmPad1 != null) btnRhythmPad1.setText(tr("باد 1", "Pad 1"));
        if (btnRhythmTempo != null) btnRhythmTempo.setText(tr("السرعة", "Tempo"));
        
        updateRhythmVisuals();

        if (btnRhythmPlayStop != null) {
            btnRhythmPlayStop.setOnClickListener(v -> {
                if (!engineReady || audioEngine == null) return;
                isRhythmPlaying = !isRhythmPlaying;
                if (isRhythmPlaying) {
                    audioEngine.playRhythm(activeRhythmIndex);
                    uiSyncHandler.post(uiSyncRunnable); 
                } else {
                    audioEngine.stopRhythm();
                    pendingRhythmIndex = -1;
                    uiSyncHandler.removeCallbacks(uiSyncRunnable);
                }
                updateRhythmVisuals();
            });
        }

        if (btnRhythmTempo != null) btnRhythmTempo.setOnClickListener(v -> { if (engineReady) OrgDialogs.showTempoPopup(this); });
        if (btnRhythmPad1 != null) btnRhythmPad1.setOnClickListener(v -> switchRhythmTo(0));
        if (btnRhythmPad2 != null) btnRhythmPad2.setOnClickListener(v -> switchRhythmTo(1));
        if (btnRhythmIntro != null) btnRhythmIntro.setOnClickListener(v -> switchRhythmTo(2));

        setupTashreeqNotesRow();
    }

    private void setupPitchBendJoystick() {
        FrameLayout pitchContainer = findViewById(R.id.pitchBendContainer);
        View pitchBg = findViewById(R.id.pitchBendBg);
        
        ImageView pitchThumb = findViewById(R.id.pitchBendThumb);
        TextView tvJoystickTitle = findViewById(R.id.tvJoystickTitle);

        if (pitchContainer == null || pitchBg == null || pitchThumb == null) return;

        GradientDrawable bgPitch = new GradientDrawable();
        bgPitch.setShape(GradientDrawable.OVAL);
        bgPitch.setColor(Color.parseColor("#1A1A1A"));
        bgPitch.setStroke(3, Color.parseColor("#4D555555"));
        pitchBg.setBackground(bgPitch);

        GradientDrawable thumbPitch = new GradientDrawable();
        thumbPitch.setShape(GradientDrawable.OVAL);
        thumbPitch.setColors(new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#B3B3B3")});
        thumbPitch.setStroke(2, Color.parseColor("#666666"));
        pitchThumb.setBackground(thumbPitch);

        GradientDrawable titleBg = new GradientDrawable();
        titleBg.setShape(GradientDrawable.RECTANGLE);
        titleBg.setCornerRadius(100f); 
        titleBg.setColor(Color.parseColor("#4D000000"));
        titleBg.setStroke(1, Color.parseColor("#80C5A059"));
        if (tvJoystickTitle != null) {
            tvJoystickTitle.setBackground(titleBg);
            
            // 🚀 الإبقاء على لوحة المطور وكلمة السر كما هي
            tvJoystickTitle.setOnClickListener(v -> {
                devClickCount++;
                if (devClickRunnable != null) devClickHandler.removeCallbacks(devClickRunnable);
                devClickRunnable = () -> devClickCount = 0;
                devClickHandler.postDelayed(devClickRunnable, 2000);
                if (devClickCount >= 7) {
                    devClickCount = 0;
                    // 🚀 التحقق الصامت من هوية المطور للوصول السريع
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null && "GrWIoyYfgCVU3BJjZzfu2T0PYdo2".equals(user.getUid())) {
                        OrgDialogs.showDeveloperPanel(OrgPianoActivity.this);
                    }
                }
            });
        }

        pitchContainer.setOnTouchListener((v, event) -> {
            float centerX = pitchContainer.getWidth() / 2f;
            float centerY = pitchContainer.getHeight() / 2f;
            float thumbRadius = pitchThumb.getWidth() / 2f;
            float maxDistX = centerX - thumbRadius;
            float maxDistY = centerY - thumbRadius;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    float touchX = event.getX();
                    float touchY = event.getY();
                    float deltaX = touchX - centerX;
                    float deltaY = touchY - centerY;
                    double distance = Math.hypot(deltaX, deltaY);
                    if (distance > maxDistX) { 
                        double ratio = maxDistX / distance;
                        deltaX *= ratio;
                        deltaY *= ratio;
                    }
                    pitchThumb.setTranslationX(deltaX);
                    pitchThumb.setTranslationY(deltaY);
                    if (audioEngine != null && engineReady) {
                        float bend = deltaX / maxDistX; 
                        float pitchMult = (float) Math.pow(2.0, (bend * 2.0) / 12.0); 
                        audioEngine.setPitchBend(pitchMult);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    pitchThumb.animate().translationX(0f).translationY(0f).setDuration(250).start();
                    if (audioEngine != null && engineReady) audioEngine.setPitchBend(1.0f);
                    break;
            }
            return true;
        });
    }

    private void initNativeAudioEngine() {
        synchronized (AUDIO_LIFECYCLE_LOCK) {
            if (pendingAudioLoadBatches > 0) {
                showLoading(tr("جاري إنهاء تحميل الصوت السابق...", "Finishing previous audio load..."));
                if (audioInitRunnable == null) {
                    audioInitRunnable = this::initNativeAudioEngine;
                }
                audioInitHandler.removeCallbacks(audioInitRunnable);
                audioInitHandler.postDelayed(audioInitRunnable, 100);
                return;
            }
        }

        audioEngine = NativeAudioEngine.getInstance(); 
        
        showLoading(tr("يرجى الانتظار...", "Please Wait..."));
        
        // 🚀 السحر يبدأ هنا: استدعاء الأسماء الافتراضية، وفي حال عدم وجودها سيقرأ من الـ Raw
        String s1 = activeSetIsDefault ? "string" : getDisplayNameFromJson("sound1", externalSoundFallback());
        String s2 = activeSetIsDefault ? "saz" : getDisplayNameFromJson("sound2", externalSoundFallback());
        String r1 = activeSetIsDefault ? "dj27" : getDisplayNameFromJson("rhythm1", externalRhythmFallback());
        String r2 = activeSetIsDefault ? "salo" : getDisplayNameFromJson("rhythm2", externalRhythmFallback());
        
        NativeAudioEngine loadingEngine = audioEngine;
        loadAudioSetTracked(loadingEngine, s1, s2, r1, r2, activeSetIsDefault, () -> {
            if (isFinishing() || isDestroyed() || audioEngine != loadingEngine) return;
            if (!loadingEngine.isAudioReady()) {
                engineReady = false;
                if (pianoView != null) pianoView.setTouchEnabled(false);
                hideLoading();
                android.util.Log.e("ZeekoAudio", "Audio initialization/loading failed; check ZeekoAudio logs for missing or undecodable samples.");
                Toast.makeText(this, tr("تعذر تحميل الأصوات. أعد فتح شاشة العزف أو تحقق من ملفات السيت.", "Audio samples could not be loaded. Reopen the piano screen or check the set files."), Toast.LENGTH_LONG).show();
                return;
            }
            engineReady = true;
            if (pianoView != null) pianoView.setTouchEnabled(true); 
            hideLoading(); 
            
            // 🌟 1. تحديث الذاكرة المؤقتة للأسماء وقراءتها من info.json قبل المسح 🌟
            updateCachedNames(); 
            // 🌟 2. تحديث الشاشة من الذاكرة المؤقتة 🌟
            updateDashboardNames(); 
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    public void hideSystemUI() {
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
        }
    }

    private void applyLanguage() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentLang = prefs.getString("app_language", "ar");
    }

    public String tr(String ar, String en) { return "en".equals(currentLang) ? en : ar; }

    private void setupGlassButton(View view) {
        if (view == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(getResources().getDisplayMetrics().density * 15);
        gd.setColor(Color.parseColor("#4D555555"));
        gd.setStroke(2, Color.parseColor("#80FFFFFF"));
        view.setBackground(gd);
        if (view instanceof TextView) {
            ((TextView) view).setShadowLayer(5, 1, 1, Color.BLACK);
        }
    }

    private void setupTashreeqNotesRow() {
        LinearLayout row = findViewById(R.id.tashreeqNotesRow);
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 12; i++) {
            final int noteIndex = i;
            TextView btn = new TextView(this);
            btn.setText(notesNames[i]);
            btn.setTextColor(Color.WHITE);
            // 🚀 تم التصغير ليتوافق مع جميع الهواتف
            btn.setTextSize(10);
            btn.setGravity(Gravity.CENTER);
            btn.setShadowLayer(5, 1, 1, Color.BLACK);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, (int)(28 * density), 1.0f);
            // 🚀 تقليل المسافات لتتنفس الأزرار
            params.setMargins((int)(0.5f * density), 0, (int)(0.5f * density), 0);
            btn.setLayoutParams(params);
            updateNoteBtnVisual(btn, false);
            
            btn.setOnClickListener(v -> {
                if(engineReady) OrgDialogs.showTuningPopup(this, noteIndex, btn);
            });
            row.addView(btn);
        }
    }

    public void updateNoteBtnVisual(TextView btn, boolean isDetuned) {
        // مفاتيح التشريق لها انحناء مختلف قليلاً (10)
        updateLedButtonVisual(btn, isDetuned, "#D4CA6A", 10);
    }

    private void updateTarteelBtnVisual() {
        if (btnTarteel != null) {
            updateLedButtonVisual(btnTarteel, isTarteelActive, "#C5A059", 15);
        }
    }

    private void switchRhythmTo(int newIndex) {
        if (!engineReady || audioEngine == null) return;
        if (isRhythmPlaying) {
            pendingRhythmIndex = newIndex; 
            audioEngine.playRhythm(newIndex); 
        } else {
            activeRhythmIndex = newIndex;
            audioEngine.playRhythm(newIndex);
            isRhythmPlaying = true;
            uiSyncHandler.post(uiSyncRunnable); 
        }
        updateRhythmVisuals();
    }

    private void updateRhythmVisuals() {
        if (btnRhythmPlayStop != null) {
            btnRhythmPlayStop.setText(isRhythmPlaying ? tr("إيقاف", "Stop") : tr("تشغيل", "Play"));
            updateLedButtonVisual(btnRhythmPlayStop, isRhythmPlaying, "#1DB954", 15);
        }

        TextView[] pads = {btnRhythmPad1, btnRhythmPad2, btnRhythmIntro};
        for (int i = 0; i < pads.length; i++) {
            if (pads[i] == null) continue;
            boolean isActive = (i == pendingRhythmIndex) || (i == activeRhythmIndex);
            String color = (i == pendingRhythmIndex) ? "#1DB954" : "#C5A059";
            updateLedButtonVisual(pads[i], isActive, color, 15);
        }
        
        // 🚀 تحريك الموجات الصوتية
        if (isRhythmPlaying) {
            eqHandler.removeCallbacks(eqRunnable);
            eqHandler.post(eqRunnable);
        } else {
            eqHandler.removeCallbacks(eqRunnable);
            float d = getResources().getDisplayMetrics().density;
            int min = (int)(4 * d);
            updateEqHeight(eqCyan1, min);
            updateEqHeight(eqGold1, min);
            updateEqHeight(eqCyan2, min);
            updateEqHeight(eqGold2, min);
            updateEqHeight(eqCyan3, min);
            updateEqHeight(eqGold3, min);
        }
    }

    private void setupPianoView() {
        pianoView = findViewById(R.id.pianoView);
        pianoView.setTouchEnabled(false); 
        
        pianoView.setPianoListener(new PianoView.PianoListener() {
            @Override
            public void onKeyDown(int noteIndex) {
                if (!engineReady || audioEngine == null) return;
                
                float baseRate = notePitchRates[noteIndex % 12];
                int targetNote = noteIndex + currentTranspose + (currentOctave * 12);
                
                int engineNoteIndex = targetNote;
                int pitchShiftSemitones = 0;
                
                while (engineNoteIndex < 0) {
                    engineNoteIndex += 12;
                    pitchShiftSemitones -= 12;
                }
                while (engineNoteIndex >= 24) {
                    engineNoteIndex -= 12;
                    pitchShiftSemitones += 12;
                }
                
                float octaveShiftRate = (float) Math.pow(2.0, pitchShiftSemitones / 12.0);
                float finalRate = baseRate * octaveShiftRate;

                activeMappedNotes[noteIndex] = engineNoteIndex; 
                activeMappedRates[noteIndex] = finalRate;

                if (isTarteelActive) {
                    boolean isNewNote = (monoNoteStack.isEmpty() || monoNoteStack.getLast() != noteIndex);
                    monoNoteStack.remove((Integer) noteIndex); 
                    monoNoteStack.addLast(noteIndex);
                    if (isNewNote) {
                        audioEngine.playMono(engineNoteIndex, 0.90f, finalRate);
                    }
                } else {
                    audioEngine.playPoly(engineNoteIndex, 0.90f, finalRate);
                }
            }

            @Override
            public void onKeyUp(int noteIndex) {
                if (!engineReady || audioEngine == null) return;
                
                int actualNote = activeMappedNotes[noteIndex];
                if (actualNote == -1) actualNote = noteIndex;

                if (isTarteelActive) {
                    boolean wasPlaying = (!monoNoteStack.isEmpty() && monoNoteStack.getLast() == noteIndex);
                    monoNoteStack.remove((Integer) noteIndex);
                    
                    if (wasPlaying) {
                        if (!monoNoteStack.isEmpty()) {
                            int lastNoteIndex = monoNoteStack.getLast();
                            int lastActualNote = activeMappedNotes[lastNoteIndex];
                            if (lastActualNote == -1) lastActualNote = lastNoteIndex;
                            
                            float lastRate = activeMappedRates[lastNoteIndex];
                            
                            audioEngine.playMono(lastActualNote, 0.90f, lastRate);
                        } else {
                            audioEngine.releaseMono();
                        }
                    }
                } else {
                    audioEngine.releasePoly(actualNote);
                }
                activeMappedNotes[noteIndex] = -1;
            }
        });
    }

}
