package com.jgd.pothbondhu.myapplication.app

data class Hotel(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val pricePerNight: Double,
    val phone: String,
    val amenities: List<String>,
    val distance: Double,
    val reviewCount: Int
)
