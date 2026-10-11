package com.example.data.repository

import com.example.data.api.KhojSupabaseClient
import com.example.data.model.SupabaseUser
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

object AuthRepository {

    private val client get() = KhojSupabaseClient.client
    private val auth get() = client.auth
    private val db get() = client.postgrest

    // ── Current session state ─────────────────────────────────────────────────

    val sessionFlow: Flow<SessionStatus> get() = auth.sessionStatus

    val currentUserId: String? get() = auth.currentUserOrNull()?.id

    private fun JsonElement?.asSafeString(): String? {
        if (this == null) return null
        return try {
            this.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        } catch (e: Exception) {
            val s = this.toString().trim('"')
            if (s.isBlank() || s == "null") null else s
        }
    }

    fun getCurrentUser(): SupabaseUser? {
        val user = auth.currentUserOrNull() ?: return null
        val meta = user.userMetadata
        
        val displayName = meta?.get("full_name").asSafeString()
            ?: meta?.get("display_name").asSafeString()
            ?: user.email?.substringBefore("@")
            ?: "User"
            
        val photoUrl = meta?.get("avatar_url").asSafeString()
            ?: meta?.get("photo_url").asSafeString()
            
        return SupabaseUser(
            id = user.id,
            email = user.email,
            displayName = displayName,
            photoUrl = photoUrl
        )
    }

    // ── Sign up with email ────────────────────────────────────────────────────

    suspend fun signUp(email: String, password: String, displayName: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            // Upsert profile row
            upsertProfile(displayName = displayName, email = email, photoUrl = null)
        }
    }

    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000L,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e
                // Don't retry invalid credentials or user cancelled
                val msg = e.message?.lowercase() ?: ""
                if (msg.contains("invalid") || msg.contains("credentials") || msg.contains("wrong password") || msg.contains("user not found")) {
                    throw e
                }
                if (attempt < maxAttempts) {
                    android.util.Log.w("AuthRepository", "Auth operation attempt $attempt failed ($e), retrying in ${currentDelay}ms...")
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay *= 2
                }
            }
        }
        throw lastException ?: Exception("Authentication failed after $maxAttempts attempts")
    }

    // ── Sign in with email ────────────────────────────────────────────────────

    suspend fun signIn(email: String, password: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            retryWithBackoff {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }
            val user = auth.currentUserOrNull() ?: return@runCatching
            upsertProfile(
                displayName = user.userMetadata?.get("display_name").asSafeString(),
                email = email,
                photoUrl = null
            )
        }
    }

    // ── Sign in with Google (IdToken) ─────────────────────────────────────────

    suspend fun signInWithGoogle(idTokenString: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            retryWithBackoff {
                auth.signInWith(io.github.jan.supabase.auth.providers.builtin.IDToken) {
                    provider = io.github.jan.supabase.auth.providers.Google
                    idToken = idTokenString
                }
            }
            val user = auth.currentUserOrNull() ?: return@runCatching
            upsertProfile(
                displayName = user.userMetadata?.get("full_name").asSafeString() ?: "Google User",
                email = user.email ?: "",
                photoUrl = user.userMetadata?.get("avatar_url").asSafeString()
            )
            Unit
        }
    }

    @Volatile
    private var isProfileUpsertedInMemory: Boolean = false

    fun isProfileUpserted(): Boolean = isProfileUpsertedInMemory

    // ── Sign out ──────────────────────────────────────────────────────────────

    suspend fun signOut(): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            isProfileUpsertedInMemory = false
            auth.signOut()
        }
    }

    // ── Fetch profile ─────────────────────────────────────────────────────────

    suspend fun getProfile(userId: String): SupabaseUser? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            db["users"].select(Columns.ALL) {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<SupabaseUser>()
        }.getOrNull()
    }

    // ── Upsert profile on login ───────────────────────────────────────────────

    suspend fun upsertProfile(displayName: String?, email: String, photoUrl: String?, force: Boolean = false) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val userId = auth.currentUserOrNull()?.id ?: return@withContext
        if (isProfileUpsertedInMemory && !force) return@withContext

        val name = displayName ?: email.substringBefore("@")
        
        try {
            // Try standard upsert (works for new users or already migrated users)
            db["users"].upsert(
                mapOf(
                    "id" to userId,
                    "email" to email,
                    "display_name" to name,
                    "photo_url" to photoUrl
                )
            ) { onConflict = "id" }
            isProfileUpsertedInMemory = true
        } catch (e: Exception) {
            // Email collision: Old Clerk user might exist with the same email
            runCatching {
                val existingUser = db["users"].select(Columns.ALL) {
                    filter { eq("email", email) }
                }.decodeSingleOrNull<SupabaseUser>()

                if (existingUser != null && existingUser.id != userId) {
                    // 1. Update foreign keys to point to the new Supabase UUID
                    runCatching { 
                        db["factcheck_reports"].update(mapOf("user_id" to userId)) { filter { eq("user_id", existingUser.id) } } 
                    }
                    runCatching { 
                        db["user_search_history"].update(mapOf("user_id" to userId)) { filter { eq("user_id", existingUser.id) } } 
                    }
                    
                    // 2. Delete the old row (Clerk ID)
                    db["users"].delete { filter { eq("id", existingUser.id) } }
                    
                    // 3. Insert the new row with Supabase UUID
                    db["users"].insert(
                        mapOf(
                            "id" to userId,
                            "email" to email,
                            "display_name" to (name.takeIf { it != email.substringBefore("@") } ?: existingUser.displayName ?: name),
                            "photo_url" to (photoUrl ?: existingUser.photoUrl),
                            "role" to existingUser.role,
                            "account_status" to existingUser.accountStatus
                        )
                    )
                }
                isProfileUpsertedInMemory = true
            }.onFailure { migrationError ->
                android.util.Log.e("AuthRepository", "Failed to migrate Clerk user profile: ${migrationError.message}", migrationError)
                throw migrationError // Rethrow so caller knows it failed
            }
        }
        Unit
    }
}
