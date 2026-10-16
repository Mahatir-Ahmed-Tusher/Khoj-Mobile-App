package com.example.data.api

import com.example.data.model.RumorClaimsResponse
import com.example.data.model.RumorHotspotsResponse
import com.example.data.model.RumorMediaSpreadResponse
import com.example.data.model.RumorMeterResponse
import com.example.data.model.RumorNarrativesResponse
import com.example.data.model.RumorTrendsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface RumorMeterApi {

    @GET("api/rumormeter/meter")
    suspend fun getMeterStats(): RumorMeterResponse

    @GET("api/rumormeter/trends")
    suspend fun getTrends(): RumorTrendsResponse

    @GET("api/rumormeter/trends/narratives")
    suspend fun getNarratives(
        @Query("limit") limit: Int = 15
    ): RumorNarrativesResponse

    @GET("api/rumormeter/trends/hotspots")
    suspend fun getHotspots(): RumorHotspotsResponse

    @GET("api/rumormeter/trends/media")
    suspend fun getMediaSpread(): RumorMediaSpreadResponse

    @GET("api/rumormeter/claims")
    suspend fun getClaims(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("q") query: String? = null,
        @Query("topic") topic: String? = null,
        @Query("zombie") zombie: Boolean? = null,
        @Query("cross_border") crossBorder: Boolean? = null,
        @Query("gender_targeted") genderTargeted: Boolean? = null
    ): RumorClaimsResponse
}
