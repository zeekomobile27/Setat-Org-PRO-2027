package com.zeekoltd.setat.pro;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

/** Safe sampled decoding helpers for images received from Firebase. */
public final class BitmapDecodeUtils {
    private static final int MAX_BASE64_CHARS = 8 * 1024 * 1024;

    private BitmapDecodeUtils() {}

    /** Decode a Base64 image to a bounded bitmap. Call from a worker thread. */
    public static Bitmap decodeBase64Sampled(String encoded, int maxDimension) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_BASE64_CHARS) return null;
        int target = Math.max(64, Math.min(maxDimension, 1024));
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            if (bytes.length == 0) return null;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sampleSize = 1;
            int largestSide = Math.max(bounds.outWidth, bounds.outHeight);
            while (largestSide / sampleSize > target * 2) {
                sampleSize *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (IllegalArgumentException | OutOfMemoryError ignored) {
            return null;
        }
    }
}
