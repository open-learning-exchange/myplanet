package org.ole.planet.myplanet.ui.community

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.RealmResults
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentServicesBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.courses.CourseStepFragment
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText
import java.util.regex.Pattern

class ServicesFragment : BaseTeamFragment() {
    private lateinit var fragmentServicesBinding: FragmentServicesBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentServicesBinding = FragmentServicesBinding.inflate(inflater, container, false)
        return fragmentServicesBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRealm = DatabaseService(requireActivity()).realmInstance
        user = UserProfileDbHandler(requireActivity()).userModel

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
            fragmentServicesBinding.tvDescription.visibility = View.VISIBLE
            val markdownContentWithLocalPaths = prependBaseUrlToHtmlImages(team.description, "file://${context?.getExternalFilesDir(null)}/ole/")
            setMarkdownText(fragmentServicesBinding.tvDescription, markdownContentWithLocalPaths)
            Log.d("ServicesFragment", "onViewCreated: $markdownContentWithLocalPaths")
        } else {
            setRecyclerView(links)
        }

        if (user?.isManager() == true || user?.isLeader() == true) {
            fragmentServicesBinding.fab.show()
        } else {
            fragmentServicesBinding.fab.hide()
        }
    }

    private fun setRecyclerView(links: RealmResults<RealmMyTeam>?) {
        fragmentServicesBinding.llServices.removeAllViews()
        links?.forEach { team ->
            val b: TextView = LayoutInflater.from(activity).inflate(R.layout.button_single, null) as TextView
            b.setPadding(8, 8, 8, 8)
            b.text = team.title
            b.setOnClickListener {
                val route = team.route?.split("/")
                if (route != null) {
                    if (route.size >= 3) {
                        val f = TeamDetailFragment()
                        val c = Bundle()
                        val teamObject = mRealm.where(RealmMyTeam::class.java)?.equalTo("_id", route[3])?.findFirst()
                        c.putString("id", route[3])
                        teamObject?.isMyTeam(user?.id, mRealm)?.let { it1 -> c.putBoolean("isMyTeam", it1) }
                        f.arguments = c
                        (context as OnHomeItemClickListener).openCallFragment(f)
                    }
                }
            }
            fragmentServicesBinding.llServices.addView(b)
        }
    }

    private fun prependBaseUrlToHtmlImages(content: String?, baseUrl: String): String {
        if (content == null) return ""

        // Pattern for HTML img tags
        val htmlPattern = "<img\\s+src=\"(.*?)\""
        val htmlImagePattern = Pattern.compile(htmlPattern)
        val htmlMatcher = htmlImagePattern.matcher(content)

        val result = StringBuffer()

        // Replace HTML image URLs
        while (htmlMatcher.find()) {
            val relativePath = htmlMatcher.group(1)
            val modifiedPath = relativePath?.replaceFirst("resources/".toRegex(), "")
            val fullUrl = baseUrl + modifiedPath
            htmlMatcher.appendReplacement(result, "<img src=\"$fullUrl\" width=600 height=350/>")
        }
        htmlMatcher.appendTail(result)

        return result.toString()
    }
}
