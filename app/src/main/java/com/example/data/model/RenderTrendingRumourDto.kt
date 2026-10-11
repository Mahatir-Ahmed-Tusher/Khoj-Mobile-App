package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RenderTrendingRumourDto(
    @Json(name = "source") val source: String,
    @Json(name = "title") val title: String,
    @Json(name = "url") val url: String,
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "fetched_at") val fetchedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class TrendingRumoursResponse(
    @Json(name = "region") val region: String,
    @Json(name = "count") val count: Int,
    @Json(name = "rumours") val rumours: List<RenderTrendingRumourDto>
)
