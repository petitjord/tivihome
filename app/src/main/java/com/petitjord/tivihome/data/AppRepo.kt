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
    val xmltvGuide: XmltvGuide? get() = xmltv

    // ---------- Réglages ----------
    /** Ouvrir directement la dernière chaîne au lancement. */
    var startOnLastChannel by mutableStateOf(false)
        private set
    /** Taille des sous-titres : 0 petit, 1 normal, 2 grand. */
    var subtitleSize by mutableStateOf(1)
        private set
    /** Groupes masqués (identifiants de catégorie). */
    var hiddenGroups by mutableStateOf<Set<String>>(emptySet())
        private set
    /** Rappels d'émissions à venir. */
    var reminders by mutableStateOf<List<Reminder>>(emptyList())
        private set
    /** Comptes enregistrés (le compte actif est client). */
    var accounts by mutableStateOf<List<Account>>(emptyList())
        private set

    private var liveCats: List<Category>? = null
    private var live: List<Channel>? = null
    private var vodCats: List<Category>? = null
    private var vod: List<Movie>? = null
    private var seriesCats: List<Category>? = null
    private var series: List<Series>? = null
    private var serverTz: String? = null
    private val seriesInfos = ConcurrentHashMap<Int, SeriesInfo>()
    private val vodInfos = ConcurrentHashMap<Int, MovieInfo>()
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
        startOnLastChannel = prefs.getBoolean("startLast", false)
        subtitleSize = prefs.getInt("subSize", 1)
        hiddenGroups = prefs.getStringSet("hiddenGroups", emptySet())!!.toSet()
        reminders = readReminders()
        accounts = readAccounts()
        com.petitjord.tivihome.ui.AppTheme.accentIndex = prefs.getInt("accent", 0)
        com.petitjord.tivihome.ui.AppTheme.oled = prefs.getBoolean("oled", false)
        val server = prefs.getString("server", null)
        val user = prefs.getString("user", null)
        val pass = prefs.getString("pass", null)
        if (server != null && user != null && pass != null) {
            client = XtreamClient(Account(server, user, pass))
        }
    }

    suspend fun login(account: Account): Boolean {
        var c = XtreamClient(account)
        val ok = try {
            c.authenticate()
        } catch (e: javax.net.ssl.SSLException) {
            // HTTPS refusé : beaucoup de serveurs IPTV répondent aussi en HTTP
            if (!c.base.startsWith("https://")) throw e
            c = XtreamClient(account.copy(server = "http://" + c.base.removePrefix("https://")))
            c.authenticate()
        }
        if (!ok) return false
        prefs.edit()
            .putString("server", c.base)
            .putString("user", account.username)
            .putString("pass", account.password)
            .apply()
        val acc = Account(c.base, account.username, account.password)
        if (accounts.none { it.server == acc.server && it.username == acc.username }) {
            accounts = accounts + acc
            writeAccounts()
        }
        clearCache()
        client = c
        return true
    }

    /** Bascule sur un autre compte enregistré. */
    fun switchAccount(acc: Account) {
        prefs.edit().putString("server", acc.server).putString("user", acc.username).putString("pass", acc.password).apply()
        clearCache()
        client = XtreamClient(acc)
    }

    fun removeAccount(acc: Account) {
        accounts = accounts.filterNot { it.server == acc.server && it.username == acc.username }
        writeAccounts()
    }

    private fun readAccounts(): List<Account> = try {
        val arr = JSONArray(prefs.getString("accounts", "[]"))
        val list = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Account(it.getString("s"), it.getString("u"), it.getString("p")) }
        }
        // Le compte actif des versions précédentes est repris automatiquement.
        val s = prefs.getString("server", null); val u = prefs.getString("user", null); val p = prefs.getString("pass", null)
        if (s != null && u != null && p != null && list.none { it.server == s && it.username == u }) list + Account(s, u, p) else list
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeAccounts() {
        val arr = JSONArray()
        accounts.forEach { arr.put(JSONObject().put("s", it.server).put("u", it.username).put("p", it.password)) }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    // ---------- Réglages ----------

    fun setAccent(i: Int) { com.petitjord.tivihome.ui.AppTheme.accentIndex = i; prefs.edit().putInt("accent", i).apply() }
    fun setOled(on: Boolean) { com.petitjord.tivihome.ui.AppTheme.oled = on; prefs.edit().putBoolean("oled", on).apply() }
    fun setStartLast(on: Boolean) { startOnLastChannel = on; prefs.edit().putBoolean("startLast", on).apply() }
    fun setSubSize(v: Int) { subtitleSize = v; prefs.edit().putInt("subSize", v).apply() }

    /** Dernière chaîne regardée et celle d'avant (pour la touche « précédente »). */
    val lastChannelId: Int get() = prefs.getInt("lastCh", -1)
    fun rememberChannel(id: Int) {
        val last = prefs.getInt("lastCh", -1)
        if (last == id) return
        prefs.edit().putInt("prevCh", last).putInt("lastCh", id).apply()
    }
    val previousChannelId: Int get() = prefs.getInt("prevCh", -1)

    fun toggleHiddenGroup(id: String): Boolean {
        val hide = id !in hiddenGroups
        hiddenGroups = if (hide) hiddenGroups + id else hiddenGroups - id
        prefs.edit().putStringSet("hiddenGroups", hiddenGroups).apply()
        return hide
    }

    fun showAllGroups() {
        hiddenGroups = emptySet()
        prefs.edit().putStringSet("hiddenGroups", hiddenGroups).apply()
    }

    // ---------- Rappels ----------

    fun hasReminder(ch: Channel, p: Programme) = reminders.any { it.streamId == ch.streamId && it.start == p.start }

    fun toggleReminder(ch: Channel, p: Programme): Boolean {
        val exists = hasReminder(ch, p)
        reminders = if (exists) reminders.filterNot { it.streamId == ch.streamId && it.start == p.start }
        else reminders + Reminder(ch.streamId, ch.name, p.title, p.start)
        writeReminders()
        return !exists
    }

    fun dropReminder(r: Reminder) {
        reminders = reminders - r
        writeReminders()
    }

    private fun readReminders(): List<Reminder> = try {
        val arr = JSONArray(prefs.getString("reminders", "[]"))
        val now = System.currentTimeMillis()
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Reminder(it.getInt("id"), it.optString("ch"), it.optString("t"), it.getLong("s")) }
        }.filter { it.start > now - 10 * 60_000 }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeReminders() {
        val arr = JSONArray()
        reminders.forEach { arr.put(JSONObject().put("id", it.streamId).put("ch", it.channelName).put("t", it.title).put("s", it.start)) }
        prefs.edit().putString("reminders", arr.toString()).apply()
    }

    fun logout() {
        prefs.edit().remove("server").remove("user").remove("pass").apply()
        client = null
        clearCache()
    }

    fun clearCache() {
        liveCats = null; live = null; vodCats = null; vod = null
        seriesCats = null; series = null; serverTz = null
        seriesInfos.clear(); vodInfos.clear(); epg.clear(); archive.clear()
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
    suspend fun vodInfo(m: Movie) = vodInfos[m.streamId] ?: c().vodInfo(m.streamId).also { vodInfos[m.streamId] = it }
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
