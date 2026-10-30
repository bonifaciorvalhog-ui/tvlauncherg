package com.example.tvlauncher.data.tmdb

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.example.tvlauncher.BuildConfig
import com.example.tvlauncher.R
import com.example.tvlauncher.data.SetupPrefs
import com.example.tvlauncher.model.HomeItem
import com.example.tvlauncher.model.HomeRow
import com.example.tvlauncher.model.RowStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale

private class Cached(
    val timestamp: Long,
    val signature: String,
    val data: Map<StreamingProvider, List<TmdbTitle>>,
)

/** Cache em memória do processo: evita rede a cada volta à tela inicial. */
private object CatalogCache {
    @Volatile var value: Cached? = null
}

class TmdbRepository(private val context: Context) {

    private companion object {
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/w780"
        const val TTL_MS = 30 * 60 * 1000L
        const val PER_TYPE = 12
        const val MAX_ITEMS = 20
    }

    private val settings = SetupPrefs(context)

    /** Uma fileira por plataforma. Lista vazia se desativado, sem chave, sem rede ou erro. */
    suspend fun loadRows(): List<HomeRow> = withContext(Dispatchers.IO) {
        val catalog = catalog()
        if (catalog.isEmpty()) return@withContext emptyList()

        // Intents são montadas a cada chamada: refletem os apps instalados agora.
        val resolver = DeepLinkResolver(context)
        catalog.map { (provider, titles) ->
            HomeRow(
                id = "tmdb_${provider.name}",
                title = context.getString(R.string.trending_on, provider.label),
                style = RowStyle.CATALOG,
                items = titles.map { it.toItem(provider, resolver) },
            )
        }
    }

    private suspend fun catalog(): Map<StreamingProvider, List<TmdbTitle>> {
        if (!settings.recommendationsEnabled) return emptyMap()
        val apiKey = settings.tmdbApiKey.ifBlank { BuildConfig.TMDB_API_KEY }
        if (apiKey.isBlank()) return emptyMap()
        val region = settings.region
        val language = settings.language
        val signature = "$apiKey|$region|$language" // cache invalida se a configuração mudar

        val cached = CatalogCache.value?.takeIf { it.signature == signature }
        if (cached != null && SystemClock.elapsedRealtime() - cached.timestamp < TTL_MS) return cached.data

        val api = TmdbApi(apiKey)
        val data = coroutineScope {
            StreamingProvider.values()
                .map { provider -> async { provider to fetchProvider(api, provider, region, language) } }
                .awaitAll()
        }.filter { it.second.isNotEmpty() }.toMap()

        if (data.isEmpty()) return cached?.data.orEmpty() // falhou: usa cache vencido
        CatalogCache.value = Cached(SystemClock.elapsedRealtime(), signature, data)
        return data
    }

    private suspend fun fetchProvider(
        api: TmdbApi,
        provider: StreamingProvider,
        region: String,
        language: String,
    ): List<TmdbTitle> = coroutineScope {
        val movies = async { safely { api.discover(MediaType.MOVIE, provider.tmdbIds, region, language) } }
        val series = async { safely { api.discover(MediaType.TV, provider.tmdbIds, region, language) } }
        (movies.await().take(PER_TYPE) + series.await().take(PER_TYPE))
            .sortedByDescending { it.popularity }
            .take(MAX_ITEMS)
    }

    private inline fun safely(block: () -> List<TmdbTitle>): List<TmdbTitle> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private fun TmdbTitle.toItem(provider: StreamingProvider, resolver: DeepLinkResolver): HomeItem {
        val kind = context.getString(if (type == MediaType.MOVIE) R.string.type_movie else R.string.type_series)
        val ratingText = if (rating > 0) String.format(Locale.getDefault(), "★ %.1f", rating) else null
        return HomeItem(
            id = "tmdb_${provider.name}_${type.path}_$id",
            title = title,
            subtitle = listOfNotNull(kind, year, ratingText).joinToString(" • "),
            imageUri = Uri.parse(IMAGE_BASE + (backdropPath ?: posterPath)),
            intent = resolver.resolve(title, provider),
            overview = overview,
            source = provider.label,
            badge = kind.uppercase(Locale.getDefault()),
            tag = context.getString(R.string.hero_trending),
        )
    }
}
