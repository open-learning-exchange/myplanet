package org.ole.planet.myplanet.ui.dictionary

import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import org.ole.planet.myplanet.utilities.GsonUtils
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Case
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.FragmentDictionaryBinding
import org.ole.planet.myplanet.model.RealmDictionary
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DownloadUtils
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class DictionaryActivity : BaseActivity() {
    private lateinit var fragmentDictionaryBinding: FragmentDictionaryBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentDictionaryBinding = FragmentDictionaryBinding.inflate(layoutInflater)
        setContentView(fragmentDictionaryBinding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, fragmentDictionaryBinding.root)
        initActionBar()
        title = getString(R.string.dictionary)

        databaseService.withRealm { realm ->
            val list = realm.where(RealmDictionary::class.java).findAll()
            fragmentDictionaryBinding.tvResult.text =
                getString(R.string.list_size, list.size)
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
    }

    private suspend fun loadDictionaryIfNeeded() {
        var isEmpty = true
        databaseService.withRealm { realm ->
            isEmpty = realm.where(RealmDictionary::class.java).count() == 0L
        }
        if (isEmpty) {
            val context = this@DictionaryActivity
            val json = withContext(Dispatchers.IO) {
                try {
                    val data = FileUtils.getStringFromFile(
                        FileUtils.getSDPathFromUrl(context, Constants.DICTIONARY_URL)
                    )
                    GsonUtils.gson.fromJson(data, JsonArray::class.java)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            json?.let { jsonArray ->
                databaseService.withRealm { realm ->
                    realm.executeTransactionAsync { bgRealm ->
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
        } else {
            setClickListener()
        }
    }

    private fun setClickListener() {
        fragmentDictionaryBinding.btnSearch.setOnClickListener {
            databaseService.withRealm { realm ->
                val dict = realm.where(RealmDictionary::class.java)
                    .equalTo(
                        "word",
                        fragmentDictionaryBinding.etSearch.text.toString(),
                        Case.INSENSITIVE
                    )
                    .findFirst()
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
                        this,
                        getString(R.string.word_not_available_in_our_database)
                    )
                }
            }
        }
    }
}
