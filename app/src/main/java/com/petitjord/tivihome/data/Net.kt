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
