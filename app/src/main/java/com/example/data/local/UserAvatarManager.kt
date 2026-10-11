package com.example.data.local

import android.content.Context
import com.example.data.model.SupabaseUser
import java.net.URLEncoder

object UserAvatarManager {
    private const val PREFS_NAME = "user_avatars_prefs"
    private const val KEY_PREFIX = "custom_avatar_"
    private const val KEY_STYLE_PREFIX = "avatar_style_"
    private const val KEY_SEED_PREFIX = "avatar_seed_"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCustomAvatar(context: Context, userId: String, avatarUrl: String, style: String? = null, seed: String? = null) {
        if (userId.isBlank()) return
        getPrefs(context).edit().apply {
            putString("${KEY_PREFIX}$userId", avatarUrl)
            if (style != null) putString("${KEY_STYLE_PREFIX}$userId", style)
            if (seed != null) putString("${KEY_SEED_PREFIX}$userId", seed)
            apply()
        }
    }

    fun getCustomAvatar(context: Context, userId: String): String? {
        if (userId.isBlank()) return null
        return getPrefs(context).getString("${KEY_PREFIX}$userId", null)
            ?.takeIf { it.isNotBlank() && it != "null" }
    }

    fun deleteCustomAvatar(context: Context, userId: String) {
        if (userId.isBlank()) return
        getPrefs(context).edit().apply {
            remove("${KEY_PREFIX}$userId")
            remove("${KEY_STYLE_PREFIX}$userId")
            remove("${KEY_SEED_PREFIX}$userId")
            apply()
        }
    }

    fun hasCustomAvatar(context: Context, userId: String): Boolean {
        return !getCustomAvatar(context, userId).isNullOrBlank()
    }

    fun getDefaultEmailAvatarUrl(email: String?, style: String = "lorelei"): String {
        val safeSeed = try {
            URLEncoder.encode(email?.trim()?.lowercase()?.ifBlank { "default_user" } ?: "default_user", "UTF-8")
        } catch (e: Exception) {
            "default_user"
        }
        return "https://api.dicebear.com/10.x/$style/svg?seed=$safeSeed"
    }

    fun getEffectiveAvatarUrl(context: Context, user: SupabaseUser?): String {
        val userId = user?.id.orEmpty()
        if (userId.isNotBlank()) {
            val custom = getCustomAvatar(context, userId)
            if (!custom.isNullOrBlank()) {
                return custom
            }
        }

        val authPhoto = user?.photoUrl
        if (!authPhoto.isNullOrBlank() && authPhoto != "null") {
            return authPhoto
        }

        return getDefaultEmailAvatarUrl(user?.email)
    }
}
