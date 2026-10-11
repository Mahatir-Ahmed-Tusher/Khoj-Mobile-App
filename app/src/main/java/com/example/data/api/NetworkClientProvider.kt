package com.example.data.api

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Centralized, production-grade OkHttpClient provider.
 * Uses a single shared ConnectionPool and Dispatcher to reuse TCP/TLS sockets,
 * while providing customized timeouts for specialized services (LLM/OCR, Web Search, Deep Search, Supabase).
 */
object NetworkClientProvider {

    private val connectionPool = ConnectionPool(
        maxIdleConnections = 8,
        keepAliveDuration = 5,
        timeUnit = TimeUnit.MINUTES
    )

    private val dispatcher = Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 8
    }

    /**
     * Shared root OkHttpClient instance.
     */
    val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Specialized client for LLM inference, Vision AI, and OCR requests (60s timeouts).
     */
    val ocrAndLlmClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Specialized client for fast Web Search queries (8s timeouts).
     */
    val webSearchClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Specialized client for Deep Search operations (15s timeouts).
     */
    val deepSearchClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Specialized client for Supabase RPC & REST operations (25s timeouts).
     */
    val supabaseHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
