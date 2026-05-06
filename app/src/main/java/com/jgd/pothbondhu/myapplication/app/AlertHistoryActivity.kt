package com.jgd.pothbondhu.myapplication.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Locale

class AlertHistoryActivity : AppCompatActivity() {

    private lateinit var alertHistoryManager: AlertHistoryManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AlertHistoryAdapter
    private lateinit var emptyView: LinearLayout
    private lateinit var clearAllBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_history)

        alertHistoryManager = AlertHistoryManager(this)

        setupViews()
        loadAlerts()
    }

    private fun setupViews() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Alert History"

        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        clearAllBtn = findViewById(R.id.clearAllBtn)

        recyclerView.layoutManager = LinearLayoutManager(this)

        clearAllBtn.setOnClickListener {
            showClearAllConfirmation()
        }
    }

    private fun loadAlerts() {
        val alerts = alertHistoryManager.getAllAlerts()

        if (alerts.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            clearAllBtn.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            clearAllBtn.visibility = View.VISIBLE

            adapter = AlertHistoryAdapter(alerts) { alert ->
                showAlertDetails(alert)
            }
            recyclerView.adapter = adapter
        }
    }

    private fun showAlertDetails(alert: AlertHistory) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_alert_details, null)

        val typeIcon = dialogView.findViewById<TextView>(R.id.typeIcon)
        val typeText = dialogView.findViewById<TextView>(R.id.typeText)
        val dateText = dialogView.findViewById<TextView>(R.id.dateText)
        val timeText = dialogView.findViewById<TextView>(R.id.timeText)
        val locationText = dialogView.findViewById<TextView>(R.id.locationText)
        val contactsText = dialogView.findViewById<TextView>(R.id.contactsText)
        val statusText = dialogView.findViewById<TextView>(R.id.statusText)
        val detailsText = dialogView.findViewById<TextView>(R.id.detailsText)
        val viewMapBtn = dialogView.findViewById<Button>(R.id.viewMapBtn)

        typeIcon.text = alert.getIcon()
        typeText.text = alert.type
        dateText.text = alert.getFormattedDate()

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        timeText.text = timeFormat.format(java.util.Date(alert.timestamp))

        locationText.text = if (alert.locationAddress.isNotEmpty()) {
            "📍 ${alert.locationAddress}"
        } else if (alert.locationLat != 0.0) {
            "📍 ${alert.locationLat}, ${alert.locationLon}"
        } else {
            "📍 Location not available"
        }

        contactsText.text = "👥 ${alert.contactsNotified} contact(s) notified"

        statusText.text = alert.status
        statusText.setTextColor(alert.getStatusColor())

        detailsText.text = if (alert.details.isNotEmpty()) {
            alert.details
        } else {
            "Emergency alert was sent to your emergency contacts."
        }

        viewMapBtn.setOnClickListener {
            if (alert.locationLat != 0.0 && alert.locationLon != 0.0) {
                val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${alert.locationLat},${alert.locationLon}")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } else {
                Toast.makeText(this, "Location not available for this alert", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showClearAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear All History")
            .setMessage("Are you sure you want to delete all alert history? This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                alertHistoryManager.clearAllAlerts()
                loadAlerts()
                Toast.makeText(this, "Alert history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}