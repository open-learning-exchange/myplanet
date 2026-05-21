package org.ole.planet.myplanet.ui.sync

import android.text.TextUtils
import android.view.View
import android.webkit.URLUtil
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.ServerConfigUtils

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
    binding.clearData.visibility = if (prefData.getConfigurationId().isNullOrBlank()) View.GONE else View.VISIBLE

    if (manualSelected) {
        setupManualUi(binding)
    } else {
        setupServerListUi(binding, dialog)
    }
}

fun SyncActivity.performSync(dialog: MaterialDialog) {
    serverConfigAction = "sync"
    val protocol = prefData.getServerProtocol()
    var url = "${serverUrl.text}"
    val pin = "${serverPassword.text}"

    prefData.setServerUrl(url)
    prefData.setServerPin(pin)

    url = protocol + url

    if (isUrlValid(url)) {
        currentDialog = dialog
        checkMinApk(url, pin, "SyncActivity")
    }
}

fun SyncActivity.onChangeServerUrl() {
    val selected = spnCloud.selectedItem
    if (selected is RealmCommunity && selected.isValid) {
        serverUrl.setText(selected.localDomain)
        protocolCheckIn.check(R.id.radio_https)
        prefData.getServerProtocol().ifEmpty { getString(R.string.https_protocol) }
        serverPassword.setText(if (selected.weight == 0) BuildConfig.PLANET_LEARNING_PIN else "")
        serverPassword.isEnabled = selected.weight != 0
    }
}

fun SyncActivity.setUrlAndPin(checked: Boolean) {
    if (checked) {
        onChangeServerUrl()
    } else {
        serverUrl.setText(ServerConfigUtils.removeProtocol(prefData.getServerUrl()))
        serverPassword.setText(prefData.getServerPin())
        protocolCheckIn.check(
            if (TextUtils.equals(prefData.getServerProtocol(), Constants.HTTP_PROTOCOL)) {
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
            R.id.radio_http -> prefData.setServerProtocol(getString(R.string.http_protocol))
            R.id.radio_https -> prefData.setServerProtocol(getString(R.string.https_protocol))
        }
    }
}

fun SyncActivity.refreshServerList() {
    val filteredList = ServerConfigUtils.getFilteredList(
        showAdditionalServers,
        serverListAddresses,
        prefData.getPinnedServerUrl(),
    )

    val pinnedUrl = prefData.getServerUrl()
    val urlWithoutProtocol = pinnedUrl.replace(Regex("^https?://"), "")

    // build the final list — if showing more, still pin selected server at top
    val finalList = if (showAdditionalServers && urlWithoutProtocol.isNotEmpty()) {
        val pinnedServer = filteredList.find {
            it.url.replace(Regex("^https?://"), "") == urlWithoutProtocol
        }
        if (pinnedServer != null) {
            // pinned server at top, then everyone else without the duplicate
            listOf(pinnedServer) + filteredList.filter {
                it.url.replace(Regex("^https?://"), "") != urlWithoutProtocol
            }
        } else {
            filteredList
        }
    } else {
        filteredList
    }

    // submitList is async, so pass a callback for when it's done
    serverAddressAdapter?.submitList(finalList) {
        // this runs AFTER the list diff is done and views are updated
        val pinnedIndex = finalList.indexOfFirst {
            it.url.replace(Regex("^https?://"), "") == urlWithoutProtocol
        }
        if (pinnedIndex != -1) {
            serverAddressAdapter?.setSelectedPosition(pinnedIndex)
        } else {
            serverAddressAdapter?.clearSelection()
        }
    }
}

fun SyncActivity.setupManualUi(binding: DialogServerUrlBinding) {
    if (prefData.getServerProtocol() == getString(R.string.http_protocol)) {
        binding.radioHttp.isChecked = true
        prefData.setServerProtocol(getString(R.string.http_protocol))
    } else if (prefData.getServerProtocol() == getString(R.string.https_protocol)) {
        binding.radioHttps.isChecked = true
        prefData.setServerProtocol(getString(R.string.https_protocol))
    }
    serverUrl.setText(ServerConfigUtils.removeProtocol(prefData.getServerUrl()))
    serverPassword.setText(prefData.getServerPin())
    serverUrl.isEnabled = true
    serverPassword.isEnabled = true
}

fun SyncActivity.setupServerListUi(binding: DialogServerUrlBinding, dialog: MaterialDialog) {
    serverAddresses.layoutManager = LinearLayoutManager(this)
    serverListAddresses = ServerConfigUtils.getServerAddresses(this)
    val storedUrl = prefData.getServerUrl().takeIf { it.isNotEmpty() }
    val storedPin = prefData.getServerPin().takeIf { it.isNotEmpty() }
    val urlWithoutProtocol = storedUrl?.replace(Regex("^https?://"), "")
    val filteredList = ServerConfigUtils.getFilteredList(
        showAdditionalServers,
        serverListAddresses,
        prefData.getPinnedServerUrl(),
    )
    serverAddressAdapter = ServerAddressAdapter(
        onItemClick = { serverListAddress ->
            val actualUrl = serverListAddress.url.replace(Regex("^https?://"), "")
            binding.inputServerUrl.setText(actualUrl)
            binding.inputServerPassword.setText(ServerConfigUtils.getPinForUrl(actualUrl))
            val protocol = if (
                actualUrl == BuildConfig.PLANET_XELA_URL ||
                actualUrl == BuildConfig.PLANET_SANPABLO_URL ||
                actualUrl == BuildConfig.PLANET_URIUR_URL ||
                isLocalNetwork(actualUrl)
            ) Constants.HTTP_PROTOCOL else Constants.HTTPS_PROTOCOL
            prefData.setServerProtocol(protocol)
            if (serverCheck) performSync(dialog)
        },
        onClearDataDialog = { _, _ ->
            clearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server), false) {
                serverAddressAdapter?.revertSelection()
            }
        },
        isServerAlreadyConfigured = !urlWithoutProtocol.isNullOrEmpty(),
    )
    serverAddresses.adapter = serverAddressAdapter  // set adapter BEFORE submitList
    serverAddressAdapter?.submitList(filteredList) {
        // runs AFTER list is ready
        if (urlWithoutProtocol != null && !syncFailed) {
            val position = filteredList.indexOfFirst {
                it.url.replace(Regex("^https?://"), "") == urlWithoutProtocol
            }
            if (position != -1) {
                serverAddressAdapter?.setSelectedPosition(position)
                binding.inputServerUrl.setText(urlWithoutProtocol)
                binding.inputServerPassword.setText(storedPin)
            }
        } else if (syncFailed) {
            serverAddressAdapter?.clearSelection()
        }
    }
    serverUrl.isEnabled = false
    serverPassword.isEnabled = false
}

