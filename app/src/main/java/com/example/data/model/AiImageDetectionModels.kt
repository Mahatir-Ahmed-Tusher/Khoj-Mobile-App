package com.example.data.model

data class EngineResult(
    val engineName: String,       // e.g. "Sightengine GenAI", "Khoj AI Image Detector", "Metadata Analyzer"
    val modelId: String,          // e.g. "sightengine/genai", "khoj/ai-vs-human", "local/metadata"
    val architecture: String,     // e.g. "Commercial REST API", "Cloud Run REST API", "Local Heuristic Engine"
    val weight: Float,
    val aiScore: Float,           // 0.0f–100.0f
    val humanScore: Float,        // 0.0f–100.0f
    val verdict: EngineVerdict,
    val error: String? = null,
    val details: String? = null
)

enum class EngineVerdict { AI, HUMAN, ERROR }

data class EnsembleResult(
    val verdict: ImageVerdict,
    val confidence: Float,        // 0.0f–100.0f
    val aiScore: Float,
    val humanScore: Float,
    val engines: List<EngineResult>,
    val successfulEngines: Int,
    val totalEngines: Int,
    val aiVotes: Int,
    val humanVotes: Int,
    val majorityAgreement: Int,   // 0–100 percentage integer
    val consensusLevel: ConsensusLevel,
    val analysisTimeMs: Long
)

enum class ImageVerdict { AI, HUMAN, UNCERTAIN }
enum class ConsensusLevel { STRONG, MODERATE, WEAK, SINGLE_ENGINE }
