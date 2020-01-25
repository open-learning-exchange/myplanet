package org.ole.planet.myplanet.ui.dictionary


import android.os.Bundle
import android.os.PersistableBundle
import android.text.Html
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import com.google.gson.JsonArray
import io.realm.Case
import io.realm.Realm
import io.realm.RealmResults
import kotlinx.android.synthetic.main.fragment_dictionary.*

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmDictionary
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.*
import java.util.concurrent.Executors

/**
 * A simple [Fragment] subclass.
 */
class DictionaryActivity : BaseActivity() {
    lateinit var mRealm: Realm;
    var list: RealmResults<RealmDictionary>? = null;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_dictionary)
        initActionBar()
        title = "Dictionary"
        mRealm = DatabaseService(this).realmInstance;
        list = mRealm?.where(RealmDictionary::class.java)?.findAll()
        tv_result.setText("List size ${list?.size}")
        Utilities.log("${FileUtils.checkFileExist(Constants.DICTIONARY_URL)} file")
        if (FileUtils.checkFileExist(Constants.DICTIONARY_URL)) {
            Utilities.log("List " + list?.size)
            insertDictionary();
        } else {
            val list = ArrayList<String>()
            list.add(Constants.DICTIONARY_URL)
            Utilities.toast(this, "Downloading started, please check notification...")
            Utilities.openDownloadService(this, list)
        }
    }

    private fun insertDictionary() {
        if (list?.size == 0) {
            var data = FileUtils.getStringFromFile(FileUtils.getSDPathFromUrl(Constants.DICTIONARY_URL))
            var json = Gson().fromJson(data, JsonArray::class.java)
            mRealm?.executeTransactionAsync { it ->
                json.forEach { js ->
                    var doc = js.asJsonObject
                    var dict = it.where(RealmDictionary::class.java)?.equalTo("id", UUID.randomUUID().toString())?.findFirst()
                    if (dict == null) {
                        dict = it.createObject(RealmDictionary::class.java, UUID.randomUUID().toString())
                    }
                    dict?.code = JsonUtils.getString("code", doc)
                    dict?.language = JsonUtils.getString("language", doc)
                    dict?.advance_code = JsonUtils.getString("advance_code", doc)
                    dict?.word = JsonUtils.getString("word", doc)
                    dict?.meaning = JsonUtils.getString("meaning", doc)
                    dict?.definition = JsonUtils.getString("definition", doc)
                    dict?.synonym = JsonUtils.getString("synonym", doc)
                    dict?.antonoym = JsonUtils.getString("antonoym", doc)
                }
            }
        } else {
           setClickListener()
        }
    }

    private fun setClickListener() {
        btn_search.setOnClickListener {
            var dict = mRealm.where(RealmDictionary::class.java)?.equalTo("word", et_search.text.toString(), Case.INSENSITIVE)?.findFirst()
            if (dict != null) {
                tv_result.text = HtmlCompat.fromHtml("Definition of '<b>"+ dict?.word + "</b>'<br/><br/>\n " +
                        "<b>" + dict?.definition + "\n</b><br/><br/><br/>" +
                        "<b>Synonym : </b>" + dict?.synonym + "\n<br/><br/>" +
                        "<b>Antonoym : </b>" + dict?.antonoym + "\n<br/>", HtmlCompat.FROM_HTML_MODE_LEGACY)
            } else {
                Utilities.toast(this, "Word not available in our database.")
            }
        }
    }


}
