package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application class that bootstraps:
 * 1. KhojSupabaseClient — requires app context for SettingsSessionManager
 * 2. Coil ImageLoader — global singleton with 50MB disk cache, 25% RAM memory cache
 */
class KhojApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Initialize Supabase client with this Application context so
        // SettingsSessionManager can persist the auth session to SharedPreferences.
        com.example.data.api.KhojSupabaseClient.init(this)

        // Initialize WorkManager background data synchronization
        com.example.data.sync.SyncManager.schedulePeriodicSync(this)
    }

    /**
     * Registered as the global Coil ImageLoader via the [ImageLoaderFactory] interface.
     * Compose's AsyncImage picks this up automatically — no call-site changes needed.
     */
    override fun newImageLoader(): ImageLoader {
        val cacheOkHttp = com.example.data.api.NetworkClientProvider.baseOkHttpClient.newBuilder()
            .addNetworkInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)     // ImgBB & Supabase storage use redirects
            .followSslRedirects(true)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(cacheOkHttp)
            // Memory cache: up to 25% of available app RAM
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Disk cache: 50 MB on internal storage
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // SVG support (existing dependency: coil-svg)
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(300)
            .respectCacheHeaders(false)   // ImgBB ignores cache-control, serve from our cache
            .build()
    }
}
