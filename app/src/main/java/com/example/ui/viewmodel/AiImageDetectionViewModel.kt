package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.AiImageDetectionEngine
import com.example.data.api.AiImageDetectionProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiImageDetectionViewModel(application: Application) : AndroidViewModel(application) {

    // Engine is instantiated with the Application context so it survives configuration changes
    private val engine = AiImageDetectionEngine(application)

    private val _detectionState =
        MutableStateFlow<AiImageDetectionProgress>(AiImageDetectionProgress.Idle)
    val detectionState: StateFlow<AiImageDetectionProgress> = _detectionState.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    /**
     * Called when the user picks an image from the gallery.
     * Resets state to Idle so the Analyze button becomes enabled again
     * even if a previous result was showing.
     */
    fun setSelectedImage(uri: Uri) {
        _selectedImageUri.value = uri
        _detectionState.value = AiImageDetectionProgress.Idle
    }

    /**
     * Launches the detection pipeline via the central Khoj AI Image Detection Gateway.
     * PreparingImage → RunningEngines → Success | Error
     */
    fun analyzeImage(uri: Uri) {
        viewModelScope.launch {
            _detectionState.value = AiImageDetectionProgress.PreparingImage
            try {
                _detectionState.value = AiImageDetectionProgress.RunningEngines
                val result = engine.detect(imageUri = uri)
                _detectionState.value = AiImageDetectionProgress.Success(result)
            } catch (e: Exception) {
                _detectionState.value = AiImageDetectionProgress.Error(
                    e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    /** Resets state to Idle without clearing the selected image. */
    fun resetState() {
        _detectionState.value = AiImageDetectionProgress.Idle
    }

    /** Clears both the selected image and the detection state. */
    fun clearImage() {
        _selectedImageUri.value = null
        _detectionState.value = AiImageDetectionProgress.Idle
    }
}
