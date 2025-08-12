package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.RealmResults
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentServicesBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class ServicesFragment : BaseTeamFragment() {
    private lateinit var fragmentServicesBinding: FragmentServicesBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentServicesBinding = FragmentServicesBinding.inflate(inflater, container, false)
        return fragmentServicesBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)
        user = profileDbHandler.userModel

        val links = mRealm.where(RealmMyTeam::class.java)?.equalTo("docType", "link")?.findAll()

        fragmentServicesBinding.fab.setOnClickListener {
            val bottomSheetDialog: BottomSheetDialogFragment = AddLinkFragment()
            bottomSheetDialog.show(childFragmentManager, "")
            Handler(Looper.getMainLooper()).postDelayed({
                bottomSheetDialog.dialog?.setOnDismissListener {
                    setRecyclerView(links)
                }
            }, 1000)
        }

        if (links?.size == 0) {
            fragmentServicesBinding.llServices.visibility = View.GONE
            fragmentServicesBinding.tvNoLinks.visibility = View.VISIBLE
        } else {
            fragmentServicesBinding.llServices.visibility = View.VISIBLE
        }

        val description = team?.description ?: ""
        if (description.isEmpty()) {
            fragmentServicesBinding.tvDescription.visibility = View.GONE
            fragmentServicesBinding.tvNoDescription.visibility = View.VISIBLE
        } else {
            fragmentServicesBinding.tvDescription.visibility = View.VISIBLE
            fragmentServicesBinding.tvNoDescription.visibility = View.GONE
        }
        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            description,
            "file://${MainApplication.context.getExternalFilesDir(null)}/ole/",
            600,
            350
        )
        setMarkdownText(fragmentServicesBinding.tvDescription, markdownContentWithLocalPaths)
        setRecyclerView(links)

        if (user?.isManager() == true || user?.isLeader() == true) {
            fragmentServicesBinding.fab.show()
        } else {
            fragmentServicesBinding.fab.hide()
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun setRecyclerView(links: RealmResults<RealmMyTeam>?) {
        fragmentServicesBinding.llServices.removeAllViews()
        links?.forEach { team ->
            val b: TextView = LayoutInflater.from(activity).inflate(R.layout.button_single, fragmentServicesBinding.llServices, false) as TextView
            b.setPadding(8, 8, 8, 8)
            b.text = team.title
            b.setOnClickListener {
                val route = team.route?.split("/")
                if (route != null) {
                    if (route.size >= 3) {
                        val teamObject = mRealm.where(RealmMyTeam::class.java)?.equalTo("_id", route[3])?.findFirst()
                        val isMyTeam = teamObject?.isMyTeam(user?.id, mRealm) == true

                        val f = TeamDetailFragment.newInstance(
                            teamId = route[3],
                            teamName = teamObject?.name ?: "",
                            teamType = teamObject?.type ?: "",
                            isMyTeam = isMyTeam
                        )

                        homeItemClickListener?.openCallFragment(f)
                    }
                }
            }
            fragmentServicesBinding.llServices.addView(b)
        }
    }
}
