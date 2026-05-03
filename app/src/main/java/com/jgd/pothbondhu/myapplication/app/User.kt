package com.jgd.pothbondhu.myapplication.app

data class User(
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userPhone: String = "",
    val bloodGroup: String = "",
    val allergies: String = "",
    val medications: String = "",
    val conditions: String = "",
    val location: String = "Sylhet, Bangladesh"
)