package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TrendingRumour(
    @Json(name = "source") val source: String,
    @Json(name = "title") val title: String,
    @Json(name = "url") val url: String,
    @Json(name = "published_at") val publishedAt: String?
)
