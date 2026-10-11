package com.example.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ResponseLanguageManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("khoj_settings", Context.MODE_PRIVATE)

    private val _responseLanguage = MutableStateFlow(loadPreference())
    val responseLanguage: StateFlow<ResponseLanguage> = _responseLanguage.asStateFlow()

    fun setLanguage(language: ResponseLanguage) {
        _responseLanguage.value = language
        prefs.edit().putString("response_language_option", language.name).apply()
    }

    private fun loadPreference(): ResponseLanguage {
        val saved = prefs.getString("response_language_option", ResponseLanguage.AUTO.name)
        return try {
            ResponseLanguage.valueOf(saved ?: ResponseLanguage.AUTO.name)
        } catch (e: IllegalArgumentException) {
            ResponseLanguage.AUTO
        }
    }

    // A helper function to build the language instruction prompt based on the chosen mode.
    // This centralizes the prompt injection logic.
    fun getLanguageInstructionPrompt(): String {
        return when (_responseLanguage.value) {
            ResponseLanguage.AUTO -> {
                """
                CRITICAL LANGUAGE RULES:
                The selected response language mode must be strictly enforced.

                AUTO MODE:
                1. If the user primarily writes in Bengali script, generate the response entirely in Bengali script.
                2. If the user primarily writes in Banglish (Bengali written using Latin characters), generate the response entirely in Bengali script.
                3. If the user primarily writes in English, generate the response entirely in English.
                4. Ignore isolated words, names, technical terms, URLs, product names, and code snippets when determining the dominant language.
                5. Never mix Bengali and English in the same response unless absolutely necessary.
                6. Maintain the selected language consistently throughout the entire response.
                """.trimIndent()
            }
            ResponseLanguage.BANGLA -> {
                """
                CRITICAL LANGUAGE RULES:
                You MUST generate the ENTIRE response in Bengali (Bangla script), regardless of the user's input language. Never mix Bengali and English in the same response unless absolutely necessary (like technical terms or URLs).
                """.trimIndent()
            }
            ResponseLanguage.ENGLISH -> {
                """
                CRITICAL LANGUAGE RULES:
                You MUST generate the ENTIRE response in English, regardless of the user's input language.
                """.trimIndent()
            }
        }
    }
}
