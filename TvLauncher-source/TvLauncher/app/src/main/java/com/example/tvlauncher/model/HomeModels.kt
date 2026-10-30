package com.example.tvlauncher.model

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri

enum class RowStyle { FEATURED, APPS, CHANNEL, CATALOG }

data class HomeItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val imageUri: Uri? = null,
    val intent: Intent? = null,
    val overview: String? = null,
    /** Imagem larga do destaque (hero). Se nula, usa [imageUri]. */
    val heroUri: Uri? = null,
    /** Origem mostrada acima do título do destaque (app, canal ou plataforma). */
    val source: String? = null,
    /** Selo ao lado dos indicadores do destaque (ex.: "SERIES"). */
    val badge: String? = null,
    /** Prefixo do subtítulo do destaque (ex.: "Trending"). */
    val tag: String? = null,
) {
    /** Banner/ícone local (apps). Fora do equals de propósito. */
    var drawable: Drawable? = null
}

data class HomeRow(
    val id: String,
    val title: String,
    val style: RowStyle,
    val items: List<HomeItem>,
)

/** Itens da lista vertical da home: o destaque rola junto com as fileiras. */
sealed class HomeEntry {
    data class Hero(val items: List<HomeItem>) : HomeEntry()
    data class Row(val row: HomeRow) : HomeEntry()
}
