package com.example.data.api

import com.example.data.model.EnsembleResult

sealed class AiImageDetectionProgress {
    object Idle : AiImageDetectionProgress()
    object PreparingImage : AiImageDetectionProgress()
    object RunningEngines : AiImageDetectionProgress()
    data class Success(val result: EnsembleResult) : AiImageDetectionProgress()
    data class Error(val message: String) : AiImageDetectionProgress()
}
