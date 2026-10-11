package com.example.data.api

import com.example.data.model.TrendingRumour
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SupabaseApi {
    @GET("rest/v1/trending_rumours")
    suspend fun getTrendingRumours(
        @Header("apikey") apiKey: String,
        @Query("select") select: String = "source,title,url,published_at",
        @Query("order") order: String = "fetched_at.desc",
        @Query("limit") limit: Int = 6
    ): List<TrendingRumour>
}
