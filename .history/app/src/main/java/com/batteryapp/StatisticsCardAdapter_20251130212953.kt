package com.batteryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class StatisticsCardAdapter(
    private val cards: List<StatisticsCard>,
    private val onCardClick: (StatisticsCard) -> Unit
) : RecyclerView.Adapter<StatisticsCardAdapter.CardViewHolder>() {

    data class StatisticsCard(
        val title: String,
        val value: String,
        val description: String,
        val subValue: String? = null
    )

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.cardView)
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        val valueTextView: TextView = itemView.findViewById(R.id.valueTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        val subValueTextView: TextView? = itemView.findViewById(R.id.subValueTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_statistics_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        holder.titleTextView.text = card.title
        holder.valueTextView.text = card.value
        holder.descriptionTextView.text = card.description
        holder.subValueTextView?.text = card.subValue
        holder.cardView.setOnClickListener {
            onCardClick(card)
        }
    }

    override fun getItemCount(): Int {
        return cards.size
    }
}
