package com.petitjord.tivihome.data

data class Account(val server: String, val username: String, val password: String)

data class Category(val id: String, val name: String)

data class Channel(
    val streamId: Int,
    val num: Int,
    val name: String,
    val icon: String?,
    val categoryId: String,
    val epgId: String?,
    /** Nombre de jours de replay disponibles (0 = pas de replay). */
    val archiveDays: Int = 0,
) {
    val favKey get() = "live:$streamId"
}

data class Movie(
    val streamId: Int,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val ext: String,
    val rating: String?,
) {
    val favKey get() = "movie:$streamId"
}

data class Series(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val plot: String?,
) {
    val favKey get() = "series:$seriesId"
}

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episodeNum: Int,
    val ext: String,
    val plot: String?,
    val image: String?,
)

data class SeriesInfo(val plot: String?, val episodes: List<Episode>)

/** start / end en millisecondes. */
data class Programme(
    val title: String,
    val description: String,
    val start: Long,
    val end: Long,
    /** Disponible en replay chez le fournisseur. */
    val hasArchive: Boolean = false,
) {
    fun isNow(now: Long = System.currentTimeMillis()) = now in start until end

    /** Avancement entre 0 et 1. */
    fun progress(now: Long = System.currentTimeMillis()): Float =
        ((now - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
}

data class AccountInfo(val status: String?, val expiry: Long?, val maxConnections: String?, val timezone: String?)

/** Film, épisode ou replay à lire, avec ce qu'il faut pour la reprise. */
data class VodItem(
    /** Identifiant stable pour la reprise (null = pas de reprise, ex. replay). */
    val key: String?,
    val title: String,
    val url: String,
    val image: String? = null,
)

/** Position mémorisée d'un film / épisode. */
data class ResumeEntry(
    val key: String,
    val title: String,
    val url: String,
    val image: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
