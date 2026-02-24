package org.ole.planet.myplanet.ui.dictionary

import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.FragmentDictionaryBinding
import org.ole.planet.myplanet.model.RealmDictionary
import org.ole.planet.myplanet.repository.DictionaryRepository
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class DictionaryActivity : BaseActivity() {
    @Inject
    lateinit var dictionaryRepository: DictionaryRepository

    private lateinit var fragmentDictionaryBinding: FragmentDictionaryBinding

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
    }

    private suspend fun loadDictionaryIfNeeded() {
        if (dictionaryRepository.isDictionaryEmpty()) {
            val context = this@DictionaryActivity
            val json = try {
                val data = withContext(Dispatchers.IO) {
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
                dictionaryRepository.importDictionary(jsonArray)
            }
        } else {
            setClickListener()
        }
    }

    private suspend fun loadDictionaryCount(): Long {
        return dictionaryRepository.getDictionaryCount()
    }

    private fun setClickListener() {
        fragmentDictionaryBinding.btnSearch.setOnClickListener {
            lifecycleScope.launch {
                val dict = dictionaryRepository.searchWord(fragmentDictionaryBinding.etSearch.text.toString())
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