fun SyncActivity.onNeutralButtonClick(dialog: MaterialDialog) {
    if (!prefData.getManualConfig()) {
        showAdditionalServers = !showAdditionalServers
        refreshServerList()
        dialog.getActionButton(DialogAction.NEUTRAL).text =
            if (showAdditionalServers) getString(R.string.show_less) else getString(R.string.show_more)
    } else {
        serverConfigAction = "save"
        val protocol = prefData.getServerProtocol()
        var url = "${serverUrl.text}"
        val pin = "${serverPassword.text}"
        url = protocol + url
        if (isUrlValid(url)) {
            currentDialog = dialog
            checkMinApk(url, pin, "SyncActivity")
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
    binding.deviceName.setText(org.ole.planet.myplanet.utils.NetworkUtils.getDeviceName())
}

fun SyncActivity.setRadioProtocolListener(binding: DialogServerUrlBinding) {
    binding.radioProtocol.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
        when (checkedId) {
            R.id.radio_http -> prefData.setServerProtocol(getString(R.string.http_protocol))
            R.id.radio_https -> prefData.setServerProtocol(getString(R.string.https_protocol))
        }
    }
}

fun SyncActivity.setupFastSyncOption(binding: DialogServerUrlBinding) {
    binding.fastSync.isChecked = prefData.getFastSync()
    binding.fastSync.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
        prefData.setFastSync(checked)
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
                prefData.setSwitchCloudUrl(false)
            }
        }
    }
}

fun SyncActivity.setupManualConfigEnabled(binding: DialogServerUrlBinding, dialog: MaterialDialog) {
    prefData.setManualConfig(true)
    prefData.setServerUrl("")
    prefData.setServerPin("")
    binding.radioHttp.isChecked = true
    prefData.setServerProtocol(getString(R.string.http_protocol))
    showConfigurationUIElements(binding, true, dialog)

    lifecycleScope.launch {
        val communities = communityRepository.getAllSorted()
        val nonEmptyCommunities = communities.filter { !TextUtils.isEmpty(it.name) }
        val adapter = ArrayAdapter(this@setupManualConfigEnabled, android.R.layout.simple_spinner_item, nonEmptyCommunities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spnCloud.adapter = adapter
        binding.spnCloud.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onChangeServerUrl()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    binding.switchServerUrl.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
        prefData.setSwitchCloudUrl(b)
        binding.spnCloud.visibility = if (b) View.VISIBLE else View.GONE
        setUrlAndPin(binding.switchServerUrl.isChecked)
    }
    serverUrl.doAfterTextChanged { s ->
        positiveAction.isEnabled = "$s".trim { it <= ' ' }.isNotEmpty() && URLUtil.isValidUrl("${prefData.getServerProtocol()}$s")
    }
    binding.switchServerUrl.isChecked = prefData.getSwitchCloudUrl()
    setUrlAndPin(prefData.getSwitchCloudUrl())
    protocolSemantics()
}

private fun isLocalNetwork(url: String): Boolean {
    val host = url.split(":").firstOrNull()?.split("/")?.firstOrNull() ?: url
    return host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) ||
            host == "localhost" ||
            host == "127.0.0.1" ||
            host.endsWith(".local")
}
