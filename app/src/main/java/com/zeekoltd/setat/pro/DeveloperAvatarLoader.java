package com.zeekoltd.setat.pro;

import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Fetches the developer avatar once per process; image decoding is delegated off-thread. */
public final class DeveloperAvatarLoader {
    private static final Object LOCK = new Object();
    private static final int MAX_ENCODED_CHARS = 8 * 1024 * 1024;
    private static final List<WeakReference<ImageView>> WAITING_VIEWS = new ArrayList<>();
    private static String cachedBase64;
    private static boolean requestInFlight;
    private static boolean requestComplete;

    private DeveloperAvatarLoader() {}

    public static void load(@NonNull ImageView imageView) {
        String imageData;
        boolean shouldFetch = false;
        synchronized (LOCK) {
            imageData = cachedBase64;
            if (imageData == null && !requestComplete) {
                WAITING_VIEWS.add(new WeakReference<>(imageView));
                if (!requestInFlight) {
                    requestInFlight = true;
                    shouldFetch = true;
                }
            }
        }

        if (imageData != null) {
            SafeBase64ImageLoader.load(imageView, "developer-avatar", imageData, R.drawable.ic_default_user);
        } else {
            imageView.setImageResource(R.drawable.ic_default_user);
        }
        if (!shouldFetch) return;

        FirebaseDatabase.getInstance().getReference("user_avatars").child(MainActivity.DEV_UID)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String result = snapshot.getValue(String.class);
                        if (result != null && (result.isEmpty() || result.length() > MAX_ENCODED_CHARS)) result = null;
                        deliver(result, true);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        deliver(null, false);
                    }
                });
    }

    private static void deliver(String result, boolean completed) {
        List<ImageView> views = new ArrayList<>();
        synchronized (LOCK) {
            cachedBase64 = result;
            requestInFlight = false;
            requestComplete = completed;
            Iterator<WeakReference<ImageView>> iterator = WAITING_VIEWS.iterator();
            while (iterator.hasNext()) {
                ImageView view = iterator.next().get();
                if (view != null) views.add(view);
                iterator.remove();
            }
        }
        if (result == null) return;
        for (ImageView view : views) {
            SafeBase64ImageLoader.load(view, "developer-avatar", result, R.drawable.ic_default_user);
        }
    }
}
