package ani.dantotsu.connections.simkl

import android.util.Log
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simkl Tracker - ANIME ONLY
 * Note: Simkl doesn\'t have manga database
 */
class Simkl {

    companion object {
        val CLIENT_ID = SimklCredentials.CLIENT_ID
        private const val TAG = "Simkl"

        @Volatile
        private var instance: Simkl? = null

        fun getInstance(): Simkl {
            return instance ?: synchronized(this) {
                instance ?: Simkl().also { instance = it }
            }
        }
    }

    val query = SimklQueries
    val auth = SimklAuth

    fun isLoggedIn(): Boolean {
        val token = PrefManager.getVal<String>(PrefName.SimklToken)
        return !token.isNullOrEmpty()
    }

    fun isEnabled(): Boolean {
        return PrefManager.getVal(PrefName.SimklEnabled, false)
    }

    fun setEnabled(enabled: Boolean) {
        PrefManager.setVal(PrefName.SimklEnabled, enabled)
        Log.d(TAG, "Simkl anime sync ${if (enabled) "enabled" else "disabled"}")
    }

    fun getToken(): String? {
        return PrefManager.getVal<String>(PrefName.SimklToken)
    }

    fun saveToken(token: String) {
        PrefManager.setVal(PrefName.SimklToken, token)
    }

    fun logout() {
        PrefManager.removeVal(PrefName.SimklToken)
        PrefManager.setVal(PrefName.SimklEnabled, false)
        SimklQueries.clearCache()
        Log.d(TAG, "Logged out from Simkl")
    }

    /**
     * Update anime episode progress
     * This is the main method called when user watches anime
     */
    suspend fun updateAnimeProgress(
        anilistAnimeId: Int,
        episode: Int,
        status: String? = null,
        score: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled()) {
            Log.d(TAG, "Simkl sync is disabled")
            return@withContext false
        }

        if (!isLoggedIn()) {
            Log.w(TAG, "Not logged in to Simkl")
            return@withContext false
        }

        try {
            val result = query.updateAnimeProgress(
                token = getToken()!!,
                anilistAnimeId = anilistAnimeId,
                episode = episode,
                status = status,
                score = score
            )

            if (result) {
                Log.i(TAG, "✅ Anime updated on Simkl")
            } else {
                Log.w(TAG, "❌ Failed to update anime on Simkl")
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "Error updating anime: ${e.message}", e)
            false
        }
    }

    /**
     * Update only anime status (Watching, Completed, etc.)
     */
    suspend fun updateAnimeStatus(
        anilistAnimeId: Int,
        status: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled() || !isLoggedIn()) return@withContext false

        try {
            query.updateAnimeProgress(
                token = getToken()!!,
                anilistAnimeId = anilistAnimeId,
                episode = 0,
                status = status,
                score = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating status: ${e.message}", e)
            false
        }
    }

    /**
     * Update only anime score/rating
     */
    suspend fun updateAnimeScore(
        anilistAnimeId: Int,
        score: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled() || !isLoggedIn()) return@withContext false

        try {
            query.updateAnimeProgress(
                token = getToken()!!,
                anilistAnimeId = anilistAnimeId,
                episode = 0,
                status = null,
                score = score
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating score: ${e.message}", e)
            false
        }
    }

    /**
     * Remove anime from Simkl list
     */
    suspend fun removeAnime(anilistAnimeId: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) return@withContext false

        try {
            query.removeAnime(getToken()!!, anilistAnimeId)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing anime: ${e.message}", e)
            false
        }
    }

    /**
     * Get user\'s full anime list from Simkl
     */
    suspend fun getUserAnimeList(): List<SimklAnimeEntry> {
        if (!isLoggedIn()) return emptyList()

        return try {
            query.getUserAnimeList(getToken()!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting anime list: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Export AniList anime to Simkl (batch)
     */
    suspend fun exportAnimeList(animeList: List<AnimeUpdate>): Boolean {
        if (!isLoggedIn()) return false

        return try {
            query.batchUpdateAnime(getToken()!!, animeList)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting anime: ${e.message}", e)
            false
        }
    }

    suspend fun getAccessToken(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = query.getAccessToken(code)
            saveToken(token)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get access token: ${e.message}", e)
            false
        }
    }
}
