package org.ole.planet.myplanet.ui.team

import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentPlanBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class PlanFragment : BaseTeamFragment() {
    private lateinit var fragmentPlanBinding: FragmentPlanBinding
    private var missionText: String? = null
    private var servicesText: String? = null
    private var rulesText = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentPlanBinding = FragmentPlanBinding.inflate(inflater, container, false)
        return fragmentPlanBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (TextUtils.equals(team?.type, "enterprise")) {
            missionText = if (team?.description?.trim { it <= ' ' }?.isEmpty() == true) {
                ""
            } else {
                "<b>" + getString(R.string.entMission) + "</b><br/>" + team?.description + "<br/><br/>"
            }
            servicesText = if (team?.services?.trim { it <= ' ' }?.isEmpty() == true) {
                ""
            } else {
                "<b>" + getString(R.string.entServices) + "</b><br/>" + team?.services + "<br/><br/>"
            }
            rulesText = if (team?.rules?.trim { it <= ' ' }?.isEmpty() == true) {
                ""
            } else {
                "<b>" + getString(R.string.entRules) + "</b><br/>" + team?.rules
            }
            fragmentPlanBinding.tvDescription.text = Html.fromHtml(missionText + servicesText + rulesText, Html.FROM_HTML_MODE_LEGACY)
            if (fragmentPlanBinding.tvDescription.text.toString().isEmpty()) {
                fragmentPlanBinding.tvDescription.text = Html.fromHtml("<br/>" + getString(R.string.entEmptyDescription) + "<br/>", Html.FROM_HTML_MODE_LEGACY)
            }
        } else {
            fragmentPlanBinding.tvDescription.text = team?.description
        }
        fragmentPlanBinding.tvDate.text = getString(R.string.two_strings, getString(R.string.created_on), team?.createdDate?.let { formatDate(it) })

        fragmentPlanBinding.btnAddPlan.setOnClickListener {
            editTeam()
        }
    }

    private fun editTeam() {
        if (!isAdded) {
            return
        }

        val existingTeamFragment = parentFragmentManager.findFragmentByTag("TeamFragment") as? TeamFragment

        if (existingTeamFragment != null) {
            team?.let { existingTeamFragment.createTeamAlert(it) }
        } else {
            val newTeamFragment = TeamFragment()
            parentFragmentManager.beginTransaction()
                .add(newTeamFragment, "TeamFragment")
                .commit()

            parentFragmentManager.executePendingTransactions()

            newTeamFragment.view?.post {
                team?.let { newTeamFragment.createTeamAlert(it) }

            }
        }
    }


    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }
}