package com.example.tvlauncher.data.tmdb

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * O TMDB não informa o ID do título dentro de cada plataforma, então o link abre o app dela.
 * Ordem: app da plataforma -> busca de trailer no YouTube -> busca no navegador.
 */
class DeepLinkResolver(context: Context) {

    private val pm = context.packageManager
    private val launchCache = HashMap<String, Intent?>()

    fun resolve(title: String, provider: StreamingProvider): Intent =
        provider.packages.firstNotNullOfOrNull(::launchIntent)
            ?: youTubeSearch("$title trailer")
            ?: browserSearch("$title ${provider.label} assistir")

    private fun launchIntent(pkg: String): Intent? = launchCache.getOrPut(pkg) {
        pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    }

    private fun youTubeSearch(query: String): Intent? {
        val uri = Uri.parse("https://www.youtube.com/results")
            .buildUpon().appendQueryParameter("search_query", query).build()
        return YOUTUBE_PACKAGES.firstNotNullOfOrNull { pkg ->
            Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).takeIf { it.resolveActivity(pm) != null }
        }
    }

    /** Último recurso. Sem navegador na ROM, o clique mostra o aviso "Aplicativo indisponível". */
    private fun browserSearch(query: String): Intent {
        val uri = Uri.parse("https://www.google.com/search")
            .buildUpon().appendQueryParameter("q", query).build()
        return Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    }

    private companion object {
        val YOUTUBE_PACKAGES = listOf("com.google.android.youtube.tv", "com.google.android.youtube")
    }
}
