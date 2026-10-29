package com.petitjord.tivihome.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Point d'accès unique : compte, préférences, cache mémoire, favoris, reprise, guide XMLTV. */
object AppRepo {
    private lateinit var prefs: SharedPreferences
    private lateinit var cacheDir: File

    var client: XtreamClient? = null
        private set

    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set

    /** "ts" (MPEG-TS) ou "m3u8" (HLS) pour la TV en direct. */
    var liveFormat by mutableStateOf("ts")
        private set

    /** Films / épisodes commencés, du plus récent au plus ancien. */
    var resume by mutableStateOf<List<ResumeEntry>>(emptyList())
        private set

    // ---------- Guide XMLTV externe ----------
    var xmltvUrl by mutableStateOf("")
        private set
    var xmltvStatus by mutableStateOf("Aucun guide externe")
        private set
    private var xmltv: XmltvGuide? = null

    private var liveCats: List<Category>? = null
    private var live: List<Channel>? = null
    private var vodCats: List<Category>? = null
    private var vod: List<Movie>? = null
    private var seriesCats: List<Category>? = null
    private var series: List<Series>? = null
    private var serverTz: String? = null
    private val seriesInfos = ConcurrentHashMap<Int, SeriesInfo>()
    /** streamId -> (heure de chargement, (limite demandée, programmes)) */
    private val epg = ConcurrentHashMap<Int, Pair<Long, Pair<Int, List<Programme>>>>()
    private val archive = ConcurrentHashMap<Int, Pair<Long, List<Programme>>>()

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.getSharedPreferences("tivihome", Context.MODE_PRIVATE)
        cacheDir = ctx.cacheDir
        favorites = prefs.getStringSet("favorites", emptySet())!!.toSet()
        liveFormat = prefs.getString("liveFormat", "ts") ?: "ts"
        xmltvUrl = prefs.getString("xmltvUrl", "") ?: ""
        resume = readResume()
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
        seriesCats = null; series = null; serverTz = null
        seriesInfos.clear(); epg.clear(); archive.clear()
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
    suspend fun accountInfo() = c().accountInfo().also { serverTz = it.timezone }

    /** Programmes à venir d'une chaîne : guide XMLTV s'il la connaît, sinon celui du fournisseur. */
    suspend fun shortEpg(ch: Channel, limit: Int = 24): List<Programme> {
        val now = System.currentTimeMillis()
        xmltv?.programmesFor(ch)?.let { list ->
            val upcoming = list.filter { it.end > now }
            if (upcoming.isNotEmpty()) return upcoming.take(limit)
        }
        epg[ch.streamId]?.let { (time, cached) ->
            if (now - time < 10 * 60_000 && cached.first >= limit) {
                return cached.second.filter { it.end > now }
            }
        }
        val list = try {
            c().shortEpg(ch.streamId, limit)
        } catch (e: Exception) {
            emptyList()
        }
        epg[ch.streamId] = now to (limit to list)
        return list.filter { it.end > now }
    }

    // ---------- Replay ----------

    /** Émissions passées disponibles en replay (cache 15 min). */
    suspend fun replayProgrammes(ch: Channel): List<Programme> {
        val now = System.currentTimeMillis()
        archive[ch.streamId]?.let { (t, list) -> if (now - t < 15 * 60_000) return list }
        val oldest = now - ch.archiveDays * 24 * 3_600_000L
        val list = c().archiveEpg(ch.streamId)
            .filter { it.end <= now && it.start >= oldest }
            .map { it.copy(hasArchive = true) }
        archive[ch.streamId] = now to list
        return list
    }

    suspend fun replayUrl(ch: Channel, p: Programme): String {
        if (serverTz == null) runCatching { accountInfo() }
        val minutes = ((p.end - p.start) / 60_000L).toInt().coerceAtLeast(1) + 1
        return c().timeshiftUrl(ch.streamId, p.start, minutes, serverTz)
    }

    // ---------- Guide XMLTV ----------

    fun updateXmltvUrl(url: String) {
        xmltvUrl = url.trim()
        prefs.edit().putString("xmltvUrl", xmltvUrl).apply()
        if (xmltvUrl.isEmpty()) {
            xmltv = null
            xmltvStatus = "Aucun guide externe"
        }
    }

    suspend fun loadXmltv(force: Boolean = false) {
        val url = xmltvUrl
        if (url.isEmpty()) return
        xmltvStatus = "Chargement du guide…"
        xmltvStatus = try {
            val g = XmltvGuide.load(url, cacheDir, force)
            xmltv = g
            epg.clear()
            "Guide chargé : ${g.channelCount} chaînes"
        } catch (e: Exception) {
            "Échec du chargement : ${e.message ?: e.javaClass.simpleName}"
        }
    }

    // ---------- Reprise de lecture ----------

    fun resumePosition(key: String): Long = resume.firstOrNull { it.key == key }?.positionMs ?: 0L

    fun saveProgress(item: VodItem, positionMs: Long, durationMs: Long) {
        val key = item.key ?: return
        if (durationMs <= 0 || positionMs < 0) return
        val others = resume.filter { it.key != key }
        // Terminé (plus de 95 %) ou à peine commencé : on oublie la position.
        resume = if (positionMs < 30_000 || positionMs > durationMs * 0.95) {
            others
        } else {
            (listOf(ResumeEntry(key, item.title, item.url, item.image, positionMs, durationMs, System.currentTimeMillis())) + others).take(40)
        }
        writeResume()
    }

    fun forgetProgress(key: String) {
        resume = resume.filter { it.key != key }
        writeResume()
    }

    private fun readResume(): List<ResumeEntry> = try {
        val arr = JSONArray(prefs.getString("resume", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                ResumeEntry(
                    key = o.getString("key"),
                    title = o.optString("title"),
                    url = o.getString("url"),
                    image = o.optString("image").takeIf { it.isNotBlank() },
                    positionMs = o.optLong("pos"),
                    durationMs = o.optLong("dur"),
                    updatedAt = o.optLong("at"),
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeResume() {
        val arr = JSONArray()
        resume.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key).put("title", it.title).put("url", it.url)
                    .put("image", it.image ?: "").put("pos", it.positionMs)
                    .put("dur", it.durationMs).put("at", it.updatedAt),
            )
        }
        prefs.edit().putString("resume", arr.toString()).apply()
    }

    fun liveUrl(ch: Channel, format: String = liveFormat) = c().liveUrl(ch.streamId, format)
    fun movieUrl(m: Movie) = c().movieUrl(m)
    fun episodeUrl(e: Episode) = c().episodeUrl(e)
}
