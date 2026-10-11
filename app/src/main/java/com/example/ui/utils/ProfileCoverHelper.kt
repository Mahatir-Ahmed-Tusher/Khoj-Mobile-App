package com.example.ui.utils

import com.example.R
import kotlin.math.abs

val ALL_COVER_RESOURCES = listOf(
    R.drawable.cover_1,
    R.drawable.cover_2,
    R.drawable.cover_3,
    R.drawable.cover_4,
    R.drawable.cover_5
)

fun getProfileCoverResource(userId: String?, customIndex: Int? = null): Int {
    if (customIndex != null && customIndex in ALL_COVER_RESOURCES.indices) {
        return ALL_COVER_RESOURCES[customIndex]
    }
    if (userId == null) return R.drawable.cover_1

    val coverIndex = abs(userId.hashCode()) % ALL_COVER_RESOURCES.size
    return ALL_COVER_RESOURCES[coverIndex]
}

