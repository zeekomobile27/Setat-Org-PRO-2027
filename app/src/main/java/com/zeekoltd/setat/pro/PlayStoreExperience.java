package com.zeekoltd.setat.pro;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Uses Google's Play in-app update and in-app review flows.
 * Immediate updates are requested only for high-priority releases.
 */
final class PlayStoreExperience {
    private static final String TAG = "PlayStoreExperience";
    private static final String PREFS = "app_prefs";
    private static final String KEY_SESSION_COUNT = "play_experience_session_count";
    private static final String KEY_FIRST_SESSION = "play_experience_first_session";
    private static final String KEY_REVIEW_REQUESTED_AT = "play_review_requested_at";
    private static final int MIN_SESSIONS_FOR_REVIEW = 3;
    private static final long MIN_AGE_FOR_REVIEW_MS = TimeUnit.DAYS.toMillis(2);
    private static final long REVIEW_COOLDOWN_MS = TimeUnit.DAYS.toMillis(60);

    private final Activity activity;
    private final ActivityResultLauncher<IntentSenderRequest> updateLauncher;
    private final AppUpdateManager appUpdateManager;
    private boolean updateCheckInProgress;
    private boolean updateFlowActive;

    PlayStoreExperience(Activity activity,
                        ActivityResultLauncher<IntentSenderRequest> updateLauncher) {
        this.activity = activity;
        this.updateLauncher = updateLauncher;
        this.appUpdateManager = AppUpdateManagerFactory.create(activity);
    }

    void onNewSession() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        long firstSession = prefs.getLong(KEY_FIRST_SESSION, 0L);
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = prefs.edit();
        if (firstSession == 0L) editor.putLong(KEY_FIRST_SESSION, now);
        editor.putInt(KEY_SESSION_COUNT, prefs.getInt(KEY_SESSION_COUNT, 0) + 1);
        editor.apply();
    }

    void checkForRequiredUpdate() {
        if (activity.isFinishing() || activity.isDestroyed() || updateFlowActive || updateCheckInProgress) return;
        updateCheckInProgress = true;
        appUpdateManager.getAppUpdateInfo()
            .addOnCompleteListener(task -> {
                updateCheckInProgress = false;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (task.isSuccessful()) {
                    handleUpdateInfo(task.getResult());
                } else {
                    Log.w(TAG, "Unable to check Google Play update", task.getException());
                }
            });
    }

    private void handleUpdateInfo(AppUpdateInfo info) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        int availability = info.updateAvailability();
        if (availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            startUpdateFlow(info, AppUpdateType.IMMEDIATE);
            return;
        }
        if (availability != UpdateAvailability.UPDATE_AVAILABLE) return;

        // This app requires users to update before continuing. Request the official
        // blocking flow whenever Play allows it, independent of release priority.
        if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            startUpdateFlow(info, AppUpdateType.IMMEDIATE);
        } else if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            // Keep the Play update prompt available if an immediate flow is not
            // eligible for this specific update/device.
            startUpdateFlow(info, AppUpdateType.FLEXIBLE);
        }
    }

    private void startUpdateFlow(AppUpdateInfo info, int updateType) {
        if (activity.isFinishing() || activity.isDestroyed() || updateFlowActive) return;
        updateFlowActive = true;
        try {
            appUpdateManager.startUpdateFlowForResult(
                info,
                updateLauncher,
                AppUpdateOptions.newBuilder(updateType).build()
            );
        } catch (Exception error) {
            updateFlowActive = false;
            Log.w(TAG, "Unable to launch Google Play update flow", error);
        }
    }

    void onUpdateFlowResult() {
        updateFlowActive = false;
    }

    boolean isUpdateFlowActive() {
        return updateFlowActive;
    }

    void requestInAppReviewIfEligible() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        long firstSession = prefs.getLong(KEY_FIRST_SESSION, 0L);
        int sessions = prefs.getInt(KEY_SESSION_COUNT, 0);
        long lastRequest = prefs.getLong(KEY_REVIEW_REQUESTED_AT, 0L);
        long now = System.currentTimeMillis();
        if (sessions < MIN_SESSIONS_FOR_REVIEW
                || firstSession == 0L
                || now - firstSession < MIN_AGE_FOR_REVIEW_MS
                || (lastRequest > 0L && now - lastRequest < REVIEW_COOLDOWN_MS)) {
            return;
        }

        try {
            ReviewManager reviewManager = ReviewManagerFactory.create(activity);
            reviewManager.requestReviewFlow().addOnCompleteListener(request -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (!request.isSuccessful()) {
                    Log.w(TAG, "Unable to request Google Play review", request.getException());
                    openPlayStoreListing();
                    prefs.edit().putLong(KEY_REVIEW_REQUESTED_AT, System.currentTimeMillis()).apply();
                    return;
                }

                prefs.edit().putLong(KEY_REVIEW_REQUESTED_AT, System.currentTimeMillis()).apply();
                try {
                    reviewManager.launchReviewFlow(activity, request.getResult())
                        .addOnFailureListener(error ->
                            Log.w(TAG, "Unable to launch Google Play review flow", error));
                } catch (RuntimeException error) {
                    Log.w(TAG, "Unable to launch Google Play review flow", error);
                    openPlayStoreListing();
                }
            });
        } catch (RuntimeException error) {
            Log.w(TAG, "Google Play review API is unavailable", error);
            openPlayStoreListing();
        }
    }

    private void openPlayStoreListing() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Uri listing = Uri.parse("https://play.google.com/store/apps/details?id=" + activity.getPackageName());
        Intent intent = new Intent(Intent.ACTION_VIEW, listing);
        try {
            activity.startActivity(intent);
        } catch (Exception error) {
            Log.w(TAG, "Unable to open Google Play listing", error);
        }
    }
}
