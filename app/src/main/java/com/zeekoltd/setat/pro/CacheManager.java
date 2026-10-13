package com.zeekoltd.setat.pro;

import android.content.Context;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import java.io.File;

public class CacheManager {
    private static SimpleCache simpleCache;
    private static StandaloneDatabaseProvider databaseProvider;
    
    // حجم الكاش: 600 ميجابايت لتخزين كل السيتات
    private static final long CACHE_SIZE = 600L * 1024L * 1024L;

    public static synchronized SimpleCache getInstance(Context context) {
        if (simpleCache == null) {
            File cacheDir = new File(context.getApplicationContext().getCacheDir(), "exo_global_cache");
            if (databaseProvider == null) {
                databaseProvider = new StandaloneDatabaseProvider(context.getApplicationContext());
            }
            simpleCache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(CACHE_SIZE), databaseProvider);
        }
        return simpleCache;
    }
}
