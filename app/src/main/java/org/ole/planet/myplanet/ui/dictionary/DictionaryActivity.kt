package org.ole.planet.myplanet.ui.dictionary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.JsonArray
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Case
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.databinding.FragmentDictionaryBinding
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmDictionary
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class DictionaryActivity : BaseActivity() {
    @Inject
    lateinit var databaseService: DatabaseService

    @Inject
    override lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    override lateinit var broadcastService: org.ole.planet.myplanet.services.BroadcastService

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

        if (FileUtils.checkFileExist(this, Constants.DICTIONARY_URL)) {
            lifecycleScope.launch {
                loadDictionaryIfNeeded()
            }
        } else {
            val list = ArrayList<String>()
            list.add(Constants.DICTIONARY_URL)
            Utilities.toast(this, getString(R.string.downloading_started_please_check_notificati))
            DownloadUtils.openDownloadService(this, list, false)
        }

        registerReceiver()
    }

    override fun registerReceiver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                broadcastService.events.collect { intent ->
                    if (isActive) {
                        when (intent.action) {
                            "message_progress" -> receiver.onReceive(this@DictionaryActivity, intent)
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadDictionaryIfNeeded() {
        val isEmpty = loadDictionaryCount() == 0L
        if (isEmpty) {
            val context = this@DictionaryActivity
            val json = try {
                val data = withContext(dispatcherProvider.io) {
                    FileUtils.getStringFromFile(
                        FileUtils.getSDPathFromUrl(context, Constants.DICTIONARY_URL)
                    )
                }
                JsonUtils.gson.fromJson(data, JsonArray::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            json?.let { jsonArray ->
                databaseService.executeTransactionAsync { bgRealm ->
                    jsonArray.forEach { js ->
                        val doc = js.asJsonObject
                        val dict = bgRealm.createObject(
                            RealmDictionary::class.java, UUID.randomUUID().toString()
                        )
                        dict.code = JsonUtils.getString("code", doc)
                        dict.language = JsonUtils.getString("language", doc)
                        dict.advanceCode = JsonUtils.getString("advance_code", doc)
                        dict.word = JsonUtils.getString("word", doc)
                        dict.meaning = JsonUtils.getString("meaning", doc)
                        dict.definition = JsonUtils.getString("definition", doc)
                        dict.synonym = JsonUtils.getString("synonym", doc)
                        dict.antonym = JsonUtils.getString("antonoym", doc)
                    }
                }
            }
        }

        val count = loadDictionaryCount()
        fragmentDictionaryBinding.tvResult.text = getString(R.string.list_size, count)
        setClickListener()
    }

    private suspend fun loadDictionaryCount(): Long {
        return databaseService.withRealmAsync { realm ->
            realm.where(RealmDictionary::class.java).count()
        }
    }

    private fun setClickListener() {
        fragmentDictionaryBinding.btnSearch.setOnClickListener {
            val query = fragmentDictionaryBinding.etSearch.text.toString()
            lifecycleScope.launch {
                val dict = databaseService.withRealmAsync { realm ->
                    realm.where(RealmDictionary::class.java)
                        .equalTo("word", query, Case.INSENSITIVE)
                        .findFirst()?.let { realm.copyFromRealm(it) }
                }
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
