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
) {
    fun isNow(now: Long = System.currentTimeMillis()) = now in start until end

    /** Avancement entre 0 et 1. */
    fun progress(now: Long = System.currentTimeMillis()): Float =
        ((now - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
}

data class AccountInfo(val status: String?, val expiry: Long?, val maxConnections: String?)
