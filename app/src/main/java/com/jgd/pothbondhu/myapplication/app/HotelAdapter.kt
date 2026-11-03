package com.jgd.pothbondhu.myapplication.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat
import java.util.Locale

class HotelAdapter(private val onItemClick: (Hotel) -> Unit) :
    RecyclerView.Adapter<HotelAdapter.HotelViewHolder>() {

    private var hotels: List<Hotel> = listOf()

    fun updateHotels(newHotels: List<Hotel>) {
        this.hotels = newHotels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hotel, parent, false)
        return HotelViewHolder(view)
    }

    override fun onBindViewHolder(holder: HotelViewHolder, position: Int) {
        val hotel = hotels[position]
        holder.bind(hotel)
        holder.itemView.setOnClickListener { onItemClick(hotel) }
    }

    override fun getItemCount(): Int = hotels.size

    class HotelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.hotelName)
        private val ratingText: TextView = itemView.findViewById(R.id.hotelRating)
        private val distanceText: TextView = itemView.findViewById(R.id.hotelDistance)
        private val priceText: TextView = itemView.findViewById(R.id.hotelPrice)

        fun bind(hotel: Hotel) {
            nameText.text = hotel.name
            
            val formattedRating = String.format(Locale.getDefault(), "%.1f", hotel.rating)
            ratingText.text = if (hotel.rating > 0) "⭐ $formattedRating" else "⭐ New"
            
            val formattedDistance = if (hotel.distance < 1000) {
                "${hotel.distance.toInt()}m"
            } else {
                "${DecimalFormat("#.#").format(hotel.distance / 1000)}km"
            }
            distanceText.text = "📍 $formattedDistance"
            
            if (hotel.pricePerNight > 0) {
                priceText.text = "BDT ${String.format(Locale.getDefault(), "%.0f", hotel.pricePerNight)}"
            } else {
                priceText.text = "Price on request"
            }
        }
    }
}
