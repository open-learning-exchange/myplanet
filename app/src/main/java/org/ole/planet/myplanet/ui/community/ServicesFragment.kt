package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentServicesBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.team.TeamDetailFragment

class ServicesFragment : Fragment() {
    private lateinit var fragmentServicesBinding: FragmentServicesBinding
    var mRealm: Realm? = null;
    var user: RealmUserModel? = null;
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        fragmentServicesBinding = FragmentServicesBinding.inflate(inflater, container, false)
        return fragmentServicesBinding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mRealm = DatabaseService(requireActivity()).realmInstance
        user = UserProfileDbHandler(requireActivity()).userModel
        fragmentServicesBinding.fab.setOnClickListener {
            var bottomSheetDialog: BottomSheetDialogFragment = AddLinkFragment()
            bottomSheetDialog.show(childFragmentManager, "")
            Handler().postDelayed({
                bottomSheetDialog.dialog?.setOnDismissListener {
                    setRecyclerView()
                }
            }, 1000)
        }
        setRecyclerView()

        if (user?.isManager == true || user?.isLeader == true) {
            fragmentServicesBinding.fab.show()
        } else {
            fragmentServicesBinding.fab.hide()
        }
    }

    private fun setRecyclerView() {
        val links = mRealm!!.where(RealmMyTeam::class.java).equalTo("docType", "link").findAll()
        fragmentServicesBinding.llServices.removeAllViews()
        links.forEach { team ->
            var b: TextView =
                LayoutInflater.from(activity).inflate(R.layout.button_single, null) as TextView;
            b.setPadding(8, 8, 8, 8)
            b.text = team.title
            b.setOnClickListener {
                val route = team.route.split("/")
                if (route.size >= 3) {
                    val f = TeamDetailFragment()
                    val b = Bundle()
                    var teamObject =
                        mRealm!!.where(RealmMyTeam::class.java).equalTo("_id", route[3]).findFirst()
                    b.putString("id", route[3])
                    b.putBoolean("isMyTeam", teamObject!!.isMyTeam(user?.id, mRealm))
                    f.arguments = b
                    (context as OnHomeItemClickListener).openCallFragment(f)
                }
            }
            fragmentServicesBinding.llServices.addView(b)
        }
    }
}
