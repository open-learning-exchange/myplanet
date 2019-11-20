package org.ole.planet.myplanet.ui.community


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.Realm
import kotlinx.android.synthetic.main.alert_add_link.view.*
import kotlinx.android.synthetic.main.fragment_add_link.*

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.ui.team.AdapterTeam
import org.ole.planet.myplanet.utilities.Utilities
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class AddLinkFragment : BottomSheetDialogFragment(), AdapterView.OnItemSelectedListener {
    override fun onNothingSelected(p0: AdapterView<*>?) {
    }

    lateinit var mRealm: Realm;
    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {

        val query = mRealm.where(RealmMyTeam::class.java)
                .isEmpty("teamId")
                .isNotEmpty("name")
                .equalTo("type", if (spn_link.selectedItem.toString().equals("Enterprises")) "enterprises" else "")
                .notEqualTo("status", "archived")
                .findAll()
        rv_list.layoutManager = LinearLayoutManager(activity!!)
        Utilities.log("SIZE ${query}")
        rv_list.adapter = AdapterTeam(activity!!, query, mRealm)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_add_link, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mRealm = DatabaseService(activity!!).realmInstance
        spn_link.onItemSelectedListener = this
        btn_save.setOnClickListener {
            var type = spn_link?.selectedItem.toString()
            var title = et_name?.text.toString()

            mRealm.executeTransaction {
                var team = it.createObject(RealmMyTeam::class.java, UUID.randomUUID().toString())
                team.docType = "link"
                team.title = title
                team.route = type + "/view/"
            }
        }

    }

}
