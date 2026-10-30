package com.example.tvlauncher.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlauncher.R
import com.example.tvlauncher.databinding.ItemAppCardBinding
import com.example.tvlauncher.model.HomeItem

/** Grade completa de aplicativos (aba Apps e fallback sem recomendações nem catálogo). */
class AppAdapter(
    private val onClick: (HomeItem) -> Unit,
) : ListAdapter<HomeItem, AppAdapter.VH>(Diff) {

    inner class VH(private val b: ItemAppCardBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            // Na grade o cartão é maior que o da fileira de favoritos.
            val res = b.root.resources
            b.card.layoutParams = b.card.layoutParams.apply {
                width = res.getDimensionPixelSize(R.dimen.card_app_grid_width)
                height = res.getDimensionPixelSize(R.dimen.card_app_grid_height)
            }
            b.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            b.label.setPadding(b.label.paddingLeft, (16 * res.displayMetrics.density).toInt(), b.label.paddingRight, b.label.paddingBottom)
            b.card.setOnFocusChangeListener { v, focused ->
                v.animateFocus(focused, 1.1f)
                b.label.visibility = if (focused) View.VISIBLE else View.INVISIBLE
            }
        }

        fun bind(item: HomeItem) {
            b.label.text = item.title
            b.label.visibility = if (b.card.isFocused) View.VISIBLE else View.INVISIBLE
            b.banner.loadItem(item)
            b.card.setOnClickListener { onClick(item) }
        }

        fun reset() {
            b.card.resetFocusAnimation()
            b.label.visibility = View.INVISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAppCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: VH) = holder.reset()

    private object Diff : DiffUtil.ItemCallback<HomeItem>() {
        override fun areItemsTheSame(a: HomeItem, b: HomeItem) = a.id == b.id
        override fun areContentsTheSame(a: HomeItem, b: HomeItem) =
            a.title == b.title && a.subtitle == b.subtitle && a.imageUri == b.imageUri
    }
}
