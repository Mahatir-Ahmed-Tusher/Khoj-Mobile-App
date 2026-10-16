package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RumorMeterClient {

    private const val FALLBACK_URL = "https://khoj-rumour-meter.mahatirtusher.workers.dev/"

    private val BASE_URL: String by lazy {
        val configured = try {
            BuildConfig::class.java.getField("KHOJ_RUMOUR_METER_URL").get(null) as? String
        } catch (_: Throwable) {
            null
        }
        val target = if (!configured.isNullOrBlank()) {
            configured.trim().removeSurrounding("\"").removeSurrounding("'")
        } else {
            FALLBACK_URL
        }
        if (target.endsWith("/")) target else "$target/"
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = NetworkClientProvider.baseOkHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    val api: RumorMeterApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RumorMeterApi::class.java)
    }
}
