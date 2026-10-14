package com.example.data.repository

import com.example.data.api.KhojSupabaseClient
import com.example.data.model.UserSearchHistory
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
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
            
            // Delete post interactions first to satisfy FK constraints
            if (!reportId.isNullOrBlank()) {
                runCatching {
                    client.postgrest["post_likes"].delete { filter { eq("post_id", reportId) } }
                }
                runCatching {
                    client.postgrest["post_comments"].delete { filter { eq("post_id", reportId) } }
                }
            }
            runCatching {
                client.postgrest["post_likes"].delete { filter { eq("post_id", id) } }
            }
            runCatching {
                client.postgrest["post_comments"].delete { filter { eq("post_id", id) } }
            }

            // 1. Delete search history entry (match by id, report_id, or query)
            runCatching {
                client.postgrest["user_search_history"]
                    .delete {
                        filter {
                            eq("id", id)
                            eq("user_id", userId)
                        }
                    }
            }
            if (!reportId.isNullOrBlank()) {
                runCatching {
                    client.postgrest["user_search_history"]
                        .delete {
                            filter {
                                eq("report_id", reportId)
                                eq("user_id", userId)
                            }
                        }
                }
            }
            if (query.isNotBlank()) {
                runCatching {
                    client.postgrest["user_search_history"]
                        .delete {
                            filter {
                                eq("query", query)
                                eq("user_id", userId)
                            }
                        }
                }
            }

            // 2. Erase from factcheck_reports and deep_search_reports
            val targetIds = listOfNotNull(reportId, id).distinct()
            targetIds.forEach { tid ->
                runCatching {
                    client.postgrest["factcheck_reports"].delete {
                        filter {
                            eq("id", tid)
                            eq("user_id", userId)
                        }
                    }
                }
                runCatching {
                    client.postgrest["deep_search_reports"].delete {
                        filter {
                            eq("id", tid)
                            eq("user_id", userId)
                        }
                    }
                }
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
