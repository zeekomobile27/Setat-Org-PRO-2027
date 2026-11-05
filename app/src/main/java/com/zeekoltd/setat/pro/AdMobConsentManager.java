package com.zeekoltd.setat.pro;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates Google's UMP consent flow before any Mobile Ads SDK initialization.
 * The consent request runs once per process and all requesting screens share its result.
 */
public final class AdMobConsentManager {

    public interface ConsentCallback {
        void onConsentResult(boolean canRequestAds);
    }

    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<ConsentCallback> PENDING_CALLBACKS = new ArrayList<>();

    private static boolean requestInProgress;
    private static boolean requestComplete;
    private static boolean lastCanRequestAds;

    private AdMobConsentManager() {}

    public static void ensureConsent(@NonNull Activity activity, ConsentCallback callback) {
        ConsentInformation consentInformation =
                UserMessagingPlatform.getConsentInformation(activity.getApplicationContext());

        synchronized (LOCK) {
            if (callback != null) PENDING_CALLBACKS.add(callback);
            if (requestInProgress) return;
            if (requestComplete) {
                dispatchPending(lastCanRequestAds);
                return;
            }
            requestInProgress = true;
        }

        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build();

        consentInformation.requestConsentInfoUpdate(
                activity,
                parameters,
                () -> {
                    Log.i("AdMobConsent", "UMP info updated; consentStatus="
                            + consentInformation.getConsentStatus()
                            + ", privacyOptions="
                            + consentInformation.getPrivacyOptionsRequirementStatus());
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                        activity,
                        formError -> {
                            if (formError != null) {
                                Log.e("AdMobConsent", "UMP consent form failed: " + formError.getMessage());
                            }
                            complete(consentInformation);
                        });
                },
                requestError -> {
                    Log.e("AdMobConsent", "UMP consent info update failed: " + requestError.getMessage());
                    complete(consentInformation);
                });
    }


    public interface PrivacyOptionsCallback {
        void onResult(boolean shown, String errorMessage);
    }

    public static boolean isPrivacyOptionsRequired(@NonNull Activity activity) {
        ConsentInformation information =
                UserMessagingPlatform.getConsentInformation(activity.getApplicationContext());
        return information.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public static void showPrivacyOptionsForm(@NonNull Activity activity, PrivacyOptionsCallback callback) {
        ConsentInformation information =
                UserMessagingPlatform.getConsentInformation(activity.getApplicationContext());
        if (information.getPrivacyOptionsRequirementStatus()
                != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            if (callback != null) callback.onResult(false, null);
            return;
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            if (callback != null) {
                callback.onResult(formError == null, formError == null ? null : formError.getMessage());
            }
        });
    }

    private static void complete(ConsentInformation consentInformation) {
        boolean canRequestAds = consentInformation.canRequestAds();
        Log.i("AdMobConsent", "UMP consent flow complete; canRequestAds=" + canRequestAds);
        synchronized (LOCK) {
            requestInProgress = false;
            requestComplete = true;
            lastCanRequestAds = canRequestAds;
        }
        dispatchPending(canRequestAds);
    }

    private static void dispatchPending(boolean canRequestAds) {
        final List<ConsentCallback> callbacks;
        synchronized (LOCK) {
            callbacks = new ArrayList<>(PENDING_CALLBACKS);
            PENDING_CALLBACKS.clear();
        }
        MAIN.post(() -> {
            for (ConsentCallback callback : callbacks) {
                try {
                    callback.onConsentResult(canRequestAds);
                } catch (RuntimeException ignored) {
                    // One consumer must not prevent other screens from receiving consent state.
                }
            }
        });
    }
}
