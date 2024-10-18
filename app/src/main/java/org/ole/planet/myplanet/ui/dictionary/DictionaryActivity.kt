package org.ole.planet.myplanet.ui.dictionary

import android.os.Bundle
import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import com.google.gson.JsonArray
import io.realm.Case
import io.realm.Realm
import io.realm.RealmResults
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.FragmentDictionaryBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmDictionary
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.UUID

class DictionaryActivity : BaseActivity() {
    private lateinit var fragmentDictionaryBinding: FragmentDictionaryBinding
//    lateinit var mRealm: Realm
    var list: RealmResults<RealmDictionary>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentDictionaryBinding = FragmentDictionaryBinding.inflate(layoutInflater)
        setContentView(fragmentDictionaryBinding.root)
        initActionBar()
        title = getString(R.string.dictionary)
        mRealm = DatabaseService(this).realmInstance
        list = mRealm.where(RealmDictionary::class.java)?.findAll()
        fragmentDictionaryBinding.tvResult.text = getString(R.string.list_size, list?.size)
        if (FileUtils.checkFileExist(Constants.DICTIONARY_URL)) {
            insertDictionary()
        } else {
            val list = ArrayList<String>()
            list.add(Constants.DICTIONARY_URL)
            Utilities.toast(this, getString(R.string.downloading_started_please_check_notificati))
            Utilities.openDownloadService(this, list, false)
        }
    }

    private fun insertDictionary() {
        if (list?.size == 0) {
            val data = FileUtils.getStringFromFile(FileUtils.getSDPathFromUrl(Constants.DICTIONARY_URL))
            val json = Gson().fromJson(data, JsonArray::class.java)
            mRealm.executeTransactionAsync {
                json.forEach { js ->
                    val doc = js.asJsonObject
                    var dict = it.where(RealmDictionary::class.java)
                        ?.equalTo("id", UUID.randomUUID().toString())?.findFirst()
                    if (dict == null) {
                        dict = it.createObject(
                            RealmDictionary::class.java, UUID.randomUUID().toString()
                        )
                    }
                    dict?.code = JsonUtils.getString("code", doc)
                    dict?.language = JsonUtils.getString("language", doc)
                    dict?.advanceCode = JsonUtils.getString("advance_code", doc)
                    dict?.word = JsonUtils.getString("word", doc)
                    dict?.meaning = JsonUtils.getString("meaning", doc)
                    dict?.definition = JsonUtils.getString("definition", doc)
                    dict?.synonym = JsonUtils.getString("synonym", doc)
                    dict?.antonym = JsonUtils.getString("antonoym", doc)
                }
            }
        } else {
            setClickListener()
        }
    }

    private fun setClickListener() {
        fragmentDictionaryBinding.btnSearch.setOnClickListener {
            val dict = mRealm.where(RealmDictionary::class.java)?.equalTo("word", fragmentDictionaryBinding.etSearch.text.toString(), Case.INSENSITIVE)?.findFirst()
            if (dict != null) {
                fragmentDictionaryBinding.tvResult.text = HtmlCompat.fromHtml(
                    "Definition of '<b>" + dict.word + "</b>'<br/><br/>\n " + "<b>" + dict.definition + "\n</b><br/><br/><br/>" + "<b>Synonym : </b>" + dict.synonym + "\n<br/><br/>" + "<b>Antonoym : </b>" + dict.antonym + "\n<br/>",
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                )
            } else {
                Utilities.toast(this, getString(R.string.word_not_available_in_our_database))
            }
        }
    }
}
