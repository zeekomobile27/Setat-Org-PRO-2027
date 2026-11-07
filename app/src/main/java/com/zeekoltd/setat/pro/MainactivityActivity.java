package com.zeekoltd.setat.pro;

import android.app.Activity;
import android.widget.FrameLayout;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseApp;

import java.util.Locale;

public class MainactivityActivity extends AppCompatActivity {

    private String currentLang = "ar";
    private SharedPreferences prefs;

    private LinearLayout connectionLayout;
    private TextView textViewStatus;
    private ProgressBar progressBar;
    private LinearLayout buttonRetry;
    private TextView tvBtnRetry;
    private FrameLayout blurBtnRetry;
    private View mainContentView;

    private String tr(String arabicText, String englishText) {
        return "en".equals(currentLang) ? englishText : arabicText;
    }

    private void applyLanguage() {
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        currentLang = prefs.getString("app_language", "ar");

        Locale locale = new Locale(currentLang);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);

        if ("en".equals(currentLang)) {
            config.setLayoutDirection(Locale.ENGLISH);
        } else {
            config.setLayoutDirection(Locale.forLanguageTag("ar"));
        }

        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        FirebaseApp.initializeApp(this);
        
        applyLanguage();
        EdgeToEdgeHelper.enable(getWindow());
        setContentView(R.layout.mainactivity);

        mainContentView = findViewById(R.id.mainContentView);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            ViewCompat.setOnApplyWindowInsetsListener(mainContentView, (v, insets) -> {
                View connectionLayout = findViewById(R.id.connectionLayout);
                float d = getResources().getDisplayMetrics().density;
                
                if (connectionLayout != null) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) connectionLayout.getLayoutParams();
                    params.topMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top + (int)(15 * d);
                    params.bottomMargin = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime()).bottom + (int)(15 * d);
                    connectionLayout.setLayoutParams(params);
                }
                return insets;
            });
            ViewCompat.requestApplyInsets(mainContentView);
        }

        connectionLayout = findViewById(R.id.connectionLayout);
        textViewStatus = findViewById(R.id.textViewStatus);
        progressBar = findViewById(R.id.progressBar);
        buttonRetry = findViewById(R.id.buttonRetry);
        tvBtnRetry = findViewById(R.id.tvBtnRetry);
        blurBtnRetry = findViewById(R.id.blurBtnRetry);

        tvBtnRetry.setText(tr("إعادة المحاولة", "Retry"));
        textViewStatus.setText(tr("جاري التحقق من الاتصال...", "Checking the connection..."));
        
        blurBtnRetry.setVisibility(View.GONE);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                DialogHelper.showExitDialog(MainactivityActivity.this, currentLang);
            }
        });

        setupFrameLayouts();
        checkNetworkAndProceed(1500);

        buttonRetry.setOnTouchListener((v, event) -> {
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
        });

        buttonRetry.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                textViewStatus.setText(tr("جاري إعادة التحقق...", "Checking again..."));
                blurBtnRetry.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                checkNetworkAndProceed(1000);
            }).start();
        });
    }
    
    private void setupFrameLayouts() {}

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || 
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }

    private void checkNetworkAndProceed(int delayMillis) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isNetworkAvailable()) {
                textViewStatus.setText(tr("الاتصال متاح! جاري التحميل...", "Connection available! Loading..."));
                progressBar.setVisibility(View.VISIBLE);
                blurBtnRetry.setVisibility(View.GONE);

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startActivity(new Intent(MainactivityActivity.this, MainActivity.class));
                    finish();
                }, 2000);
            } else {
                textViewStatus.setText(tr("لا يوجد اتصال بالإنترنت", "No internet connection"));
                progressBar.setVisibility(View.GONE);
                blurBtnRetry.setVisibility(View.VISIBLE);
            }
        }, delayMillis);
    }
}