package com.example.data.api

import android.content.Context
import com.example.BuildConfig
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import com.russhwolf.settings.SharedPreferencesSettings
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Supabase client singleton.
 *
 * Must be initialized before first use by calling [init] from [com.example.KhojApplication.onCreate].
 * This ensures session tokens persist across process restarts.
 */
@OptIn(SupabaseInternal::class, SupabaseExperimental::class)
object KhojSupabaseClient {

    @Volatile
    private var _client: io.github.jan.supabase.SupabaseClient? = null

    val client: io.github.jan.supabase.SupabaseClient
        get() = _client ?: error(
            "KhojSupabaseClient is not initialized. Call KhojSupabaseClient.init(context) in Application.onCreate()."
        )

    fun init(context: Context) {
        if (_client != null) return

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)

        val sharedHttpClient = NetworkClientProvider.supabaseHttpClient

        _client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            httpEngine = OkHttp.create {
                preconfigured = sharedHttpClient
            }

            install(Auth) {
                sessionManager = SettingsSessionManager(SharedPreferencesSettings(prefs))
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Realtime)

            httpConfig {
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000L
                    socketTimeoutMillis = 30_000L
                }
            }
        }
    }
}
