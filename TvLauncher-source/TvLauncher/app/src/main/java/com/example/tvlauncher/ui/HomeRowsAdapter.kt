package com.example.tvlauncher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlauncher.R
import com.example.tvlauncher.databinding.ItemHeroBinding
import com.example.tvlauncher.databinding.ItemRowBinding
import com.example.tvlauncher.model.HomeEntry
import com.example.tvlauncher.model.HomeItem
import com.example.tvlauncher.model.HomeRow
import com.example.tvlauncher.model.RowStyle

/** Lista vertical da home: destaque (opcional, sempre o primeiro) seguido das fileiras. */
class HomeRowsAdapter(
    private val onClick: (HomeItem) -> Unit,
) : ListAdapter<HomeEntry, RecyclerView.ViewHolder>(Diff) {

    private var heroHolder: HeroVH? = null
    private var heroIndex = 0

    /** Passa ao próximo item do destaque (chamado pelo temporizador da tela). */
    fun advanceHero() {
        val hero = currentList.firstOrNull() as? HomeEntry.Hero ?: return
        if (hero.items.size < 2) return
        heroIndex = (heroIndex + 1) % hero.items.size
        heroHolder?.bind(hero.items, heroIndex)
    }

    // O estilo vira viewType: holders só são reciclados entre linhas do mesmo tipo.
    override fun getItemViewType(position: Int) = when (val entry = getItem(position)) {
        is HomeEntry.Hero -> TYPE_HERO
        is HomeEntry.Row -> entry.row.style.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HERO) {
            HeroVH(ItemHeroBinding.inflate(inflater, parent, false))
        } else {
            val binding = ItemRowBinding.inflate(inflater, parent, false)
            RowVH(binding, CardAdapter(RowStyle.values()[viewType], onClick))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = getItem(position)) {
            is HomeEntry.Hero -> {
                heroHolder = holder as HeroVH
                heroIndex = heroIndex.coerceIn(0, (entry.items.size - 1).coerceAtLeast(0))
                holder.bind(entry.items, heroIndex)
            }
            is HomeEntry.Row -> (holder as RowVH).bind(entry.row)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder === heroHolder) heroHolder = null
    }

    class RowVH(private val binding: ItemRowBinding, private val cards: CardAdapter) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.rowList.adapter = cards
        }

        fun bind(row: HomeRow) {
            binding.rowTitle.text = row.title
            cards.submitList(row.items)
        }
    }

    class HeroVH(private val binding: ItemHeroBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(items: List<HomeItem>, index: Int) {
            val item = items.getOrNull(index)
            val context = binding.root.context

            binding.heroSource.text = item?.source.orEmpty()
            binding.heroTitle.text = item?.title.orEmpty()

            val body = item?.overview?.takeIf { it.isNotBlank() } ?: item?.subtitle
            binding.heroSubtitle.text = listOfNotNull(item?.tag, body).joinToString(" | ")

            binding.heroDots.count = if (items.size > 1) items.size else 0
            binding.heroDots.index = index

            binding.heroBadge.text = item?.badge.orEmpty()
            binding.heroBadge.isVisible = !item?.badge.isNullOrBlank()
            binding.heroAvailable.text = item?.source?.let { context.getString(R.string.hero_available, it) }.orEmpty()

            binding.heroImage.loadHero(item?.heroUri ?: item?.imageUri)
        }
    }

    private object Diff : DiffUtil.ItemCallback<HomeEntry>() {
        override fun areItemsTheSame(a: HomeEntry, b: HomeEntry) = when {
            a is HomeEntry.Hero && b is HomeEntry.Hero -> true
            a is HomeEntry.Row && b is HomeEntry.Row -> a.row.id == b.row.id
            else -> false
        }

        override fun areContentsTheSame(a: HomeEntry, b: HomeEntry) = when {
            a is HomeEntry.Hero && b is HomeEntry.Hero ->
                a.items.map { it.id } == b.items.map { it.id }
            a is HomeEntry.Row && b is HomeEntry.Row ->
                a.row.title == b.row.title && a.row.items.size == b.row.items.size &&
                    a.row.items.zip(b.row.items).all { (x, y) ->
                        x.id == y.id && x.title == y.title && x.imageUri == y.imageUri
                    }
            else -> false
        }
    }

    private companion object {
        const val TYPE_HERO = 100
    }
}
