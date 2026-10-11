package com.example.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "OverlayVoiceManager"

/**
 * Voice-to-text manager for the overlay context.
 *
 * This is a thin adaptation of the existing [com.example.ui.screens.VoiceInputManager] for use
 * inside [OverlayModeService]. The key difference is that [SpeechRecognizer] must be created
 * and used on the **main thread** — even from a Service — so we enforce that via a
 * [Handler(Looper.getMainLooper())].
 *
 * Lifecycle: create one instance per OverlayModeService lifetime. Call [destroy] when the
 * service is stopped.
 */
class OverlayVoiceManager(private val context: Context) {

    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startListening(
        isBn: Boolean,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (errorKey: String) -> Unit
    ) {
        if (!isAvailable) {
            onError("voice_not_available")
            return
        }

        stopListening()

        mainHandler.post {
            try {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        // Let onResults/onError drive the state; do not reset here.
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> { /* Silent reset — expected */ }

                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> onError("voice_network_error")

                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onError("voice_perm_required")

                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> stopListening()

                            else -> { /* Silent reset */ }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull()
                        if (!spoken.isNullOrBlank()) onFinalResult(spoken)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()
                        if (!partial.isNullOrBlank()) onPartialResult(partial)
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
                    putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 1500L)
                    putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 2000L)
                    putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 3000L)
                }

                recognizer.startListening(intent)
                _isListening.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer: ${e.message}", e)
                _isListening.value = false
                onError("voice_not_available")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            } finally {
                speechRecognizer = null
                _isListening.value = false
            }
        }
    }

    fun destroy() {
        stopListening()
    }
}
