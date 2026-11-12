package com.zeekoltd.setat.pro;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Decodes Firebase Base64 images off the UI thread and bounds the in-memory cache. */
public final class SafeBase64ImageLoader {
    private static final int CACHE_MAX_BYTES = 8 * 1024 * 1024;
    private static final int TARGET_DIMENSION = 256;
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService DECODERS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "firebase-image-decoder");
        thread.setDaemon(true);
        return thread;
    });
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(CACHE_MAX_BYTES) {
        @Override
        protected int sizeOf(@NonNull String key, @NonNull Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };
    private static final Map<String, List<PendingTarget>> IN_FLIGHT = new HashMap<>();

    private SafeBase64ImageLoader() {}

    public static void load(@NonNull ImageView imageView, @NonNull String stableKey,
                            String encoded, @DrawableRes int placeholder) {
        imageView.setImageResource(placeholder);
        Object requestToken = new Object();
        imageView.setTag(R.id.safe_base64_image_request, requestToken);
        if (encoded == null || encoded.isEmpty() || encoded.length() > 8 * 1024 * 1024) return;

        // Use the encoded String identity to avoid scanning large Base64 data on the UI thread.
        String cacheKey = stableKey + ':' + encoded.length() + ':' + System.identityHashCode(encoded);
        Bitmap cached;
        boolean shouldDecode = false;
        synchronized (LOCK) {
            cached = CACHE.get(cacheKey);
            if (cached == null) {
                List<PendingTarget> targets = IN_FLIGHT.get(cacheKey);
                if (targets == null) {
                    targets = new ArrayList<>();
                    IN_FLIGHT.put(cacheKey, targets);
                    shouldDecode = true;
                }
                targets.add(new PendingTarget(imageView, requestToken));
            }
        }

        if (cached != null) {
            imageView.setImageBitmap(cached);
        } else if (shouldDecode) {
            final String encodedCopy = encoded;
            DECODERS.execute(() -> decodeAndDeliver(cacheKey, encodedCopy));
        }
    }

    private static void decodeAndDeliver(String cacheKey, String encoded) {
        Bitmap bitmap = BitmapDecodeUtils.decodeBase64Sampled(encoded, TARGET_DIMENSION);
        final List<PendingTarget> targets;
        synchronized (LOCK) {
            if (bitmap != null) CACHE.put(cacheKey, bitmap);
            List<PendingTarget> pending = IN_FLIGHT.remove(cacheKey);
            targets = pending == null ? new ArrayList<>() : pending;
        }
        if (bitmap == null || targets.isEmpty()) return;

        final Bitmap decoded = bitmap;
        MAIN.post(() -> {
            for (PendingTarget target : targets) {
                ImageView imageView = target.view.get();
                if (imageView != null && imageView.getTag(R.id.safe_base64_image_request) == target.token) {
                    imageView.setImageBitmap(decoded);
                }
            }
        });
    }

    private static final class PendingTarget {
        final WeakReference<ImageView> view;
        final Object token;

        PendingTarget(ImageView view, Object token) {
            this.view = new WeakReference<>(view);
            this.token = token;
        }
    }
}
