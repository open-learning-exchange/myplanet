package org.ole.planet.myplanet.ui.dictionary


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.Gson
import com.google.gson.JsonArray
import io.realm.Realm

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmDictionary
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class DictionaryFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_dictionary, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var mRealm: Realm = DatabaseService(activity).realmInstance;
        var list = mRealm?.where(RealmDictionary::class.java)?.findAll()
        if (FileUtils.checkFileExist(Constants.DICTIONARY_URL)) {

            if (list?.size == 0) {
                var data = FileUtils.getStringFromFile(FileUtils.getSDPathFromUrl(Constants.DICTIONARY_URL))
                var json = Gson().fromJson(data, JsonArray::class.java)
                json.forEach { js ->
                    var doc = js.asJsonObject
                    mRealm?.executeTransactionAsync { it->
                        var dict = it.where(RealmDictionary::class.java)?.equalTo("_id", UUID.randomUUID().toString())?.findFirst()
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

            }
        } else {
            Utilities.toast(activity, "Please download dictionary first")
        }

    }


}
