package com.example.data.model

import android.net.Uri

enum class VerificationMode {
    FACT_CHECK,
    IMAGE_SEARCH,
    AI_DETECTION
}

sealed class PipelineState<out T> {
    object Idle : PipelineState<Nothing>()
    data class Loading(val message: String? = null) : PipelineState<Nothing>()
    data class Success<T>(val data: T) : PipelineState<T>()
    data class Error(val message: String) : PipelineState<Nothing>()
}

data class VerificationSession(
    val originalImageUri: Uri,
    val croppedImageUri: Uri? = null,
    val selectedModes: Set<VerificationMode>,
    val additionalContext: String? = null,
    
    val imageSearchState: PipelineState<ImageSearchResult> = PipelineState.Idle,
    val aiDetectionState: PipelineState<EnsembleResult> = PipelineState.Idle,
    val factCheckState: PipelineState<FactCheckReport> = PipelineState.Idle
)
