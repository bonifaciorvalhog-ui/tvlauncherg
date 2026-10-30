package com.example.tvlauncher.setup

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tvlauncher.R
import com.example.tvlauncher.databinding.ItemSetupCardBinding
import com.example.tvlauncher.databinding.ItemSetupOptionBinding

sealed class SetupRow(val key: String) {
    /** Escolha de lista (idioma/região): ao clicar, o assistente avança. */
    data class Option(val code: String, val label: String) : SetupRow("opt_$code")

    data class Action(val id: String, val label: String, val primary: Boolean = false) : SetupRow("action_$id")

    /** [checked] = null: sem caixa de seleção. [title] = null: só texto (parágrafo de documento). */
    data class Card(
        val id: String,
        val title: String?,
        val description: String?,
        val checked: Boolean? = null,
    ) : SetupRow("card_$id")
}

class SetupAdapter(
    private val onClick: (SetupRow) -> Unit,
) : ListAdapter<SetupRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) =
        if (getItem(position) is SetupRow.Card) TYPE_CARD else TYPE_OPTION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CARD) {
            CardVH(ItemSetupCardBinding.inflate(inflater, parent, false))
        } else {
            OptionVH(ItemSetupOptionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = getItem(position)
        when {
            holder is CardVH && row is SetupRow.Card -> holder.bind(row)
            holder is OptionVH -> holder.bind(row)
        }
    }

    private inner class OptionVH(private val b: ItemSetupOptionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: SetupRow) {
            when (row) {
                is SetupRow.Option -> {
                    b.root.text = row.label
                    b.root.setTypeface(null, Typeface.NORMAL)
                }
                is SetupRow.Action -> {
                    b.root.text = row.label
                    b.root.setTypeface(null, if (row.primary) Typeface.BOLD else Typeface.NORMAL)
                }
                is SetupRow.Card -> Unit
            }
            b.root.setOnClickListener { onClick(row) }
        }
    }

    private inner class CardVH(private val b: ItemSetupCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: SetupRow.Card) {
            b.title.text = row.title
            b.title.visibility = if (row.title == null) View.GONE else View.VISIBLE
            b.description.text = row.description
            b.description.visibility = if (row.description == null) View.GONE else View.VISIBLE
            b.checkbox.visibility = if (row.checked == null) View.GONE else View.VISIBLE
            if (row.checked != null) {
                b.checkbox.setImageResource(if (row.checked) R.drawable.ic_checkbox_on else R.drawable.ic_checkbox_off)
            }
            b.root.setOnClickListener { onClick(row) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<SetupRow>() {
        override fun areItemsTheSame(a: SetupRow, b: SetupRow) = a.key == b.key
        override fun areContentsTheSame(a: SetupRow, b: SetupRow) = a == b
    }

    private companion object {
        const val TYPE_OPTION = 0
        const val TYPE_CARD = 1
    }
}
