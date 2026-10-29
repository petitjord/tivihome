package com.petitjord.tivihome.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Mise à jour depuis l'appli : lit la dernière version publiée sur GitHub et l'installe. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/petitjord/tivihome/releases/latest"

    /** Numéro de la version disponible si elle est plus récente que celle installée. */
    var available by mutableStateOf<Int?>(null)
        private set
    var status by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    private var apkUrl: String? = null

    fun installedBuild(ctx: Context): Int {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }

    fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** Vérifie s'il existe une version plus récente. */
    suspend fun check(ctx: Context) {
        try {
            val json = withContext(Dispatchers.IO) {
                val req = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
                Net.client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("GitHub a répondu ${r.code}")
                    JSONObject(r.body?.string().orEmpty())
                }
            }
            val tag = json.optString("tag_name")               // ex. v1.0.12
            val build = tag.substringAfterLast('.').toIntOrNull() ?: 0
            val assets = json.optJSONArray("assets")
            apkUrl = (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
            val mine = installedBuild(ctx)
            if (build > mine && apkUrl != null) {
                available = build
                status = "Nouvelle version 1.0.$build disponible"
            } else {
                available = null
                status = "Tivihome est à jour (1.0.$mine)"
            }
        } catch (e: Exception) {
            status = "Vérification impossible : ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Télécharge l'APK puis ouvre l'installeur Android. */
    suspend fun downloadAndInstall(ctx: Context) {
        val url = apkUrl ?: run { check(ctx); apkUrl } ?: return
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            status = "Autorise Tivihome à installer des applis, puis relance la mise à jour."
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        busy = true
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "Tivihome.apk")
                val req = Request.Builder().url(url).build()
                Net.client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("téléchargement refusé (${r.code})")
                    val body = r.body ?: throw IOException("fichier vide")
                    val total = body.contentLength()
                    var done = 0L
                    out.outputStream().use { o ->
                        body.byteStream().use { i ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = i.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                done += n
                                if (total > 0) status = "Téléchargement… ${done * 100 / total} %"
                            }
                        }
                    }
                }
                out
            }
            status = "Installation…"
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            status = "Échec de la mise à jour : ${e.message ?: e.javaClass.simpleName}"
        } finally {
            busy = false
        }
    }
}
