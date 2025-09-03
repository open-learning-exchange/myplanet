package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.RealmResults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentServicesBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class ServicesFragment : BaseTeamFragment() {
    private var binding: FragmentServicesBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentServicesBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)
        mRealm = databaseService.realmInstance
        user = UserProfileDbHandler(requireActivity()).userModel

        val links = mRealm.where(RealmMyTeam::class.java)?.equalTo("docType", "link")?.findAll()

        binding?.fab?.setOnClickListener {
            val bottomSheetDialog: BottomSheetDialogFragment = AddLinkFragment()
            bottomSheetDialog.show(childFragmentManager, "")
            viewLifecycleOwner.lifecycleScope.launch {
                delay(1000)
                bottomSheetDialog.dialog?.setOnDismissListener {
                    setRecyclerView(links)
                }
            }
        }

        if (links?.size == 0) {
            binding?.llServices?.visibility = View.GONE
            binding?.tvNoLinks?.visibility = View.VISIBLE
        } else {
            binding?.llServices?.visibility = View.VISIBLE
        }

        val description = team?.description ?: ""
        if (description.isEmpty()) {
            binding?.tvDescription?.visibility = View.GONE
            binding?.tvNoDescription?.visibility = View.VISIBLE
        } else {
            binding?.tvDescription?.visibility = View.VISIBLE
            binding?.tvNoDescription?.visibility = View.GONE
        }
        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            description,
            "file://${MainApplication.context.getExternalFilesDir(null)}/ole/",
            600,
            350
        )
        binding?.let { setMarkdownText(it.tvDescription, markdownContentWithLocalPaths) }
        setRecyclerView(links)

        if (user?.isManager() == true || user?.isLeader() == true) {
            binding?.fab?.show()
        } else {
            binding?.fab?.hide()
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun setRecyclerView(links: RealmResults<RealmMyTeam>?) {
        val parent = binding?.llServices ?: return
        parent.removeAllViews()
        links?.forEach { team ->
            val b: TextView = LayoutInflater.from(activity).inflate(R.layout.button_single, parent, false) as TextView
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
            parent.addView(b)
        }
    }
}
