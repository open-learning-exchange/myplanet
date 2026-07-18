package org.ole.planet.myplanet.ui.community

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.databinding.FragmentCommunityServicesBinding
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.ui.components.FragmentNavigator.replaceFragment
import org.ole.planet.myplanet.ui.teams.TeamDetailFragment
import org.ole.planet.myplanet.ui.viewer.WebViewActivity
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.MarkdownUtils.prependBaseUrlToImages
import org.ole.planet.myplanet.utils.MarkdownUtils.setMarkdownText

class CommunityServicesFragment : BaseTeamFragment() {
    private var binding: FragmentCommunityServicesBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentCommunityServicesBinding.inflate(inflater, container, false)
        binding = b
        return b.root
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
        val basePath = FileUtils.getExternalFilesDir(requireContext())?.let { externalDir ->
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
            val links = teamsRepository.getTeamLinks()
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

    override fun onNewsItemClick(news: News?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun setRecyclerView(links: List<MyTeam>) {
        val parent = binding?.llServices ?: return
        parent.removeAllViews()
        links.forEach { team ->
            val b: TextView = LayoutInflater.from(activity).inflate(R.layout.button_single, parent, false) as TextView
            b.setPadding(8, 8, 8, 8)
            b.text = team.title
            b.setOnClickListener {
                val rawRoute = team.route ?: return@setOnClickListener
                if (rawRoute.startsWith("http://") || rawRoute.startsWith("https://")) {
                    startActivity(Intent(requireContext(), WebViewActivity::class.java).apply {
                        putExtra("link", rawRoute)
                        putExtra("title", team.title)
                    })
                    return@setOnClickListener
                }
                val segments = rawRoute.split("/")
                val teamId = if (segments.size >= 4) segments[3] else null
                if (teamId != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val isMyTeam = teamsRepository.isMember(user?.id, teamId)
                        val f = TeamDetailFragment()
                        f.arguments = Bundle().apply {
                            putString("id", teamId)
                            putBoolean("isMyTeam", isMyTeam)
                        }
                        replaceFragment(
                            requireActivity().supportFragmentManager,
                            R.id.fragment_container,
                            f,
                            addToBackStack = true,
                            tag = ""
                        )
                    }
                } else {
                    startActivity(Intent(requireContext(), WebViewActivity::class.java).apply {
                        putExtra("link", rawRoute)
                        putExtra("title", team.title)
                    })
                }
            }
            parent.addView(b)
        }
    }
}
