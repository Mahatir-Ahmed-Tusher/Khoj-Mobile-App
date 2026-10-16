package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class RumorMeterResponse(
    val success: Boolean = false,
    val data: RumorMeterData? = null,
    val error: String? = null
)

@Serializable
@JsonClass(generateAdapter = true)
data class RumorMeterData(
    val meter: MeterInfo = MeterInfo(),
    val counts: MeterCounts = MeterCounts(),
    @SerialName("top_topics")
    @Json(name = "top_topics")
    val topTopics: List<TopicItem> = emptyList()
)

@Serializable
@JsonClass(generateAdapter = true)
data class MeterInfo(
    val score: Int = 0,
    val level: String = "NORMAL",
    @SerialName("daily_velocity")
    @Json(name = "daily_velocity")
    val dailyVelocity: Double = 0.0
)

@Serializable
@JsonClass(generateAdapter = true)
data class MeterCounts(
    val today: Int = 0,
    @SerialName("last_7_days")
    @Json(name = "last_7_days")
    val last7Days: Int = 0,
    @SerialName("last_30_days")
    @Json(name = "last_30_days")
    val last30Days: Int = 0,
    @SerialName("total_articles")
    @Json(name = "total_articles")
    val totalArticles: Int = 0,
    @SerialName("unique_claims")
    @Json(name = "unique_claims")
    val uniqueClaims: Int = 0,
    @SerialName("zombie_claims")
    @Json(name = "zombie_claims")
    val zombieClaims: Int = 0,
    @SerialName("cross_border_claims")
    @Json(name = "cross_border_claims")
    val crossBorderClaims: Int = 0,
    @SerialName("gender_targeted_claims")
    @Json(name = "gender_targeted_claims")
    val genderTargetedClaims: Int = 0
)

@Serializable
@JsonClass(generateAdapter = true)
data class TopicItem(
    @SerialName("primary_topic")
    @Json(name = "primary_topic")
    val primaryTopic: String = "",
    val count: String = "0",
    val percentage: String = "0"
)

@Serializable
@JsonClass(generateAdapter = true)
data class RumorTrendsResponse(
    val success: Boolean = false,
    val data: RumorTrendsData? = null,
    val error: String? = null
)

@Serializable
@JsonClass(generateAdapter = true)
data class RumorTrendsData(
    val topics: List<TopicItem> = emptyList(),
    val formats: List<FormatItem> = emptyList(),
    val techniques: List<TechniqueItem> = emptyList(),
    @SerialName("ai_subtypes")
    @Json(name = "ai_subtypes")
    val aiSubtypes: List<AiSubtypeItem> = emptyList()
)

@Serializable
@JsonClass(generateAdapter = true)
data class FormatItem(
    val format: String = "",
    val count: String = "0"
)

@Serializable
@JsonClass(generateAdapter = true)
data class TechniqueItem(
    val technique: String = "",
    val count: String = "0"
)

@Serializable
@JsonClass(generateAdapter = true)
data class AiSubtypeItem(
    @SerialName("ai_subtype")
    @Json(name = "ai_subtype")
    val aiSubtype: String = "",
    val count: String = "0"
)

@Serializable
@JsonClass(generateAdapter = true)
data class RumorNarrativesResponse(
    val success: Boolean = false,
    val data: List<NarrativeItem> = emptyList(),
    val error: String? = null
)

@Serializable
@JsonClass(generateAdapter = true)
data class NarrativeItem(
    val narrative: String = "",
    val count: Int = 0
)

@Serializable
@JsonClass(generateAdapter = true)
data class RumorHotspotsResponse(
    val success: Boolean = false,
    val data: List<DistrictHotspotItem> = emptyList(),
    val error: String? = null
)

@Serializable
@JsonClass(generateAdapter = true)
data class DistrictHotspotItem(
    val id: String = "",
    @SerialName("name_en")
    @Json(name = "name_en")
    val nameEn: String = "",
    @SerialName("name_bn")
    @Json(name = "name_bn")
    val nameBn: String = "",
    val division: String = "",
    @SerialName("rumor_count")
    @Json(name = "rumor_count")
    val rumorCount: String = "0"
)

@Serializable
@JsonClass(generateAdapter = true)
data class RumorMediaSpreadResponse(
    val success: Boolean = false,
    val data: List<MediaSpreadItem> = emptyList(),
    val error: String? = null
)

@Serializable
@JsonClass(generateAdapter = true)
data class MediaSpreadItem(
    @SerialName("media_name")
    @Json(name = "media_name")
    val mediaName: String = "",
    @SerialName("spread_count")
    @Json(name = "spread_count")
    val spreadCount: String = "0"
)

@Serializable
@JsonClass(generateAdapter = true)
data class RumorClaimsResponse(
    val success: Boolean = false,
    val data: List<CanonicalClaimItem> = emptyList(),
    val error: String? = null
)

@Serializable
@JsonClass(generateAdapter = true)
data class CanonicalClaimItem(
    val id: Long = 0L,
    @SerialName("canonical_claim")
    @Json(name = "canonical_claim")
    val canonicalClaim: String = "",
    @SerialName("canonical_claim_bn")
    @Json(name = "canonical_claim_bn")
    val canonicalClaimBn: String? = null,
    @SerialName("primary_topic")
    @Json(name = "primary_topic")
    val primaryTopic: String? = null,
    val format: String? = null,
    val technique: String? = null,
    @SerialName("article_count")
    @Json(name = "article_count")
    val articleCount: Int = 1,
    @SerialName("is_zombie")
    @Json(name = "is_zombie")
    val isZombie: Boolean = false,
    @SerialName("is_cross_border")
    @Json(name = "is_cross_border")
    val isCrossBorder: Boolean = false,
    @SerialName("is_gender_targeted")
    @Json(name = "is_gender_targeted")
    val isGenderTargeted: Boolean = false,
    @SerialName("first_detected_at")
    @Json(name = "first_detected_at")
    val firstDetectedAt: String? = null,
    @SerialName("last_detected_at")
    @Json(name = "last_detected_at")
    val lastDetectedAt: String? = null,
    @SerialName("fact_checks")
    @Json(name = "fact_checks")
    val factChecks: List<FactCheckReference> = emptyList()
)

@Serializable
@JsonClass(generateAdapter = true)
data class FactCheckReference(
    val id: Long = 0L,
    val title: String = "",
    val url: String = "",
    val rating: String = "",
    @SerialName("published_at")
    @Json(name = "published_at")
    val publishedAt: String? = null,
    val author: String? = null
)
