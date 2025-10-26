package ani.dantotsu.connections.simkl

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Simkl API - ANIME ONLY
 * (Simkl doesn\'t have manga database)
 */
object SimklQueries {

    private const val BASE_URL = "https://api.simkl.com"
    private const val TAG = "SimklQueries"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    // Cache for AniList ID → Simkl ID mapping
    private val idCache = ConcurrentHashMap<Int, CacheEntry>()
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

    private data class CacheEntry(
        val simklId: String?,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isValid(): Boolean = System.currentTimeMillis() - timestamp < CACHE_TTL_MS
    }

    /**
     * Map AniList anime status to Simkl status
     */
    private fun mapAnimeStatusToSimkl(anilistStatus: String?): String {
        return when (anilistStatus?.uppercase()) {
            "CURRENT", "WATCHING" -> "watching"
            "COMPLETED" -> "completed"
            "PAUSED", "HOLD" -> "hold"
            "DROPPED" -> "dropped"
            "PLANNING", "PLAN_TO_WATCH" -> "plantowatch"
            else -> "watching"
        }
    }

    /**
     * Map Simkl status to AniList anime status
     */
    private fun mapSimklStatusToAniList(simklStatus: String?): String {
        return when (simklStatus?.lowercase()) {
            "watching" -> "CURRENT"
            "completed" -> "COMPLETED"
            "hold" -> "PAUSED"
            "dropped" -> "DROPPED"
            "plantowatch" -> "PLANNING"
            else -> "CURRENT"
        }
    }

    /**
     * Get Simkl anime ID from AniList anime ID
     */
    suspend fun getSimklIdFromAniList(anilistAnimeId: Int): String? {
        // Check cache first
        idCache[anilistAnimeId]?.let { cached ->
            if (cached.isValid()) {
                Log.d(TAG, "Cache hit: AniList $anilistAnimeId → Simkl ${cached.simklId}")
                return cached.simklId
            } else {
                idCache.remove(anilistAnimeId)
            }
        }

        return try {
            val url = "$BASE_URL/search/id?anilist=$anilistAnimeId&client_id=${Simkl.CLIENT_ID}"
            val response: HttpResponse = client.get(url)

            if (response.status != HttpStatusCode.OK) {
                Log.w(TAG, "Simkl search failed: ${response.status}")
                return null
            }

            val body = response.bodyAsText()
            val jsonArray = JSONArray(body)

            if (jsonArray.length() == 0) {
                Log.w(TAG, "No Simkl anime found for AniList ID: $anilistAnimeId")
                idCache[anilistAnimeId] = CacheEntry(null)
                return null
            }

            val firstResult = jsonArray.getJSONObject(0)
            val idsObject = firstResult.optJSONObject("ids")
            val simklId = idsObject?.optString("simkl")

            // Cache result
            idCache[anilistAnimeId] = CacheEntry(simklId)

            Log.d(TAG, "Mapped: AniList $anilistAnimeId → Simkl $simklId")
            return simklId

        } catch (e: Exception) {
            Log.e(TAG, "Error mapping anime ID: ${e.message}", e)
            null
        }
    }

    /**
     * Update anime episode progress on Simkl
     */
    suspend fun updateAnimeProgress(
        token: String,
        anilistAnimeId: Int,
        episode: Int,
        status: String? = null,
        score: Int? = null
    ): Boolean {
        return try {
            val simklId = getSimklIdFromAniList(anilistAnimeId)
            if (simklId == null) {
                Log.w(TAG, "Cannot update - no Simkl ID for AniList anime $anilistAnimeId")
                return false
            }

            // Step 1: Add to list with status/score if provided
            if (status != null || score != null) {
                addAnimeToList(token, simklId, status, score, episode)
            }

            // Step 2: Mark episodes as watched
            if (episode > 0) {
                markEpisodesWatched(token, simklId, episode)
            }

            Log.i(TAG, "✅ Anime synced: AniList $anilistAnimeId → Ep $episode, Status: $status, Score: $score")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error updating anime: ${e.message}", e)
            false
        }
    }

    /**
     * Add anime to user\'s list
     */
    private suspend fun addAnimeToList(
        token: String,
        simklId: String,
        status: String?,
        score: Int?,
        episodes: Int?
    ): Boolean {
        return try {
            val url = "$BASE_URL/sync/add-to-list"

            val showObject = JSONObject().apply {
                put("ids", JSONObject().put("simkl", simklId))
                status?.let { put("status", mapAnimeStatusToSimkl(it)) }
                score?.let { put("rating", it) }
                episodes?.let { put("watched_episodes_count", it) }
            }

            val requestBody = JSONObject().apply {
                put("shows", JSONArray().put(showObject))
            }

            val response: HttpResponse = client.post(url) {
                header("Authorization", "Bearer $token")
                header("simkl-api-key", Simkl.CLIENT_ID)
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            response.status == HttpStatusCode.OK

        } catch (e: Exception) {
            Log.e(TAG, "Error adding to list: ${e.message}", e)
            false
        }
    }

    /**
     * Mark episodes 1 through {episode} as watched
     */
    private suspend fun markEpisodesWatched(
        token: String,
        simklId: String,
        upToEpisode: Int
    ): Boolean {
        return try {
            val url = "$BASE_URL/sync/history"

            // Mark all episodes up to current as watched
            val episodesArray = JSONArray()
            for (ep in 1..upToEpisode) {
                episodesArray.put(JSONObject().apply {
                    put("watched_at", getCurrentTimestamp())
                    put("ids", JSONObject().put("simkl", simklId))
                    put("episode", ep)
                })
            }

            val requestBody = JSONObject().apply {
                put("shows", JSONArray())
                put("episodes", episodesArray)
            }

            val response: HttpResponse = client.post(url) {
                header("Authorization", "Bearer $token")
                header("simkl-api-key", Simkl.CLIENT_ID)
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            response.status == HttpStatusCode.OK

        } catch (e: Exception) {
            Log.e(TAG, "Error marking episodes: ${e.message}", e)
            false
        }
    }

    /**
     * Remove anime from user\'s list
     */
    suspend fun removeAnime(
        token: String,
        anilistAnimeId: Int
    ): Boolean {
        return try {
            val simklId = getSimklIdFromAniList(anilistAnimeId) ?: return false

            val url = "$BASE_URL/sync/remove-from-list"

            val requestBody = JSONObject().apply {
                put("shows", JSONArray().put(JSONObject().apply {
                    put("ids", JSONObject().put("simkl", simklId))
                }))
            }

            val response: HttpResponse = client.post(url) {
                header("Authorization", "Bearer $token")
                header("simkl-api-key", Simkl.CLIENT_ID)
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val success = response.status == HttpStatusCode.OK
            if (success) {
                Log.i(TAG, "✅ Removed anime from Simkl: AniList $anilistAnimeId")
            }
            success

        } catch (e: Exception) {
            Log.e(TAG, "Error removing anime: ${e.message}", e)
            false
        }
    }

    /**
     * Get user\'s anime list from Simkl
     */
    suspend fun getUserAnimeList(token: String): List<SimklAnimeEntry> {
        return try {
            val url = "$BASE_URL/sync/all-items/anime?extended=full"
            val response: HttpResponse = client.get(url) {
                header("Authorization", "Bearer $token")
                header("simkl-api-key", Simkl.CLIENT_ID)
            }

            if (response.status != HttpStatusCode.OK) {
                Log.w(TAG, "Failed to get anime list: ${response.status}")
                return emptyList()
            }

            val body = response.bodyAsText()
            val jsonObject = JSONObject(body)
            val result = mutableListOf<SimklAnimeEntry>()

            // Parse different status lists
            listOf("watching", "completed", "hold", "dropped", "plantowatch").forEach { status ->
                val statusArray = jsonObject.optJSONArray(status) ?: return@forEach

                for (i in 0 until statusArray.length()) {
                    try {
                        val item = statusArray.getJSONObject(i)
                        val show = item.optJSONObject("show") ?: continue
                        val ids = show.optJSONObject("ids")

                        result.add(SimklAnimeEntry(
                            simklId = ids?.optString("simkl") ?: "",
                            anilistId = ids?.optInt("anilist") ?: 0,
                            title = show.optString("title", "Unknown"),
                            status = status,
                            watchedEpisodes = item.optInt("watched_episodes_count", 0),
                            totalEpisodes = show.optInt("total_episodes", 0),
                            rating = item.optInt("user_rating", 0),
                            lastWatchedAt = item.optString("last_watched_at")
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing anime entry: ${e.message}")
                    }
                }
            }

            Log.i(TAG, "✅ Retrieved ${result.size} anime from Simkl")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Error getting anime list: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Batch update multiple anime (for export)
     */
    suspend fun batchUpdateAnime(
        token: String,
        updates: List<AnimeUpdate>
    ): Boolean {
        return try {
            val showsArray = JSONArray()

            updates.forEach { update ->
                val simklId = getSimklIdFromAniList(update.anilistId)
                if (simklId != null) {
                    showsArray.put(JSONObject().apply {
                        put("ids", JSONObject().put("simkl", simklId))
                        update.status?.let { put("status", mapAnimeStatusToSimkl(it)) }
                        update.score?.let { put("rating", it) }
                    })
                }
            }

            if (showsArray.length() == 0) {
                Log.w(TAG, "No anime to batch update")
                return false
            }

            val url = "$BASE_URL/sync/add-to-list"
            val requestBody = JSONObject().apply {
                put("shows", showsArray)
            }

            val response: HttpResponse = client.post(url) {
                header("Authorization", "Bearer $token")
                header("simkl-api-key", Simkl.CLIENT_ID)
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val success = response.status == HttpStatusCode.OK
            if (success) {
                Log.i(TAG, "✅ Batch updated ${updates.size} anime")
            }
            success

        } catch (e: Exception) {
            Log.e(TAG, "Batch update error: ${e.message}", e)
            false
        }
    }

    fun clearCache() {
        idCache.clear()
        Log.d(TAG, "ID cache cleared")
    }

    private fun getCurrentTimestamp(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())
    }

    suspend fun getAccessToken(code: String): String {
        val url = "$BASE_URL/oauth/token"
        val requestBody = JSONObject().apply {
            put("code", code)
            put("client_id", Simkl.CLIENT_ID)
            put("client_secret", SimklCredentials.CLIENT_SECRET)
            put("grant_type", "authorization_code")
            put("redirect_uri", SimklAuth.REDIRECT_URI)
        }

        val response: HttpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        if (response.status != HttpStatusCode.OK) {
            throw Exception("Failed to get access token: ${response.status}")
        }

        val body = response.bodyAsText()
        val jsonObject = JSONObject(body)
        return jsonObject.getString("access_token")
    }
}

/**
 * Simkl anime entry from user\'s list
 */
data class SimklAnimeEntry(
    val simklId: String,
    val anilistId: Int,
    val title: String,
    val status: String,
    val watchedEpisodes: Int,
    val totalEpisodes: Int,
    val rating: Int,
    val lastWatchedAt: String?
)

/**
 * Anime update data for batch operations
 */
data class AnimeUpdate(
    val anilistId: Int,
    val status: String? = null,
    val score: Int? = null
)
