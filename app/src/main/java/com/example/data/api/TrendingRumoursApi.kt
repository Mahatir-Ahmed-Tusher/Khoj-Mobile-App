package com.example.data.api

import com.example.data.model.TrendingRumoursResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TrendingRumoursApi {
    @GET("trending")
    suspend fun getTrendingRumours(
        @Query("region") region: String // "bd" or "global"
    ): TrendingRumoursResponse
}
