package org.ole.planet.myplanet.ui.sync

import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import io.realm.Sort
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.utilities.ServerConfigUtils

fun SyncActivity.showConfigurationUIElements(
    binding: DialogServerUrlBinding,
    manualSelected: Boolean,
    dialog: MaterialDialog,
) {
    serverAddresses.visibility = if (manualSelected) View.GONE else View.VISIBLE
    syncToServerText.visibility = if (manualSelected) View.GONE else View.VISIBLE
    positiveAction.visibility = if (manualSelected) View.VISIBLE else View.GONE
    dialog.getActionButton(DialogAction.NEUTRAL).text =
        if (manualSelected) {
            getString(R.string.btn_sync_save)
        } else {
            if (showAdditionalServers) {
                getString(R.string.show_less)
            } else {
                getString(R.string.show_more)
            }
        }
    binding.ltAdvanced.visibility = if (manualSelected) View.VISIBLE else View.GONE
    binding.switchServerUrl.visibility = if (manualSelected) View.VISIBLE else View.GONE

    if (manualSelected) {
        setupManualUi(binding)
    } else {
        setupServerListUi(binding, dialog)
    }
}

fun SyncActivity.performSync(dialog: MaterialDialog) {
    Log.d("ServerSync", "=== performSync called ===")
    serverConfigAction = "sync"
    val protocol = "${settings.getString("serverProtocol", "")}"
    var url = "${serverUrl.text}"
    val pin = "${serverPassword.text}"

    Log.d("ServerSync", "User input - URL: $url, Protocol: $protocol")
    Log.d("ServerSync", "PIN provided: ${if (pin.isEmpty()) "NO" else "YES"}")
    editor.putString("serverURL", url).apply()
    editor.putString("serverPin", pin).apply()
    Log.d("ServerSync", "Saved to preferences - serverURL: $url, serverPin: ${if (pin.isEmpty()) "(empty)" else "(set)"}")
    url = protocol + url
    Log.d("ServerSync", "Full URL with protocol: $url")
    if (isUrlValid(url)) {
        Log.d("ServerSync", "URL validation passed, proceeding with getMinApk")
        currentDialog = dialog
        service.getMinApk(this, url, pin, this, "SyncActivity")
    } else {
        Log.e("ServerSync", "URL validation failed, sync aborted")
    }
    Log.d("ServerSync", "=== performSync finished ===")
}

fun SyncActivity.onChangeServerUrl() {
    val selected = spnCloud.selectedItem
    if (selected is RealmCommunity && selected.isValid) {
        serverUrl.setText(selected.localDomain)
        protocolCheckIn.check(R.id.radio_https)
        settings.getString("serverProtocol", getString(R.string.https_protocol))
        serverPassword.setText(if (selected.weight == 0) "1983" else "")
        serverPassword.isEnabled = selected.weight != 0
    }
}

fun SyncActivity.setUrlAndPin(checked: Boolean) {
    if (checked) {
        onChangeServerUrl()
    } else {
        serverUrl.setText(settings.getString("serverURL", "")?.let { ServerConfigUtils.removeProtocol(it) })
        serverPassword.setText(settings.getString("serverPin", ""))
        protocolCheckIn.check(
            if (TextUtils.equals(settings.getString("serverProtocol", ""), "http://")) {
                R.id.radio_http
            } else {
                R.id.radio_https
            }
        )
    }
    serverUrl.isEnabled = !checked
    serverPassword.isEnabled = !checked
    serverPassword.clearFocus()
    serverUrl.clearFocus()
    protocolCheckIn.isEnabled = !checked
}

fun SyncActivity.protocolSemantics() {
    protocolCheckIn.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
        when (i) {
            R.id.radio_http -> editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
            R.id.radio_https -> editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
        }
    }
}

fun SyncActivity.refreshServerList() {
    val filteredList = ServerConfigUtils.getFilteredList(
        showAdditionalServers,
        serverListAddresses,
        settings.getString("pinnedServerUrl", null),
    )
    serverAddressAdapter?.submitList(filteredList)

    val pinnedUrl = settings.getString("serverURL", "")
    val pinnedIndex = filteredList.indexOfFirst {
        it.url.replace(Regex("^https?://"), "") == pinnedUrl?.replace(Regex("^https?://"), "")
    }
    if (pinnedIndex != -1) {
        serverAddressAdapter?.setSelectedPosition(pinnedIndex)
    }
}

