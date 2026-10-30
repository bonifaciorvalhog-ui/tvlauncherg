package com.example.tvlauncher.data.tmdb

import android.net.Uri
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class MediaType(val path: String) { MOVIE("movie"), TV("tv") }

data class TmdbTitle(
    val id: Long,
    val type: MediaType,
    val title: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val rating: Double,
    val year: String?,
    val popularity: Double,
)

class TmdbApi(private val apiKey: String) {

    /** Títulos mais populares disponíveis por assinatura nas plataformas indicadas. */
    fun discover(type: MediaType, providerIds: String, region: String, language: String): List<TmdbTitle> {
        val url = Uri.Builder()
            .scheme("https")
            .authority("api.themoviedb.org")
            .appendPath("3").appendPath("discover").appendPath(type.path)
            .appendQueryParameter("api_key", apiKey)
            .appendQueryParameter("language", language)
            .appendQueryParameter("watch_region", region)
            .appendQueryParameter("with_watch_providers", providerIds)
            .appendQueryParameter("with_watch_monetization_types", "flatrate")
            .appendQueryParameter("sort_by", "popularity.desc")
            .build()
            .toString()

        val results = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { results.optJSONObject(it)?.toTitle(type) }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("TMDB HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONObject.toTitle(type: MediaType): TmdbTitle? {
        val movie = type == MediaType.MOVIE
        if (str("poster_path") == null && str("backdrop_path") == null) return null // sem imagem, sem card
        return TmdbTitle(
            id = optLong("id"),
            type = type,
            title = str(if (movie) "title" else "name") ?: return null,
            overview = str("overview"),
            posterPath = str("poster_path"),
            backdropPath = str("backdrop_path"),
            rating = optDouble("vote_average", 0.0),
            year = str(if (movie) "release_date" else "first_air_date")?.take(4),
            popularity = optDouble("popularity", 0.0),
        )
    }

    // optString devolve "null" para JSON null; isNull cobre ausente e null.
    private fun JSONObject.str(name: String): String? =
        if (isNull(name)) null else optString(name).ifBlank { null }
}
