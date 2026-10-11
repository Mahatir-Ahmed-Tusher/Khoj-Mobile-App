package com.example.data.api

import com.example.BuildConfig

object SearxngHelper {
    val baseUrls: List<String>
        get() {
            val urls = mutableListOf<String>()
            
            try { urls.add(BuildConfig.SEARXNG_BASE_URL) } catch (e: Exception) {}
            try { urls.add(BuildConfig.SEARXNG_BASE_URL_2) } catch (e: Exception) {}
            try { urls.add(BuildConfig.SEARXNG_BASE_URL_3) } catch (e: Exception) {}
            try { urls.add(BuildConfig.SEARXNG_BASE_URL_4) } catch (e: Exception) {}
            
            return urls
                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                .filter { it.isNotBlank() && it.startsWith("http") }
                .map { it.removeSuffix("/") }
        }
}
