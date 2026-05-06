package com.jgd.pothbondhu.myapplication.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AlertHistoryManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("alert_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val maxHistorySize = 50 // Keep last 50 alerts

    companion object {
        private const val KEY_ALERTS = "alerts_list"
    }

    // Save a new alert
    fun saveAlert(alert: AlertHistory) {
        val alerts = getAllAlerts().toMutableList()
        alerts.add(0, alert) // Add to beginning (newest first)

        // Keep only last 50 alerts
        if (alerts.size > maxHistorySize) {
            alerts.removeAt(alerts.size - 1)
        }

        val json = gson.toJson(alerts)
        prefs.edit().putString(KEY_ALERTS, json).apply()
    }

    // Get all alerts
    fun getAllAlerts(): List<AlertHistory> {
        val json = prefs.getString(KEY_ALERTS, "[]")
        val type = object : TypeToken<List<AlertHistory>>() {}.type
        return gson.fromJson(json, type)
    }

    // Clear all alerts
    fun clearAllAlerts() {
        prefs.edit().remove(KEY_ALERTS).apply()
    }

    // Delete specific alert
    fun deleteAlert(alertId: String) {
        val alerts = getAllAlerts().filter { it.id != alertId }
        val json = gson.toJson(alerts)
        prefs.edit().putString(KEY_ALERTS, json).apply()
    }

    // Get alerts by type
    fun getAlertsByType(type: String): List<AlertHistory> {
        return getAllAlerts().filter { it.type == type }
    }

    // Get recent alerts (last 10)
    fun getRecentAlerts(limit: Int = 10): List<AlertHistory> {
        return getAllAlerts().take(limit)
    }
}