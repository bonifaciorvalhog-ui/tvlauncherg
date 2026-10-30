package com.example.tvlauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlauncher.databinding.ItemAppCardBinding
import com.example.tvlauncher.databinding.ItemMediaCardBinding
import com.example.tvlauncher.model.HomeItem
import com.example.tvlauncher.model.RowStyle

/** Cartões de uma fileira: banners de apps (APPS) ou imagens 16:9 (FEATURED, CHANNEL, CATALOG). */
class CardAdapter(
    private val style: RowStyle,
    private val onClick: (HomeItem) -> Unit,
) : ListAdapter<HomeItem, RecyclerView.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (style == RowStyle.APPS) {
            AppVH(ItemAppCardBinding.inflate(inflater, parent, false))
        } else {
            MediaVH(ItemMediaCardBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is AppVH -> holder.bind(item)
            is MediaVH -> holder.bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is AppVH -> holder.reset()
            is MediaVH -> holder.reset()
        }
    }

    private inner class AppVH(private val b: ItemAppCardBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.card.setOnFocusChangeListener { v, focused ->
                v.animateFocus(focused, 1.1f)
                b.label.visibility = if (focused) View.VISIBLE else View.INVISIBLE
            }
        }

        fun bind(item: HomeItem) {
            b.label.text = item.title
            b.banner.loadItem(item)
            b.card.setOnClickListener { onClick(item) }
        }

        fun reset() {
            b.card.resetFocusAnimation()
            b.label.visibility = View.INVISIBLE
        }
    }

    private inner class MediaVH(private val b: ItemMediaCardBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.card.setOnFocusChangeListener { v, focused ->
                v.animateFocus(focused, 1.08f)
                b.info.isVisible = focused
            }
        }

        fun bind(item: HomeItem) {
            b.title.text = item.title
            b.meta.text = item.subtitle.orEmpty()
            b.meta.isVisible = !item.subtitle.isNullOrBlank()
            b.overview.text = item.overview.orEmpty()
            b.overview.isVisible = !item.overview.isNullOrBlank()
            b.image.loadItem(item)
            b.card.setOnClickListener { onClick(item) }
        }

        fun reset() {
            b.card.resetFocusAnimation()
            b.info.isVisible = false
        }
    }

    private object Diff : DiffUtil.ItemCallback<HomeItem>() {
        override fun areItemsTheSame(a: HomeItem, b: HomeItem) = a.id == b.id
        override fun areContentsTheSame(a: HomeItem, b: HomeItem) =
            a.title == b.title && a.subtitle == b.subtitle && a.imageUri == b.imageUri
    }
}
