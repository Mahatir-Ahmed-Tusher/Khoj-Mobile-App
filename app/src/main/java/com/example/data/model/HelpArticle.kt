package com.example.data.model

data class HelpArticle(
    val id: String,
    val titleEn: String,
    val titleBn: String,
    val contentEn: String,
    val contentBn: String,
    val tags: List<String> = emptyList()
)
