package org.ole.planet.myplanet.ui.community

import android.os.*
import android.view.*
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.kotlin.query.RealmResults
import org.ole.planet.myplanet.*
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentServicesBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.courses.CourseStepFragment
import org.ole.planet.myplanet.ui.team.*
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class ServicesFragment : BaseTeamFragment() {
    private lateinit var fragmentServicesBinding: FragmentServicesBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentServicesBinding = FragmentServicesBinding.inflate(inflater, container, false)
        return fragmentServicesBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRealm = DatabaseService().realmInstance
        user = UserProfileDbHandler(requireActivity()).userModel

        val links = mRealm.query<RealmMyTeam>(RealmMyTeam::class, "docType == $0", "link").find()

        fragmentServicesBinding.fab.setOnClickListener {
            val bottomSheetDialog: BottomSheetDialogFragment = AddLinkFragment()
            bottomSheetDialog.show(childFragmentManager, "")
            Handler(Looper.getMainLooper()).postDelayed({
                bottomSheetDialog.dialog?.setOnDismissListener {
                    setRecyclerView(links)
                }
            }, 1000)
        }

        if (links.isEmpty()) {
            fragmentServicesBinding.llServices.visibility = View.GONE
        }

        val description = team?.description ?: ""
        fragmentServicesBinding.llServices.visibility = View.VISIBLE
        fragmentServicesBinding.tvDescription.visibility = View.VISIBLE
        val markdownContentWithLocalPaths = CourseStepFragment.prependBaseUrlToImages(description, "file://${MainApplication.context.getExternalFilesDir(null)}/ole/")
        setMarkdownText(fragmentServicesBinding.tvDescription, markdownContentWithLocalPaths)
        setRecyclerView(links)

        if (user?.isManager() == true || user?.isLeader() == true) {
            fragmentServicesBinding.fab.show()
        } else {
            fragmentServicesBinding.fab.hide()
        }
    }

    override fun setData(list: List<RealmNews>?) {}

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun setRecyclerView(links: RealmResults<RealmMyTeam>) {
        fragmentServicesBinding.llServices.removeAllViews()
        links.forEach { team ->
            val b: TextView = LayoutInflater.from(activity).inflate(R.layout.button_single, fragmentServicesBinding.llServices, false) as TextView
            b.setPadding(8, 8, 8, 8)
            b.text = team.title
            b.setOnClickListener {
                val route = team.route.split("/")
                if (route.size >= 3) {
                    val f = TeamDetailFragment()
                    val c = Bundle()
                    val teamObject = mRealm.query<RealmMyTeam>(RealmMyTeam::class, "_id == $0", route[3]).first().find()
                    c.putString("id", route[3])
                    teamObject?.isMyTeam(user?.id ?: "", mRealm)?.let { isTeamMember ->
                        c.putBoolean("isMyTeam", isTeamMember)
                    }
                    f.arguments = c
                    (context as OnHomeItemClickListener).openCallFragment(f)
                }
            }
            fragmentServicesBinding.llServices.addView(b)
        }
    }
}