package com.petitjord.tivihome.data

import okhttp3.OkHttpClient
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/**
 * Client réseau partagé (API, flux vidéo).
 *
 * Beaucoup de fournisseurs IPTV présentent un certificat HTTPS valide mais émis
 * pour un autre nom de domaine que celui de leur serveur. On accepte ce décalage
 * uniquement pour les serveurs des comptes enregistrés : la chaîne de certificats
 * reste vérifiée, seul le nom d'hôte est toléré.
 */
object Net {
    private val trustedHosts: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun trustHost(url: String) {
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
        if (host.isNotBlank()) trustedHosts += host
    }

    /** Identifiants d'appli génériques essayés quand un serveur répond 403. */
    val userAgents = listOf(
        "VLC/3.0.20 LibVLC/3.0.20",
        "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "okhttp/4.12.0",
        "Lavf/60.3.100",
        "Tivihome/1.0 (Android TV)",
    )

    /** User-Agent utilisé pour l'API, les flux et le guide (mémorisé après une connexion réussie). */
    @Volatile var userAgent: String = userAgents.first()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .hostnameVerifier { host, session ->
            HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session) ||
                host.lowercase() in trustedHosts
        }
        .build()
}
