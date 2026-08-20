package org.ole.planet.myplanet.ui.dictionary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.FragmentDictionaryBinding
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.services.BroadcastService
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.repository.DictionaryRepository
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class DictionaryActivity : BaseActivity() {
    @Inject
    lateinit var dictionaryRepository: DictionaryRepository

    @Inject
    override lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    override lateinit var broadcastService: BroadcastService

    private lateinit var fragmentDictionaryBinding: FragmentDictionaryBinding

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val download = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("download", Download::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("download") as? Download
            }
            if (download != null && download.fileUrl == Constants.DICTIONARY_URL && download.progress == 100) {
                lifecycleScope.launch {
                    loadDictionaryIfNeeded()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentDictionaryBinding = FragmentDictionaryBinding.inflate(layoutInflater)
        setContentView(fragmentDictionaryBinding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, fragmentDictionaryBinding.root)
        initActionBar()
        title = getString(R.string.dictionary)

        lifecycleScope.launch {
            val count = loadDictionaryCount()
            fragmentDictionaryBinding.tvResult.text = getString(R.string.list_size, count)
        }

        lifecycleScope.launch {
            loadDictionaryIfNeeded()
        }

        registerReceiver()
    }

    override fun registerReceiver() {
        collectWhenStarted(broadcastService.events) { intent ->
            when (intent.action) {
                "message_progress" -> receiver.onReceive(this@DictionaryActivity, intent)
            }
        }
    }

    private suspend fun loadDictionaryIfNeeded() {
        if (dictionaryRepository.insertDictionaryData()) {
            val count = dictionaryRepository.count()
            fragmentDictionaryBinding.tvResult.text = getString(R.string.list_size, count)
            setClickListener()
        } else {
            val list = ArrayList<String>()
            list.add(Constants.DICTIONARY_URL)
            Utilities.toast(
                this@DictionaryActivity,
                getString(R.string.downloading_started_please_check_notificati)
            )
            DownloadUtils.openDownloadService(this@DictionaryActivity, list, false)
        }
    }

    private suspend fun loadDictionaryCount(): Long {
        return dictionaryRepository.count()
    }

    private fun setClickListener() {
        fragmentDictionaryBinding.btnSearch.setOnClickListener {
            val query = fragmentDictionaryBinding.etSearch.text.toString()
            lifecycleScope.launch {
                val dict = dictionaryRepository.findByWord(query)
                if (dict != null) {
                    fragmentDictionaryBinding.tvResult.text = HtmlCompat.fromHtml(
                        "Definition of '<b>" + dict.word + "</b>'<br/><br/>\n " +
                            "<b>" + dict.definition + "\n</b><br/><br/><br/>" +
                            "<b>Synonym : </b>" + dict.synonym + "\n<br/><br/>" +
                            "<b>Antonoym : </b>" + dict.antonym + "\n<br/>",
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
                } else {
                    Utilities.toast(
                        this@DictionaryActivity,
                        getString(R.string.word_not_available_in_our_database)
                    )
                }
            }
        }
    }
}
