package com.example.tvlauncher.data.tmdb

/**
 * [tmdbIds]: IDs de watch provider do TMDB, "|" = OU.
 * Confira em GET /3/watch/providers/movie?watch_region=BR.
 */
enum class StreamingProvider(
    val label: String,
    val tmdbIds: String,
    val packages: List<String>,
) {
    NETFLIX("Netflix", "8|1796", listOf("com.netflix.ninja", "com.netflix.mediaclient")),
    PRIME_VIDEO(
        "Prime Video", "119|9",
        listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
    ),
    DISNEY_PLUS("Disney+", "337", listOf("com.disney.disneyplus")),
}
