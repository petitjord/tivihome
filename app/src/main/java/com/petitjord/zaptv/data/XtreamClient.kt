package com.petitjord.zaptv.data

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

const val USER_AGENT = "ZapTV/1.0 (Android TV)"

/** Client de l'API publique Xtream Codes (player_api.php). */
class XtreamClient(val account: Account) {

    val base: String = normalize(account.server)

    companion object {
        private val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

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
        val ui = obj(api(null)).optJSONObject("user_info") ?: return AccountInfo(null, null, null)
        return AccountInfo(
            status = ui.s("status"),
            expiry = ui.s("exp_date")?.toLongOrNull()?.times(1000),
            maxConnections = ui.s("max_connections"),
        )
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
        val plot = j.optJSONObject("info")?.s("plot")
        return SeriesInfo(plot, episodes.sortedWith(compareBy({ it.season }, { it.episodeNum })))
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
