package com.zeekoltd.setat.pro;

import android.app.Activity;
import android.content.SharedPreferences;
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
    private static final String KEY_REVIEW_ATTEMPTED = "play_review_attempted";
    private static final int HIGH_PRIORITY_THRESHOLD = 4;
    private static final int MIN_SESSIONS_FOR_REVIEW = 5;
    private static final long MIN_AGE_FOR_REVIEW_MS = TimeUnit.DAYS.toMillis(7);

    private final Activity activity;
    private final ActivityResultLauncher<IntentSenderRequest> updateLauncher;
    private final AppUpdateManager appUpdateManager;
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
        if (activity.isFinishing() || activity.isDestroyed() || updateFlowActive) return;
        appUpdateManager.getAppUpdateInfo()
            .addOnSuccessListener(info -> handleUpdateInfo(info))
            .addOnFailureListener(error -> Log.w(TAG, "Unable to check Google Play update", error));
    }

    private void handleUpdateInfo(AppUpdateInfo info) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        int availability = info.updateAvailability();
        if (availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            startUpdateFlow(info, AppUpdateType.IMMEDIATE);
            return;
        }
        if (availability != UpdateAvailability.UPDATE_AVAILABLE) return;

        // Google Play's guidance reserves immediate updates for critical changes.
        // Ordinary releases remain available through Google Play's normal update flow.
        if (info.updatePriority() >= HIGH_PRIORITY_THRESHOLD
                && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            startUpdateFlow(info, AppUpdateType.IMMEDIATE);
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
        if (prefs.getBoolean(KEY_REVIEW_ATTEMPTED, false)
                || sessions < MIN_SESSIONS_FOR_REVIEW
                || firstSession == 0L
                || System.currentTimeMillis() - firstSession < MIN_AGE_FOR_REVIEW_MS) {
            return;
        }

        ReviewManager reviewManager = ReviewManagerFactory.create(activity);
        reviewManager.requestReviewFlow().addOnCompleteListener(request -> {
            if (!request.isSuccessful() || activity.isFinishing() || activity.isDestroyed()) return;
            reviewManager.launchReviewFlow(activity, request.getResult())
                .addOnCompleteListener(flow -> {
                    // Google Play decides whether to display its review card and does not
                    // reveal whether a review was submitted.
                    prefs.edit().putBoolean(KEY_REVIEW_ATTEMPTED, true).apply();
                });
        });
    }
}
