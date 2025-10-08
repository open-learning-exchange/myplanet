package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentServicesBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
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

        val description = team?.description ?: ""
        if (description.isEmpty()) {
            binding?.tvDescription?.visibility = View.GONE
            binding?.tvNoDescription?.visibility = View.VISIBLE
        } else {
            binding?.tvDescription?.visibility = View.VISIBLE
            binding?.tvNoDescription?.visibility = View.GONE
        }
        val basePath = requireContext().getExternalFilesDir(null)?.let { externalDir ->
            "file://${externalDir.absolutePath}/ole/"
        }.orEmpty()
        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            description,
            basePath,
            600,
            350
        )
        binding?.let { setMarkdownText(it.tvDescription, markdownContentWithLocalPaths) }

        viewLifecycleOwner.lifecycleScope.launch {
            val links = teamRepository.getTeamLinks()
            val currentBinding = binding ?: return@launch
            if (links.isEmpty()) {
                currentBinding.llServices.visibility = View.GONE
                currentBinding.tvNoLinks.visibility = View.VISIBLE
            } else {
                currentBinding.llServices.visibility = View.VISIBLE
                currentBinding.tvNoLinks.visibility = View.GONE
            }
            setRecyclerView(links)
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun setRecyclerView(links: List<RealmMyTeam>) {
        val parent = binding?.llServices ?: return
        parent.removeAllViews()
        links.forEach { team ->
            val b: TextView = LayoutInflater.from(activity).inflate(R.layout.button_single, parent, false) as TextView
            b.setPadding(8, 8, 8, 8)
            b.text = team.title
            b.setOnClickListener {
                val route = team.route?.split("/")
                if (route != null && route.size >= 4) {
                    val teamId = route[3]
                    viewLifecycleOwner.lifecycleScope.launch {
                        val teamObject = teamRepository.getTeamById(teamId)
                        val isMyTeam = teamRepository.isMember(user?.id, teamId)

                        val f = TeamDetailFragment.newInstance(
                            teamId = teamId,
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
