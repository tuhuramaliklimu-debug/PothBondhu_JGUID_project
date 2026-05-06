package com.jgd.pothbondhu.myapplication.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class AlertHistoryAdapter(
    private val alerts: List<AlertHistory>,
    private val onItemClick: (AlertHistory) -> Unit
) : RecyclerView.Adapter<AlertHistoryAdapter.AlertViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert_history, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val alert = alerts[position]
        holder.bind(alert)
        holder.itemView.setOnClickListener { onItemClick(alert) }
    }

    override fun getItemCount(): Int = alerts.size

    class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconText: TextView = itemView.findViewById(R.id.iconText)
        private val typeText: TextView = itemView.findViewById(R.id.typeText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val locationText: TextView = itemView.findViewById(R.id.locationText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val card: CardView = itemView.findViewById(R.id.cardView)

        fun bind(alert: AlertHistory) {
            iconText.text = alert.getIcon()
            typeText.text = alert.type
            dateText.text = alert.getFormattedDate().split(" ")[0]
            timeText.text = alert.getFormattedDate().split(" ")[1]

            locationText.text = if (alert.locationAddress.isNotEmpty()) {
                alert.locationAddress.take(30)
            } else {
                "Location recorded"
            }

            statusText.text = alert.status
            statusText.setTextColor(alert.getStatusColor())

            // Set card background based on type
            when (alert.type) {
                "SOS" -> card.setCardBackgroundColor(0xFFFFEBEE.toInt())
                "Crash" -> card.setCardBackgroundColor(0xFFFFF3E0.toInt())
                "Journey Alert" -> card.setCardBackgroundColor(0xFFE8F5E9.toInt())
                else -> card.setCardBackgroundColor(0xFFF5F5F5.toInt())
            }
        }
    }
}