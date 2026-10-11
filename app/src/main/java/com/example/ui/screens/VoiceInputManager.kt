package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class VoiceInputManager(private val context: Context) {

    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    var isListening by mutableStateOf(false)
        private set

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening(
        isBn: Boolean,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (errorKey: String, isPermissionError: Boolean) -> Unit
    ) {
        if (!isAvailable) {
            onError("voice_not_available", false)
            return
        }

        stopListening()

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                }

                override fun onBeginningOfSpeech() {
                    isListening = true
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    // Audio capture ended — do NOT reset isListening here.
                    // Recognition engine is still processing; let onResults/onError drive state.
                }

                override fun onError(error: Int) {
                    isListening = false
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // Non-fatal: user didn't speak or spoke too briefly.
                            // Silently reset — the button returning to idle is clear feedback.
                        }
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                            onError("voice_network_error", false)
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            onError("voice_perm_required", true)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            // Force-destroy and reset so next tap creates a fresh instance.
                            stopListening()
                        }
                        else -> {
                            // All other errors: silent reset, don't annoy the user.
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull()
                    if (!spokenText.isNullOrBlank()) {
                        onFinalResult(spokenText)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull()
                    if (!partialText.isNullOrBlank()) {
                        onPartialResult(partialText)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val langCode = if (isBn) "bn-BD" else "en-US"
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, langCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Give the user time to start speaking before the engine gives up.
                // MINIMUM_LENGTH: won't return a result before 1.5s of audio.
                // POSSIBLY_COMPLETE_SILENCE: waits 2s of silence before checking if done.
                // COMPLETE_SILENCE: waits 3s of total silence before ending recognition.
                // These are hints to the engine — respected by Google's speech engine on most devices.
                putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 1500L)
                putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 2000L)
                putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 3000L)
            }

            recognizer.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            isListening = false
            onError("voice_not_available", false)
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
            isListening = false
        }
    }

    fun destroy() {
        stopListening()
    }
}
