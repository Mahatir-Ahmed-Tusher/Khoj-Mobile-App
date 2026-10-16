package com.example.data.repository

import com.example.data.api.KhojSupabaseClient
import com.example.data.model.UserSearchHistory
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.put

object ProfileRepository {
    private val client get() = KhojSupabaseClient.client

    suspend fun fetchSearchHistory(userId: String): Result<List<UserSearchHistory>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            client.postgrest["user_search_history"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order("created_at", order = Order.DESCENDING)
                    limit(50)
                }
                .decodeList<UserSearchHistory>()
        }
    }

    suspend fun deleteHistoryItem(id: String, query: String, reportId: String?): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val userId = AuthRepository.currentUserId ?: throw Exception("User not authenticated")
            val targetIds = listOfNotNull(reportId, id).distinct()

            kotlinx.coroutines.coroutineScope {
                val tasks = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

                // 1. Delete associated likes and comments concurrently
                targetIds.forEach { tid ->
                    tasks += async {
                        runCatching {
                            client.postgrest["post_likes"].delete { filter { eq("post_id", tid) } }
                        }
                        Unit
                    }
                    tasks += async {
                        runCatching {
                            client.postgrest["post_comments"].delete { filter { eq("post_id", tid) } }
                        }
                        Unit
                    }
                }

                // 2. Delete user search history entries concurrently
                tasks += async {
                    runCatching {
                        client.postgrest["user_search_history"].delete {
                            filter {
                                eq("id", id)
                                eq("user_id", userId)
                            }
                        }
                    }
                    Unit
                }
                if (!reportId.isNullOrBlank()) {
                    tasks += async {
                        runCatching {
                            client.postgrest["user_search_history"].delete {
                                filter {
                                    eq("report_id", reportId)
                                    eq("user_id", userId)
                                }
                            }
                        }
                        Unit
                    }
                }
                if (query.isNotBlank()) {
                    tasks += async {
                        runCatching {
                            client.postgrest["user_search_history"].delete {
                                filter {
                                    eq("query", query)
                                    eq("user_id", userId)
                                }
                            }
                        }
                        Unit
                    }
                }

                // 3. Delete from factcheck_reports and deep_search_reports concurrently
                targetIds.forEach { tid ->
                    tasks += async {
                        runCatching {
                            client.postgrest["factcheck_reports"].delete {
                                filter {
                                    eq("id", tid)
                                    eq("user_id", userId)
                                }
                            }
                        }
                        Unit
                    }
                    tasks += async {
                        runCatching {
                            client.postgrest["deep_search_reports"].delete {
                                filter {
                                    eq("id", tid)
                                    eq("user_id", userId)
                                }
                            }
                        }
                        Unit
                    }
                }

                tasks.awaitAll()
            }
            Unit
        }
    }

    suspend fun updateProfileName(userId: String, fullName: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            // 1. Update Auth user metadata
            client.auth.updateUser {
                data {
                    put("full_name", fullName)
                    put("display_name", fullName)
                }
            }
            // 2. Update profile users table in database
            client.postgrest["users"].update(
                mapOf("display_name" to fullName)
            ) {
                filter {
                    eq("id", userId)
                }
            }
            Unit
        }
    }
}
