package com.example.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class TrendingRumourUi(
    val source: String,
    val title: String,
    val url: String,
    val timestamp: String?,
    val thumbnail: String? = null
)
