package com.jgd.pothbondhu.myapplication.app

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial

class ProfileFragment : Fragment() {

    private lateinit var authRepository: AuthRepository

    // UI Elements
    private lateinit var avatarInitials: TextView
    private lateinit var profileName: TextView
    private lateinit var profileLocation: TextView
    private lateinit var contactCount: TextView
    private lateinit var alertCount: TextView
    private lateinit var bloodGroupValue: TextView
    private lateinit var allergiesValue: TextView
    private lateinit var medicationsValue: TextView
    private lateinit var conditionsValue: TextView
    private lateinit var emailValue: TextView
    private lateinit var phoneValue: TextView
    private lateinit var contactsContainer: LinearLayout

    // Safety Settings UI Elements
    private lateinit var crashDetectionSwitch: SwitchMaterial
    private lateinit var locationSwitch: SwitchMaterial
    private lateinit var sensitivitySeekBar: SeekBar
    private lateinit var sensitivityValue: TextView
    private lateinit var crashStatusText: TextView
    private lateinit var locationStatusText: TextView
    private lateinit var sosMethodValue: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authRepository = AuthRepository()

        initializeViews(view)
        loadUserData()
        setupClickListeners()
        setupSafetySettingsListeners(view)
        setupSOSButton(view)
    }

    private fun initializeViews(view: View) {
        avatarInitials = view.findViewById(R.id.avatarInitials)
        profileName = view.findViewById(R.id.profileName)
        profileLocation = view.findViewById(R.id.profileLocation)
        contactCount = view.findViewById(R.id.contactCount)
        alertCount = view.findViewById(R.id.alertCount)
        bloodGroupValue = view.findViewById(R.id.bloodGroupValue)
        allergiesValue = view.findViewById(R.id.allergiesValue)
        medicationsValue = view.findViewById(R.id.medicationsValue)
        conditionsValue = view.findViewById(R.id.conditionsValue)
        emailValue = view.findViewById(R.id.emailValue)
        phoneValue = view.findViewById(R.id.phoneValue)
        contactsContainer = view.findViewById(R.id.contactsContainer)

        // Safety Settings
        crashDetectionSwitch = view.findViewById(R.id.crashDetectionSwitch)
        locationSwitch = view.findViewById(R.id.locationSwitch)
        sensitivitySeekBar = view.findViewById(R.id.sensitivitySeekBar)
        sensitivityValue = view.findViewById(R.id.sensitivityValue)
        crashStatusText = view.findViewById(R.id.crashStatusText)
        locationStatusText = view.findViewById(R.id.locationStatusText)
        sosMethodValue = view.findViewById(R.id.sosMethodValue)
    }

    private fun loadUserData() {
        val userId = authRepository.getCurrentUserId()
        if (userId != null) {
            authRepository.getUserProfile(userId) { success, user ->
                if (success && user != null) {
                    profileName.text = user.userName.ifEmpty { "PothBondhu User" }
                    emailValue.text = user.userEmail
                    phoneValue.text = user.userPhone.ifEmpty { "Not set" }
                    bloodGroupValue.text = user.bloodGroup.ifEmpty { "Not set" }
                    allergiesValue.text = user.allergies.ifEmpty { "None" }
                    medicationsValue.text = user.medications.ifEmpty { "None" }
                    conditionsValue.text = user.conditions?.ifEmpty { "None" } ?: "None"

                    // Set avatar initials
                    val initials = user.userName.take(2).uppercase()
                    avatarInitials.text = if (initials.length >= 2) initials else "PB"
                }
            }
        }

        // Load emergency contacts
        loadEmergencyContacts()

        // Load alert count
        alertCount.text = "12"

        // Load saved preferences for safety settings
        loadSafetySettings()
    }

    private fun loadSafetySettings() {
        val prefs = requireContext().getSharedPreferences("app_prefs", AppCompatActivity.MODE_PRIVATE)

        // Crash Detection
        val isCrashOn = prefs.getBoolean("crash_detection", true)
        crashDetectionSwitch.isChecked = isCrashOn
        crashStatusText.text = if (isCrashOn) "Enabled" else "Disabled"
        crashStatusText.setTextColor(if (isCrashOn)
            android.graphics.Color.parseColor("#4CAF50")
        else android.graphics.Color.parseColor("#F44336"))

        // Background Location
        val isLocationOn = prefs.getBoolean("background_location", true)
        locationSwitch.isChecked = isLocationOn
        locationStatusText.text = if (isLocationOn) "Always On" else "Off"

        // Sensitivity
        val sensitivity = prefs.getInt("detection_sensitivity", 8)
        sensitivitySeekBar.progress = sensitivity
        val gForce = sensitivity + 4
        sensitivityValue.text = when (gForce) {
            in 4..6 -> "Low (${gForce}G)"
            in 7..9 -> "Medium (${gForce}G)"
            else -> "High (${gForce}G)"
        }

        // SOS Method
        val sosMethod = prefs.getString("sos_method", "WhatsApp + SMS")
        sosMethodValue.text = sosMethod
    }

    private fun loadEmergencyContacts() {
        authRepository.getEmergencyContacts { success, contacts ->
            if (success) {
                contactCount.text = contacts.size.toString()
                contactsContainer.removeAllViews()

                if (contacts.isNotEmpty()) {
                    for (contact in contacts) {
                        val contactView = createContactView(contact)
                        contactsContainer.addView(contactView)

                        if (contacts.indexOf(contact) < contacts.size - 1) {
                            val divider = View(requireContext())
                            divider.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                1
                            )
                            divider.setBackgroundColor(resources.getColor(R.color.beige_dark))
                            contactsContainer.addView(divider)
                        }
                    }
                } else {
                    val noContactsText = TextView(requireContext()).apply {
                        text = "No emergency contacts added yet.\nTap '+ Add Emergency Contact' to add."
                        textSize = 12f
                        setTextColor(resources.getColor(R.color.navy_light))
                        gravity = android.view.Gravity.CENTER
                        setPadding(16, 24, 16, 24)
                    }
                    contactsContainer.addView(noContactsText)
                }
            }
        }
    }

    private fun createContactView(contact: EmergencyContact): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 12, 8, 12)
        }

        val avatar = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40)
            text = contact.name.take(2).uppercase()
            textSize = 14f
            setTextColor(resources.getColor(R.color.beige_light))
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.contact_avatar)
        }

        val textLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(12, 0, 0, 0)
        }

        val nameText = TextView(requireContext()).apply {
            text = "${contact.name} (${getRelationshipString(contact.name)})"
            textSize = 14f
            setTextColor(resources.getColor(R.color.navy_dark))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val phoneText = TextView(requireContext()).apply {
            text = "📞 ${contact.phoneNumber}"
            textSize = 12f
            setTextColor(resources.getColor(R.color.navy_light))
        }

        textLayout.addView(nameText)
        textLayout.addView(phoneText)
        layout.addView(avatar)
        layout.addView(textLayout)

        val callBtn = Button(requireContext()).apply {
            text = "Call"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.navy_primary))
            setTextColor(ContextCompat.getColor(requireContext(), R.color.beige_light))
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                Toast.makeText(context, "Calling ${contact.name}...", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(callBtn)
        return layout
    }

    private fun getRelationshipString(name: String): String {
        return when {
            name.contains("Fatima", ignoreCase = true) -> "Mom"
            name.contains("Karim", ignoreCase = true) -> "Dad"
            name.contains("Roni", ignoreCase = true) -> "Sister"
            else -> "Contact"
        }
    }

    private fun setupClickListeners() {
        view?.findViewById<Button>(R.id.editProfileBtn)?.setOnClickListener {
            showEditProfileDialog()
        }

        view?.findViewById<Button>(R.id.editBloodGroupBtn)?.setOnClickListener {
            showEditBloodGroupDialog()
        }

        view?.findViewById<Button>(R.id.editAllergiesBtn)?.setOnClickListener {
            showEditAllergiesDialog()
        }

        view?.findViewById<Button>(R.id.editMedsBtn)?.setOnClickListener {
            showEditMedicationsDialog()
        }

        view?.findViewById<Button>(R.id.editConditionsBtn)?.setOnClickListener {
            showEditConditionsDialog()
        }

        view?.findViewById<Button>(R.id.addContactBtn)?.setOnClickListener {
            showAddContactDialog()
        }

        view?.findViewById<TextView>(R.id.changePassword)?.setOnClickListener {
            showChangePasswordDialog()
        }

        view?.findViewById<TextView>(R.id.privacyPolicy)?.setOnClickListener {
            Toast.makeText(context, "Opening Privacy Policy...", Toast.LENGTH_SHORT).show()
        }

        view?.findViewById<TextView>(R.id.termsOfService)?.setOnClickListener {
            Toast.makeText(context, "Opening Terms of Service...", Toast.LENGTH_SHORT).show()
        }

        view?.findViewById<TextView>(R.id.downloadData)?.setOnClickListener {
            Toast.makeText(context, "Downloading your data...", Toast.LENGTH_SHORT).show()
        }

        view?.findViewById<TextView>(R.id.deleteAccount)?.setOnClickListener {
            showDeleteAccountConfirmation()
        }

        view?.findViewById<Button>(R.id.editSosMethodBtn)?.setOnClickListener {
            showSosMethodDialog()
        }
    }

    private fun setupSafetySettingsListeners(view: View) {
        val prefs = requireContext().getSharedPreferences("app_prefs", AppCompatActivity.MODE_PRIVATE)

        // Crash Detection Switch
        crashDetectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            crashStatusText.text = if (isChecked) "Enabled" else "Disabled"
            crashStatusText.setTextColor(if (isChecked)
                android.graphics.Color.parseColor("#4CAF50")
            else android.graphics.Color.parseColor("#F44336"))
            prefs.edit().putBoolean("crash_detection", isChecked).apply()
            Toast.makeText(context, "Crash detection ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        // Background Location Switch
        locationSwitch.setOnCheckedChangeListener { _, isChecked ->
            locationStatusText.text = if (isChecked) "Always On" else "Off"
            prefs.edit().putBoolean("background_location", isChecked).apply()
            Toast.makeText(context, "Background location ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        // Sensitivity SeekBar
        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val gForce = progress + 4
                sensitivityValue.text = when (gForce) {
                    in 4..6 -> "Low (${gForce}G)"
                    in 7..9 -> "Medium (${gForce}G)"
                    else -> "High (${gForce}G)"
                }
                if (fromUser) {
                    prefs.edit().putInt("detection_sensitivity", progress).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun showSosMethodDialog() {
        val options = arrayOf("WhatsApp Only", "SMS Only", "WhatsApp + SMS", "Call Only")
        AlertDialog.Builder(requireContext())
            .setTitle("Select SOS Method")
            .setItems(options) { _, which ->
                val method = options[which]
                sosMethodValue.text = method
                requireContext().getSharedPreferences("app_prefs", AppCompatActivity.MODE_PRIVATE)
                    .edit().putString("sos_method", method).apply()
                Toast.makeText(context, "SOS method updated to $method", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setupSOSButton(view: View) {
        val profileSosFab = view.findViewById<ExtendedFloatingActionButton>(R.id.profileSosFab)
        profileSosFab.setOnClickListener {
            sendSOS()
        }
    }

    private fun sendSOS() {
        authRepository.getEmergencyContacts { success, contacts ->
            if (success && contacts.isNotEmpty()) {
                val method = requireContext().getSharedPreferences("app_prefs", AppCompatActivity.MODE_PRIVATE)
                    .getString("sos_method", "WhatsApp + SMS")
                Toast.makeText(
                    context,
                    "🚨 SOS Alert Sent via $method to ${contacts.size} contacts!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "⚠️ No emergency contacts added. Please add contacts first.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
        val editText = dialogView.findViewById<EditText>(R.id.editText)
        editText.hint = "Full Name"
        editText.setText(profileName.text)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Profile Name")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotBlank()) {
                    profileName.text = newName
                    val userId = authRepository.getCurrentUserId()
                    if (userId != null) {
                        authRepository.saveUserProfile(userId, mapOf("userName" to newName)) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditBloodGroupDialog() {
        val options = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Blood Group")
            .setItems(options) { _, which ->
                val selected = options[which]
                bloodGroupValue.text = selected
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    authRepository.saveUserProfile(userId, mapOf("bloodGroup" to selected)) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showEditAllergiesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
        val editText = dialogView.findViewById<EditText>(R.id.editText)
        editText.hint = "Allergies (comma separated)"
        editText.setText(allergiesValue.text.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Allergies")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newAllergies = editText.text.toString()
                allergiesValue.text = newAllergies
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    authRepository.saveUserProfile(userId, mapOf("allergies" to newAllergies)) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditMedicationsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
        val editText = dialogView.findViewById<EditText>(R.id.editText)
        editText.hint = "Current Medications"
        editText.setText(medicationsValue.text.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Medications")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newMeds = editText.text.toString()
                medicationsValue.text = newMeds
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    authRepository.saveUserProfile(userId, mapOf("medications" to newMeds)) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditConditionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
        val editText = dialogView.findViewById<EditText>(R.id.editText)
        editText.hint = "Medical Conditions"
        editText.setText(conditionsValue.text.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Medical Conditions")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newConditions = editText.text.toString()
                conditionsValue.text = newConditions
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    authRepository.saveUserProfile(userId, mapOf("conditions" to newConditions)) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddContactDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.contactName)
        val phoneInput = dialogView.findViewById<EditText>(R.id.contactPhone)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Emergency Contact")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString()
                val phone = phoneInput.text.toString()
                if (name.isNotBlank() && phone.isNotBlank()) {
                    authRepository.addEmergencyContact(name, phone) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (success) loadEmergencyContacts()
                    }
                } else {
                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChangePasswordDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Change Password")
            .setMessage("A password reset link will be sent to your email.")
            .setPositiveButton("Send") { _, _ ->
                authRepository.resetPassword(emailValue.text.toString()) { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteAccountConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Are you sure? This action cannot be undone. All your data will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                authRepository.deleteUser { success, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (success) {
                        activity?.finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}