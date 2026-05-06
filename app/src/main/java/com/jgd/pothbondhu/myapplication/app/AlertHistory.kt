package com.jgd.pothbondhu.myapplication.app

data class AlertHistory(
    val id: String = "",
    val type: String = "", // "SOS", "Crash", "Journey Alert"
    val timestamp: Long = System.currentTimeMillis(),
    val locationLat: Double = 0.0,
    val locationLon: Double = 0.0,
    val locationAddress: String = "",
    val contactsNotified: Int = 0,
    val status: String = "", // "Sent", "Failed", "Cancelled"
    val details: String = ""
) {
    // Helper function to get formatted date
    fun getFormattedDate(): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a", java.util.Locale.getDefault())
        return format.format(date)
    }

    // Helper function to get icon based on type
    fun getIcon(): String {
        return when (type) {
            "SOS" -> "🚨"
            "Crash" -> "💥"
            "Journey Alert" -> "⏱️"
            else -> "📋"
        }
    }

    // Helper function to get color based on status
    fun getStatusColor(): Int {
        return when (status) {
            "Sent" -> android.graphics.Color.parseColor("#4CAF50")
            "Failed" -> android.graphics.Color.parseColor("#F44336")
            "Cancelled" -> android.graphics.Color.parseColor("#FF9800")
            else -> android.graphics.Color.parseColor("#9E9E9E")
        }
    }
}