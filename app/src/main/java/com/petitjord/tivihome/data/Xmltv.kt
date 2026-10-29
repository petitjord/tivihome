package com.petitjord.tivihome.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Guide XMLTV externe : téléchargé (gzip accepté), mis en cache 12 h,
 * puis lu en flux pour ne garder que la fenêtre utile (hier → +3 jours).
 */
class XmltvGuide(
    /** id XMLTV -> programmes triés */
    private val byId: Map<String, List<Programme>>,
    /** nom normalisé -> id XMLTV */
    private val byName: Map<String, String>,
) {
    val channelCount get() = byId.size

    fun programmesFor(ch: Channel): List<Programme>? {
        ch.epgId?.let { id -> byId[id]?.let { return it } }
        val id = byName[normalizeName(ch.name)] ?: return null
        return byId[id]
    }

    companion object {
        private val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        /** Enlève accents, qualité (HD, FHD…), préfixes pays et ponctuation pour rapprocher les noms. */
        fun normalizeName(name: String): String {
            var n = Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            n = n.replace(Regex("^(fr|be|ch|ca|uk|us)\\s*[:|\\-]\\s*"), "")
            n = n.replace(Regex("\\b(uhd|fhd|hd|sd|4k|hevc|h265|backup|\\+1)\\b"), "")
            return n.replace(Regex("[^a-z0-9]"), "")
        }

        suspend fun load(url: String, cacheDir: File, force: Boolean): XmltvGuide = withContext(Dispatchers.IO) {
            val cache = File(cacheDir, "xmltv-${url.hashCode()}.xml")
            val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 12 * 3_600_000L
            if (force || !fresh) download(url, cache)
            cache.inputStream().buffered().use { parse(it) }
        }

        private fun download(url: String, target: File) {
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Le guide a répondu ${r.code}")
                val body = r.body ?: throw IOException("Guide vide")
                val tmp = File(target.parentFile, target.name + ".part")
                val raw = BufferedInputStream(body.byteStream())
                raw.mark(4)
                val b1 = raw.read(); val b2 = raw.read()
                raw.reset()
                val input: InputStream = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw) else raw
                tmp.outputStream().use { out -> input.copyTo(out) }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true); tmp.delete()
                }
            }
        }

        private fun parse(input: InputStream): XmltvGuide {
            val now = System.currentTimeMillis()
            val from = now - 26 * 3_600_000L
            val to = now + 3 * 24 * 3_600_000L
            val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
            val fmtNoTz = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            fun time(s: String?): Long? {
                if (s.isNullOrBlank()) return null
                return try {
                    if (s.length > 14) fmt.parse(s.trim())?.time else fmtNoTz.parse(s.trim())?.time
                } catch (e: Exception) {
                    null
                }
            }

            val progs = HashMap<String, MutableList<Programme>>()
            val names = HashMap<String, String>()
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, null)

            var channelId: String? = null
            var progChannel: String? = null
            var start: Long? = null
            var stop: Long? = null
            var title = ""
            var desc = ""
            var tag: String? = null

            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        tag = p.name
                        when (p.name) {
                            "channel" -> channelId = p.getAttributeValue(null, "id")
                            "programme" -> {
                                progChannel = p.getAttributeValue(null, "channel")
                                start = time(p.getAttributeValue(null, "start"))
                                stop = time(p.getAttributeValue(null, "stop"))
                                title = ""; desc = ""
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val t = p.text
                        when (tag) {
                            "display-name" -> channelId?.let { id ->
                                val key = normalizeName(t)
                                if (key.isNotEmpty() && key !in names) names[key] = id
                            }
                            "title" -> if (progChannel != null && title.isEmpty()) title = t.trim()
                            "desc" -> if (progChannel != null && desc.isEmpty()) desc = t.trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (p.name) {
                            "channel" -> channelId = null
                            "programme" -> {
                                val c = progChannel; val s = start; val e = stop
                                if (c != null && s != null && e != null && e > from && s < to && e > s) {
                                    progs.getOrPut(c) { mutableListOf() } += Programme(title, desc, s, e)
                                }
                                progChannel = null
                            }
                        }
                        tag = null
                    }
                }
                ev = p.next()
            }
            progs.values.forEach { it.sortBy { pr -> pr.start } }
            return XmltvGuide(progs, names)
        }
    }
}