fun SyncActivity.setupManualUi(binding: DialogServerUrlBinding) {
    if (settings.getString("serverProtocol", "") == getString(R.string.http_protocol)) {
        binding.radioHttp.isChecked = true
        editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
    } else if (settings.getString("serverProtocol", "") == getString(R.string.https_protocol)) {
        binding.radioHttps.isChecked = true
        editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
    }
    serverUrl.setText(settings.getString("serverURL", "")?.let { ServerConfigUtils.removeProtocol(it) })
    serverPassword.setText(settings.getString("serverPin", ""))
    serverUrl.isEnabled = true
    serverPassword.isEnabled = true
}

fun SyncActivity.setupServerListUi(binding: DialogServerUrlBinding, dialog: MaterialDialog) {
    serverAddresses.layoutManager = LinearLayoutManager(this)
    serverListAddresses = ServerConfigUtils.getServerAddresses(this)

    val storedUrl = settings.getString("serverURL", null)
    val storedPin = settings.getString("serverPin", null)
    val urlWithoutProtocol = storedUrl?.replace(Regex("^https?://"), "")

    val filteredList = ServerConfigUtils.getFilteredList(
        showAdditionalServers,
        serverListAddresses,
        settings.getString("pinnedServerUrl", null),
    )

    serverAddressAdapter = ServerAddressAdapter(
        { serverListAddress ->
            val actualUrl = serverListAddress.url.replace(Regex("^https?://"), "")
            binding.inputServerUrl.setText(actualUrl)
            binding.inputServerPassword.setText(ServerConfigUtils.getPinForUrl(actualUrl))
            val protocol = if (
                actualUrl == BuildConfig.PLANET_XELA_URL ||
                actualUrl == BuildConfig.PLANET_SANPABLO_URL ||
                actualUrl == BuildConfig.PLANET_URIUR_URL
            ) "http://" else "https://"
            editor.putString("serverProtocol", protocol).apply()
            if (serverCheck) {
                performSync(dialog)
            }
        },
        { _, _ ->
            clearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server), false) {
                serverAddressAdapter?.revertSelection()
            }
        },
        urlWithoutProtocol,
    )

    serverAddressAdapter?.submitList(filteredList)

    serverAddresses.adapter = serverAddressAdapter

    if (urlWithoutProtocol != null) {
        val position = serverListAddresses.indexOfFirst { it.url.replace(Regex("^https?://"), "") == urlWithoutProtocol }
        if (position != -1) {
            serverAddressAdapter?.setSelectedPosition(position)
            binding.inputServerUrl.setText(urlWithoutProtocol)
            binding.inputServerPassword.setText(settings.getString("serverPin", ""))
        }
    }

    if (!prefData.getManualConfig()) {
        serverAddresses.visibility = View.VISIBLE
        if (storedUrl != null && !syncFailed) {
            val position = serverListAddresses.indexOfFirst { it.url.replace(Regex("^https?://"), "") == urlWithoutProtocol }
            if (position != -1) {
                serverAddressAdapter?.setSelectedPosition(position)
                binding.inputServerUrl.setText(urlWithoutProtocol)
                binding.inputServerPassword.setText(storedPin)
            }
        } else if (syncFailed) {
            serverAddressAdapter?.clearSelection()
        }
    } else if (storedUrl != null) {
        val position = serverListAddresses.indexOfFirst { it.url.replace(Regex("^https?://"), "") == urlWithoutProtocol }
        if (position != -1) {
            serverAddressAdapter?.setSelectedPosition(position)
            binding.inputServerUrl.setText(urlWithoutProtocol)
            binding.inputServerPassword.setText(storedPin)
        }
    }
    serverUrl.isEnabled = false
    serverPassword.isEnabled = false
    editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
}

