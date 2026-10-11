package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "web_search_history")
data class WebSearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long
)
