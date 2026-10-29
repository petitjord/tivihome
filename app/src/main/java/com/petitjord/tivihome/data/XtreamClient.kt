package com.petitjord.tivihome.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

const val USER_AGENT = "Tivihome/1.0 (Android TV)"

/** Client de l'API publique Xtream Codes (player_api.php). */
class XtreamClient(val account: Account) {

    val base: String = normalize(account.server)

    init {
        Net.trustHost(base)
    }

    companion object {
        private val http get() = Net.client

        fun normalize(server: String): String {
            var u = server.trim()
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            u = u.trimEnd('/')
            u = u.removeSuffix("/player_api.php").removeSuffix("/get.php")
            return u.trimEnd('/')
        }
    }

    private suspend fun api(action: String?, params: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val url = try {
                base.toHttpUrl().newBuilder()
            } catch (e: IllegalArgumentException) {
                throw IOException("Adresse du serveur invalide")
            }
            url.addPathSegment("player_api.php")
                .addQueryParameter("username", account.username)
                .addQueryParameter("password", account.password)
            if (action != null) url.addQueryParameter("action", action)
            params.forEach { (k, v) -> url.addQueryParameter(k, v) }
            val req = Request.Builder().url(url.build()).header("User-Agent", USER_AGENT).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Le serveur a répondu ${r.code}")
                r.body?.string() ?: ""
            }
        }

    private fun array(body: String): JSONArray = try {
        JSONArray(body)
    } catch (e: JSONException) {
        JSONArray()
    }

    private fun obj(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw IOException("Réponse inattendue du serveur")
    }

    // ---------- Compte ----------

    suspend fun authenticate(): Boolean {
        val ui = obj(api(null)).optJSONObject("user_info") ?: return false
        return ui.optString("auth") == "1"
    }

    suspend fun accountInfo(): AccountInfo {
        val root = obj(api(null))
        val ui = root.optJSONObject("user_info")
        return AccountInfo(
            status = ui?.s("status"),
            expiry = ui?.s("exp_date")?.toLongOrNull()?.times(1000),
            maxConnections = ui?.s("max_connections"),
            timezone = root.optJSONObject("server_info")?.s("timezone"),
        )
    }

    /** Guide complet d'une chaîne, programmes passés compris (pour le replay). */
    suspend fun archiveEpg(streamId: Int): List<Programme> {
        val body = api("get_simple_data_table", mapOf("stream_id" to "$streamId"))
        val listings = try {
            JSONObject(body).optJSONArray("epg_listings")
        } catch (e: JSONException) {
            null
        } ?: return emptyList()
        return listings.mapObj { o ->
            Programme(
                title = b64(o.optString("title")),
                description = b64(o.optString("description")),
                start = o.optString("start_timestamp").toLongOrNull()?.times(1000) ?: 0L,
                end = o.optString("stop_timestamp").toLongOrNull()?.times(1000) ?: 0L,
                hasArchive = o.optString("has_archive") == "1",
            )
        }.filter { it.start > 0 && it.end > it.start }.distinctBy { it.start }.sortedBy { it.start }
    }

    // ---------- TV en direct ----------

    suspend fun liveCategories() = categories("get_live_categories")

    suspend fun liveStreams(): List<Channel> = array(api("get_live_streams")).mapObj { o ->
        Channel(
            streamId = o.optInt("stream_id"),
            num = o.optInt("num"),
            name = o.optString("name").trim(),
            icon = o.s("stream_icon"),
            categoryId = o.optString("category_id"),
            epgId = o.s("epg_channel_id"),
            archiveDays = if (o.optString("tv_archive") == "1") {
                o.optString("tv_archive_duration").toIntOrNull()?.coerceAtLeast(1) ?: 1
            } else 0,
        )
    }

    suspend fun shortEpg(streamId: Int, limit: Int): List<Programme> {
        val body = api("get_short_epg", mapOf("stream_id" to "$streamId", "limit" to "$limit"))
        val listings = try {
            JSONObject(body).optJSONArray("epg_listings")
        } catch (e: JSONException) {
            null
        } ?: return emptyList()
        return listings.mapObj { o ->
            Programme(
                title = b64(o.optString("title")),
                description = b64(o.optString("description")),
                start = o.optString("start_timestamp").toLongOrNull()?.times(1000) ?: 0L,
                end = o.optString("stop_timestamp").toLongOrNull()?.times(1000) ?: 0L,
            )
        }.filter { it.start > 0 && it.end > it.start }.sortedBy { it.start }
    }

    // ---------- Films ----------

    suspend fun vodCategories() = categories("get_vod_categories")

    suspend fun vodStreams(): List<Movie> = array(api("get_vod_streams")).mapObj { o ->
        Movie(
            streamId = o.optInt("stream_id"),
            name = o.optString("name").trim(),
            poster = o.s("stream_icon"),
            categoryId = o.optString("category_id"),
            ext = o.s("container_extension") ?: "mp4",
            rating = o.s("rating"),
            added = o.optString("added").toLongOrNull() ?: 0L,
        )
    }

    // ---------- Séries ----------

    suspend fun seriesCategories() = categories("get_series_categories")

    suspend fun seriesList(): List<Series> = array(api("get_series")).mapObj { o ->
        Series(
            seriesId = o.optInt("series_id"),
            name = o.optString("name").trim(),
            cover = o.s("cover"),
            categoryId = o.optString("category_id"),
            plot = o.s("plot"),
            added = o.optString("last_modified").toLongOrNull() ?: 0L,
            rating = o.s("rating"),
        )
    }

    suspend fun seriesInfo(seriesId: Int): SeriesInfo {
        val j = obj(api("get_series_info", mapOf("series_id" to "$seriesId")))
        val episodes = mutableListOf<Episode>()
        fun addFrom(arr: JSONArray, seasonHint: Int) {
            for (i in 0 until arr.length()) {
                when (val item = arr.opt(i)) {
                    is JSONObject -> episodes += episode(item, seasonHint)
                    is JSONArray -> addFrom(item, seasonHint)
                }
            }
        }
        when (val eps = j.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { key ->
                eps.optJSONArray(key)?.let { addFrom(it, key.toIntOrNull() ?: 0) }
            }
            is JSONArray -> addFrom(eps, 0)
        }
        val info = j.optJSONObject("info")
        return SeriesInfo(
            plot = info?.s("plot"),
            episodes = episodes.sortedWith(compareBy({ it.season }, { it.episodeNum })),
            cast = info?.s("cast"),
            genre = info?.s("genre"),
            backdrop = info?.let { firstImage(it, "backdrop_path") },
            trailer = info?.s("youtube_trailer"),
        )
    }

    /** Fiche détaillée d'un film (get_vod_info). */
    suspend fun vodInfo(streamId: Int): MovieInfo {
        val info = obj(api("get_vod_info", mapOf("vod_id" to "$streamId"))).optJSONObject("info") ?: JSONObject()
        return MovieInfo(
            plot = info.s("plot") ?: info.s("description"),
            cast = info.s("cast") ?: info.s("actors"),
            director = info.s("director"),
            genre = info.s("genre"),
            year = info.s("releasedate")?.take(4) ?: info.s("year"),
            durationText = info.s("duration"),
            rating = info.s("rating"),
            backdrop = firstImage(info, "backdrop_path"),
            trailer = info.s("youtube_trailer"),
        )
    }

    /** backdrop_path peut être une chaîne ou un tableau d'URL. */
    private fun firstImage(o: JSONObject, key: String): String? = when (val v = o.opt(key)) {
        is JSONArray -> (0 until v.length()).map { v.optString(it) }.firstOrNull { it.startsWith("http") }
        is String -> v.takeIf { it.startsWith("http") }
        else -> null
    }

    private fun episode(o: JSONObject, seasonHint: Int): Episode {
        val info = o.optJSONObject("info")
        return Episode(
            id = o.optString("id"),
            title = o.optString("title").trim(),
            season = o.optString("season").toIntOrNull() ?: seasonHint,
            episodeNum = o.optString("episode_num").toIntOrNull() ?: 0,
            ext = o.s("container_extension") ?: "mp4",
            plot = info?.s("plot"),
            image = info?.s("movie_image"),
        )
    }

    // ---------- URLs de lecture ----------

    fun liveUrl(streamId: Int, format: String) =
        "$base/live/${account.username}/${account.password}/$streamId.$format"

    /** URL de replay : début exprimé dans le fuseau horaire du serveur. */
    fun timeshiftUrl(streamId: Int, startMs: Long, durationMin: Int, timezone: String?): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
        fmt.timeZone = timezone?.let { java.util.TimeZone.getTimeZone(it) } ?: java.util.TimeZone.getDefault()
        val start = fmt.format(java.util.Date(startMs))
        return "$base/timeshift/${account.username}/${account.password}/$durationMin/$start/$streamId.ts"
    }

    fun movieUrl(m: Movie) =
        "$base/movie/${account.username}/${account.password}/${m.streamId}.${m.ext}"

    fun episodeUrl(e: Episode) =
        "$base/series/${account.username}/${account.password}/${e.id}.${e.ext}"

    // ---------- Utilitaires ----------

    private suspend fun categories(action: String) = array(api(action)).mapObj { o ->
        Category(o.optString("category_id"), o.optString("category_name").trim())
    }

    private fun b64(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
    } catch (e: IllegalArgumentException) {
        s
    }
}

private fun JSONObject.s(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private inline fun <T> JSONArray.mapObj(f: (JSONObject) -> T): List<T> {
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out += f(it) }
    return out
}
