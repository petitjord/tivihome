package com.petitjord.tivihome.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/** Point d'accès unique : compte, préférences, cache mémoire, favoris. */
object AppRepo {
    private lateinit var prefs: SharedPreferences

    var client: XtreamClient? = null
        private set

    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set

    /** "ts" (MPEG-TS) ou "m3u8" (HLS) pour la TV en direct. */
    var liveFormat by mutableStateOf("ts")
        private set

    private var liveCats: List<Category>? = null
    private var live: List<Channel>? = null
    private var vodCats: List<Category>? = null
    private var vod: List<Movie>? = null
    private var seriesCats: List<Category>? = null
    private var series: List<Series>? = null
    private val seriesInfos = ConcurrentHashMap<Int, SeriesInfo>()
    /** streamId -> (heure de chargement, (limite demandée, programmes)) */
    private val epg = ConcurrentHashMap<Int, Pair<Long, Pair<Int, List<Programme>>>>()

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.getSharedPreferences("tivihome", Context.MODE_PRIVATE)
        favorites = prefs.getStringSet("favorites", emptySet())!!.toSet()
        liveFormat = prefs.getString("liveFormat", "ts") ?: "ts"
        val server = prefs.getString("server", null)
        val user = prefs.getString("user", null)
        val pass = prefs.getString("pass", null)
        if (server != null && user != null && pass != null) {
            client = XtreamClient(Account(server, user, pass))
        }
    }

    suspend fun login(account: Account): Boolean {
        val c = XtreamClient(account)
        if (!c.authenticate()) return false
        prefs.edit()
            .putString("server", c.base)
            .putString("user", account.username)
            .putString("pass", account.password)
            .apply()
        clearCache()
        client = c
        return true
    }

    fun logout() {
        prefs.edit().remove("server").remove("user").remove("pass").apply()
        client = null
        clearCache()
    }

    fun clearCache() {
        liveCats = null; live = null; vodCats = null; vod = null
        seriesCats = null; series = null
        seriesInfos.clear(); epg.clear()
    }

    fun setFormat(format: String) {
        liveFormat = format
        prefs.edit().putString("liveFormat", format).apply()
    }

    fun isFavorite(key: String) = key in favorites

    /** Ajoute ou retire un favori. Renvoie true s'il est maintenant favori. */
    fun toggleFavorite(key: String): Boolean {
        val now = key !in favorites
        favorites = if (now) favorites + key else favorites - key
        prefs.edit().putStringSet("favorites", favorites).apply()
        return now
    }

    private fun c() = client ?: error("Non connecté")

    suspend fun liveCategories() = liveCats ?: c().liveCategories().also { liveCats = it }
    suspend fun liveStreams() = live ?: c().liveStreams().also { live = it }
    suspend fun vodCategories() = vodCats ?: c().vodCategories().also { vodCats = it }
    suspend fun vodStreams() = vod ?: c().vodStreams().also { vod = it }
    suspend fun seriesCategories() = seriesCats ?: c().seriesCategories().also { seriesCats = it }
    suspend fun seriesList() = series ?: c().seriesList().also { series = it }
    suspend fun seriesInfo(id: Int) = seriesInfos[id] ?: c().seriesInfo(id).also { seriesInfos[id] = it }
    suspend fun accountInfo() = c().accountInfo()

    /** Programmes à venir d'une chaîne (cache de 10 minutes). */
    suspend fun shortEpg(streamId: Int, limit: Int = 24): List<Programme> {
        val now = System.currentTimeMillis()
        epg[streamId]?.let { (time, cached) ->
            if (now - time < 10 * 60_000 && cached.first >= limit) {
                return cached.second.filter { it.end > now }
            }
        }
        val list = try {
            c().shortEpg(streamId, limit)
        } catch (e: Exception) {
            emptyList()
        }
        epg[streamId] = now to (limit to list)
        return list.filter { it.end > now }
    }

    fun liveUrl(ch: Channel, format: String = liveFormat) = c().liveUrl(ch.streamId, format)
    fun movieUrl(m: Movie) = c().movieUrl(m)
    fun episodeUrl(e: Episode) = c().episodeUrl(e)
}