fun SyncActivity.onNeutralButtonClick(dialog: MaterialDialog) {
    if (!prefData.getManualConfig()) {
        showAdditionalServers = !showAdditionalServers
        refreshServerList()
        dialog.getActionButton(DialogAction.NEUTRAL).text =
            if (showAdditionalServers) getString(R.string.show_less) else getString(R.string.show_more)
    } else {
        serverConfigAction = "save"
        val protocol = "${settings.getString("serverProtocol", "")}"
        var url = "${serverUrl.text}"
        val pin = "${serverPassword.text}"
        url = protocol + url
        if (isUrlValid(url)) {
            currentDialog = dialog
            service.getMinApk(this, url, pin, this, "SyncActivity")
        }
    }
}

fun SyncActivity.initServerDialog(binding: DialogServerUrlBinding) {
    spnCloud = binding.spnCloud
    protocolCheckIn = binding.radioProtocol
    serverUrl = binding.inputServerUrl
    serverPassword = binding.inputServerPassword
    serverAddresses = binding.serverUrls
    syncToServerText = binding.syncToServerText
    binding.deviceName.setText(org.ole.planet.myplanet.utilities.NetworkUtils.getDeviceName())
}

fun SyncActivity.setRadioProtocolListener(binding: DialogServerUrlBinding) {
    binding.radioProtocol.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
        when (checkedId) {
            R.id.radio_http -> editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
            R.id.radio_https -> editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
        }
    }
}

fun SyncActivity.setupFastSyncOption(binding: DialogServerUrlBinding) {
    val isFastSync = settings.getBoolean("fastSync", false)
    binding.fastSync.isChecked = isFastSync
    binding.fastSync.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
        editor.putBoolean("fastSync", checked).apply()
    }
}

fun SyncActivity.handleManualConfiguration(
    binding: DialogServerUrlBinding,
    configurationId: String?,
    dialog: MaterialDialog,
) {
    if (!prefData.getManualConfig()) {
        binding.manualConfiguration.isChecked = false
        showConfigurationUIElements(binding, false, dialog)
    } else {
        binding.manualConfiguration.isChecked = true
        showConfigurationUIElements(binding, true, dialog)
    }

    binding.manualConfiguration.setOnCheckedChangeListener(null)
    binding.manualConfiguration.setOnClickListener {
        if (configurationId != null) {
            binding.manualConfiguration.isChecked = prefData.getManualConfig()
            if (prefData.getManualConfig()) {
                clearDataDialog(getString(R.string.switching_off_manual_configuration_to_clear_data), false)
            } else {
                clearDataDialog(getString(R.string.switching_on_manual_configuration_to_clear_data), true)
            }
        } else {
            val newCheckedState = !prefData.getManualConfig()
            prefData.setManualConfig(newCheckedState)
            if (newCheckedState) {
                setupManualConfigEnabled(binding, dialog)
            } else {
                prefData.setManualConfig(false)
                showConfigurationUIElements(binding, false, dialog)
                editor.putBoolean("switchCloudUrl", false).apply()
            }
        }
    }
}

fun SyncActivity.setupManualConfigEnabled(binding: DialogServerUrlBinding, dialog: MaterialDialog) {
    prefData.setManualConfig(true)
    editor.putString("serverURL", "").apply()
    editor.putString("serverPin", "").apply()
    binding.radioHttp.isChecked = true
    editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
    showConfigurationUIElements(binding, true, dialog)

    val communities: List<RealmCommunity> = databaseService.withRealm { realm ->
        realm.where(RealmCommunity::class.java).sort("weight", Sort.ASCENDING).findAll().let { realm.copyFromRealm(it) }
    }
    val nonEmptyCommunities = communities.filter { !TextUtils.isEmpty(it.name) }
    binding.spnCloud.adapter = ArrayAdapter(this, R.layout.spinner_item_white, nonEmptyCommunities)
    binding.spnCloud.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            onChangeServerUrl()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    binding.switchServerUrl.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
        editor.putBoolean("switchCloudUrl", b).apply()
        binding.spnCloud.visibility = if (b) View.VISIBLE else View.GONE
        setUrlAndPin(binding.switchServerUrl.isChecked)
    }
    serverUrl.addTextChangedListener(MyTextWatcher(serverUrl))
    binding.switchServerUrl.isChecked = settings.getBoolean("switchCloudUrl", false)
    setUrlAndPin(settings.getBoolean("switchCloudUrl", false))
    protocolSemantics()
}
