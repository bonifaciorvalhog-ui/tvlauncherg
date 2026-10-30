package com.example.tvlauncher.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.example.tvlauncher.R
import com.example.tvlauncher.model.HomeItem
import com.example.tvlauncher.model.HomeRow
import com.example.tvlauncher.model.RowStyle

class ChannelRepository(private val context: Context) {

    private companion object {
        const val MAX_ITEMS = 20
    }

    /** Linha de destaque (Watch Next) primeiro, depois um canal por linha. */
    fun loadRows(): List<HomeRow> = try {
        val watchNext = loadWatchNext()
        val channels = loadChannelRows()
        when {
            watchNext != null -> listOf(watchNext) + channels
            channels.isNotEmpty() -> listOf(channels.first().copy(style = RowStyle.FEATURED)) + channels.drop(1)
            else -> emptyList()
        }
    } catch (e: SecurityException) {
        emptyList() // READ_EPG_DATA não concedida (app fora de /system/priv-app)
    } catch (e: RuntimeException) {
        emptyList() // TvProvider ausente nesta ROM
    }

    private fun loadWatchNext(): HomeRow? {
        val items = query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION) {
            WatchNextProgram.fromCursor(it).let { p ->
                HomeItem("wn_${p.id}", p.title.orEmpty(), p.description, p.posterArtUri, p.intent, source = context.getString(R.string.watch_next))
            }
        }.take(MAX_ITEMS)
        return items.takeIf { it.isNotEmpty() }
            ?.let { HomeRow("watch_next", context.getString(R.string.watch_next), RowStyle.FEATURED, it) }
    }

    private fun loadChannelRows(): List<HomeRow> {
        val channels = query(TvContractCompat.Channels.CONTENT_URI, Channel.PROJECTION) {
            Channel.fromCursor(it)
        }.filter { it.isBrowsable && it.type == TvContractCompat.Channels.TYPE_PREVIEW }

        return channels.mapNotNull { channel ->
            val programs = query(
                TvContractCompat.buildPreviewProgramsUriForChannel(channel.id),
                PreviewProgram.PROJECTION,
            ) { PreviewProgram.fromCursor(it) }
                .sortedByDescending { it.weight }
                .take(MAX_ITEMS)
                .map { p ->
                    HomeItem("pp_${p.id}", p.title.orEmpty(), p.description, p.posterArtUri, p.intent, source = channel.displayName?.toString())
                }
            programs.takeIf { it.isNotEmpty() }?.let {
                HomeRow("ch_${channel.id}", channel.displayName?.toString().orEmpty(), RowStyle.CHANNEL, it)
            }
        }
    }

    private fun <T> query(uri: Uri, projection: Array<String>, map: (Cursor) -> T): List<T> =
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(map(c)) }
        }.orEmpty()
}
