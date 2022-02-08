package me.lucky.silence

import android.Manifest
import android.app.role.RoleManager
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

import me.lucky.silence.databinding.ActivityMainBinding

open class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Preferences
    private var roleManager: RoleManager? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Preferences.SERVICE_ENABLED -> {
                Utils.setSmsReceiverState(
                    this,
                    prefs.isServiceEnabled && prefs.isMessagesChecked,
                )
                updateToggle()
            }
            Preferences.MESSAGES_CHECKED -> {
                Utils.setSmsReceiverState(
                    this,
                    prefs.isServiceEnabled && prefs.isMessagesChecked,
                )
                updateMessages()
            }
            Preferences.CONTACTED_CHECKED -> updateContacted()
            Preferences.REPEATED_CHECKED -> updateRepeated()
        }
    }

    private val registerForCallScreeningRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) prefs.isServiceEnabled = true
        }

    private val registerForContactedPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            var result = true
            for (permission in getContactedPermissions()) {
                result = result && map[permission] == true
            }
            when (result) {
                true -> prefs.isContactedChecked = true
                false -> binding.contactedSwitch.isChecked = false
            }
        }

    private val registerForRepeatedPermissions =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            when (isGranted) {
                true -> prefs.isRepeatedChecked = true
                false -> binding.repeatedSwitch.isChecked = false
            }
        }

    private val registerForMessagesPermissions =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            when (isGranted) {
                true -> prefs.isMessagesChecked = true
                false -> binding.messagesSwitch.isChecked = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
        setup()
    }

    private fun setup() {
        binding.apply {
            contactedSwitch.setOnCheckedChangeListener { _, isChecked ->
                when (!hasContactedPermissions() && isChecked) {
                    true -> requestContactedPermissions()
                    false -> prefs.isContactedChecked = isChecked
                }
            }
            contactedSwitch.setOnLongClickListener {
                showContactedSettings()
                true
            }
            groupsSwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.isGroupsChecked = isChecked
            }
            groupsSwitch.setOnLongClickListener {
                showGroupsSettings()
                true
            }
            repeatedSwitch.setOnCheckedChangeListener { _, isChecked ->
                when (!hasRepeatedPermissions() && isChecked) {
                    true -> requestRepeatedPermissions()
                    false -> prefs.isRepeatedChecked = isChecked
                }
            }
            repeatedSwitch.setOnLongClickListener {
                showRepeatedSettings()
                true
            }
            messagesSwitch.setOnCheckedChangeListener { _, isChecked ->
                when (!hasMessagesPermissions() && isChecked) {
                    true -> requestMessagesPermissions()
                    false -> prefs.isMessagesChecked = isChecked
                }
            }
            stirSwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.isStirChecked = isChecked
            }
            toggle.setOnClickListener {
                when (!Utils.hasCallScreeningRole(this@MainActivity)
                      && !prefs.isServiceEnabled
                ) {
                    true -> requestCallScreeningRole()
                    false -> prefs.isServiceEnabled = !prefs.isServiceEnabled
                }
            }
            toggle.setOnLongClickListener {
                showGeneralSettings()
                true
            }
        }
    }

    private fun init() {
        prefs = Preferences(this)
        roleManager = getSystemService(RoleManager::class.java)
        NotificationManager(this).createNotificationChannels()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) hideStir()
        binding.apply {
            contactedSwitch.isChecked = prefs.isContactedChecked
            groupsSwitch.isChecked = prefs.isGroupsChecked
            repeatedSwitch.isChecked = prefs.isRepeatedChecked
            messagesSwitch.isChecked = prefs.isMessagesChecked
            stirSwitch.isChecked = prefs.isStirChecked
        }
    }

    private fun hideStir() {
        binding.apply {
            stirSpace.visibility = View.GONE
            stirSwitch.visibility = View.GONE
            stirDescription.visibility = View.GONE
        }
    }

    private fun update() {
        updateContacted()
        updateRepeated()
        updateMessages()
        updateToggle()
        if (!Utils.hasCallScreeningRole(this) && prefs.isServiceEnabled)
            Snackbar.make(
                binding.toggle,
                getString(R.string.service_unavailable_popup),
                Snackbar.LENGTH_SHORT,
            ).show()
    }

    override fun onStart() {
        super.onStart()
        prefs.registerListener(prefsListener)
        update()
    }

    override fun onStop() {
        super.onStop()
        prefs.unregisterListener(prefsListener)
    }

    private fun updateRepeated() {
        binding.apply {
            when {
                !hasRepeatedPermissions() && prefs.isRepeatedChecked ->
                    repeatedSwitch.setTextColor(getColor(R.color.icon_color_red))
                else -> repeatedSwitch.setTextColor(groupsSwitch.currentTextColor)
            }
        }
    }

    private fun updateContacted() {
        binding.apply {
            when {
                !hasContactedPermissions() && prefs.isContactedChecked ->
                    contactedSwitch.setTextColor(getColor(R.color.icon_color_red))
                else -> contactedSwitch.setTextColor(groupsSwitch.currentTextColor)
            }
        }
    }

    private fun updateMessages() {
        binding.apply {
            when {
                !hasMessagesPermissions() && prefs.isMessagesChecked ->
                    messagesSwitch.setTextColor(getColor(R.color.icon_color_red))
                else -> messagesSwitch.setTextColor(groupsSwitch.currentTextColor)
            }
        }
    }

    private fun updateToggle() {
        val stringId: Int
        val colorId: Int
        if (prefs.isServiceEnabled) {
            stringId = R.string.toggle_on
            colorId = R.color.icon_color_red
        } else {
            stringId = R.string.toggle_off
            colorId = R.color.icon_color_yellow
        }
        binding.toggle.apply {
            text = getText(stringId)
            setBackgroundColor(getColor(colorId))
        }
    }

    private fun showGroupsSettings() {
        var groups = prefs.groups
        val flags = Group.values()
        MaterialAlertDialogBuilder(this)
            .setMultiChoiceItems(
                resources.getStringArray(R.array.groups),
                flags.map { groups.and(it.value) != 0 }.toBooleanArray()
            ) { _, index, isChecked ->
                val flag = flags[index]
                groups = when (isChecked) {
                    true -> groups.or(flag.value)
                    false -> groups.and(flag.value.inv())
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.groups = groups
            }
            .show()
    }

    private fun showContactedSettings() {
        var contacted = prefs.contacted
        val flags = Contacted.values()
        MaterialAlertDialogBuilder(this)
            .setMultiChoiceItems(
                resources.getStringArray(R.array.contacted),
                flags.map { contacted.and(it.value) != 0 }.toBooleanArray()
            ) { _, index, isChecked ->
                val flag = flags[index]
                contacted = when (isChecked) {
                    true -> contacted.or(flag.value)
                    false -> contacted.and(flag.value.inv())
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.contacted = contacted
            }
            .show()
    }

    private fun showRepeatedSettings() {
        val itemsN = resources.getStringArray(R.array.repeated_settings_n)
        val itemsT = resources.getStringArray(R.array.repeated_settings_t)
        val repeatedSettings = prefs.repeatedSettings
        @Suppress("InflateParams")
        val view = layoutInflater.inflate(R.layout.repeated_settings, null)
        val n = view.findViewById<AutoCompleteTextView>(R.id.repeatedSettingsCount)
        n.setText(repeatedSettings.count.toString())
        n.setAdapter(ArrayAdapter(
            view.context,
            android.R.layout.simple_spinner_dropdown_item,
            itemsN,
        ))
        val t = view.findViewById<AutoCompleteTextView>(R.id.repeatedSettingsMinutes)
        t.setText(repeatedSettings.minutes.toString())
        t.setAdapter(ArrayAdapter(
            view.context,
            android.R.layout.simple_spinner_dropdown_item,
            itemsT,
        ))
        var count = repeatedSettings.count
        var minutes = repeatedSettings.minutes
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.repeatedSettings = RepeatedSettings(count, minutes)
            }
            .create()
        val updateButtonState = {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = count < minutes
        }
        n.setOnItemClickListener { _, _, position, _ ->
            count = itemsN[position].toInt()
            updateButtonState()
        }
        t.setOnItemClickListener { _, _, position, _ ->
            minutes = itemsT[position].toInt()
            updateButtonState()
        }
        dialog.show()
    }

    private fun showGeneralSettings() {
        var general = prefs.generalFlag
        val flags = GeneralFlag.values()
        MaterialAlertDialogBuilder(this)
            .setMultiChoiceItems(
                resources.getStringArray(R.array.general_flag),
                flags.map { general.and(it.value) != 0 }.toBooleanArray(),
            ) { _, index, isChecked ->
                val flag = flags[index]
                general = when (isChecked) {
                    true -> general.or(flag.value)
                    false -> general.and(flag.value.inv())
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.generalFlag = general
            }
            .show()
    }

    private fun requestContactedPermissions() {
        registerForContactedPermissions.launch(getContactedPermissions())
    }

    private fun getContactedPermissions(): Array<String> {
        val contacted = prefs.contacted
        val permissions = mutableListOf<String>()
        for (value in Contacted.values().asSequence().filter { contacted.and(it.value) != 0 }) {
            when (value) {
                Contacted.CALL -> permissions.add(Manifest.permission.READ_CALL_LOG)
                Contacted.MESSAGE -> permissions.add(Manifest.permission.READ_SMS)
            }
        }
        return permissions.toTypedArray()
    }

    private fun requestRepeatedPermissions() {
        registerForRepeatedPermissions.launch(Manifest.permission.READ_CALL_LOG)
    }

    private fun requestMessagesPermissions() {
        registerForMessagesPermissions.launch(Manifest.permission.RECEIVE_SMS)
    }

    private fun requestCallScreeningRole() {
        registerForCallScreeningRole
            .launch(roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    private fun hasContactedPermissions(): Boolean {
        return Utils.hasPermissions(this, *getContactedPermissions())
    }

    private fun hasRepeatedPermissions(): Boolean {
        return Utils.hasPermissions(this, Manifest.permission.READ_CALL_LOG)
    }

    private fun hasMessagesPermissions(): Boolean {
        return Utils.hasPermissions(this, Manifest.permission.RECEIVE_SMS)
    }
}
