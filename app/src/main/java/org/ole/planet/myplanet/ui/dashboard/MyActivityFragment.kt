package org.ole.planet.myplanet.ui.dashboard


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_my_activity.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.service.UserProfileDbHandler


/**
 * A simple [Fragment] subclass.
 */
class MyActivityFragment : Fragment() {
    lateinit var realm : Realm;
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_my_activity, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var userModel = UserProfileDbHandler(activity!!).userModel
        realm = DatabaseService(activity!!).realmInstance
        var resourceActivity = realm.where(RealmResourceActivity::class.java).equalTo("user", userModel.name).findAll()


        resourceActivity.forEach {

        }
    }

}
