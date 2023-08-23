package org.ole.planet.myplanet.utilities

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import me.toptas.fancyshowcase.FancyShowCaseQueue
import me.toptas.fancyshowcase.FancyShowCaseView
import me.toptas.fancyshowcase.FocusShape
import me.toptas.fancyshowcase.listener.OnViewInflateListener
import org.ole.planet.myplanet.MainApplication.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityLoginBinding

object Tutorials {
    private var queue = FancyShowCaseQueue()
    private lateinit var dashboardReferenceHolder: DashboardReferenceHolder

    fun setDashboardReferenceHolder(holder: DashboardReferenceHolder) {
        dashboardReferenceHolder = holder
    }

    fun loginTutorials(bind: ActivityLoginBinding, activity: FragmentActivity) {
        val a = fancyShowCaseViewRoundedRectSkippable(activity, bind.syncIcon, context.getString(R.string.press_the_sync_button_to_sync_your_planet_account_data_with_your_myplanet_application_data))
        val b = fancyShowCaseViewRoundedRectSkippable(activity, bind.imgBtnSetting, context.getString(R.string.press_the_settings_button_to_access_your_myplanet_planet_account_server_settings_to_properly_set_up_your_syncing_process))
        show(a, b)
    }

    fun dashboardTutorials(activity: FragmentActivity) {
        val a = dashboardReferenceHolder.begin?.let {
            fancyShowCaseViewRoundedRectSkippable(activity,
                it, context.getString(R.string.please_make_sure_your_device_is_horizontal))
        };
        val b = dashboardReferenceHolder.img?.let {
            fancyShowCaseViewRoundedRectSkippable(activity,
                it, context.getString(R.string.click_on_the_logo_to_get_the_full_menu_of_your_planet_home_mylibrary_mycourses_library_courses_community_enterprises_and_surveys))
        };
        val c = dashboardReferenceHolder.menuh?.customView?.let {
            fancyShowCaseViewRoundedRectSkippable(activity,
                it, context.getString(R.string.navigate_to_the_home_tab_to_access_your_dashboard_with_your_library_courses_and_teams))
        };
        val d = dashboardReferenceHolder.menul?.customView?.let {
            fancyShowCaseViewRoundedRectSkippable(activity,
                it, context.getString(R.string.navigate_to_the_library_tab_to_access_resources_in_your_community))
        };
        val e = dashboardReferenceHolder.menuc?.customView?.let {
            fancyShowCaseViewRoundedRectSkippable(activity,
                it, context.getString(R.string.navigate_to_the_courses_tab_to_access_the_courses_exams_questions_lessons_within_your_community))
        };
        val f = dashboardReferenceHolder.menut?.customView?.let {
            fancyShowCaseViewRoundedRectSkippable(activity,
                it, context.getString(R.string.navigate_to_the_teams_tab_to_join_request_and_check_up_on_your_teams))
        };
        val g = dashboardReferenceHolder.menue?.customView?.let {
            fancyShowCaseViewRoundedRectSkippable(activity,
                it, context.getString(R.string.navigate_to_the_enterprises_tab_to_search_through_a_list_of_enterprises_within_your_community))
        };
        val h = dashboardReferenceHolder.menuco?.customView?.let {
            fancyShowCaseViewRoundedRectSkippable(activity,
                it, context.getString(R.string.navigate_to_the_community_tab_to_access_the_news_community_leaders_calendar_services_and_finances_involved_within_your_community))
        };
        show(a!!, b!!, c!!, d!!,e!!,f!!,g!!,h!!)
    }

    fun fancyShowCaseViewBuilderSkippable(activity: FragmentActivity, view: View, title: String, focusShape: FocusShape = FocusShape.CIRCLE): FancyShowCaseView.Builder {
        return fancyShowCaseViewBuilder(activity, view, title, focusShape)
            .customView(R.layout.tutorial, object : OnViewInflateListener {
                override fun onViewInflated(view: View) {
                    val skipButton = view.findViewById<Button>(R.id.skipBtn)
                    skipButton.setOnClickListener(mClickListener)
                    val text = view.findViewById<TextView>(R.id.text)
                    text.text = title
                }
            })
    }

    fun fancyShowCaseViewBuilder(activity: FragmentActivity, view: View, title: String, focusShape: FocusShape = FocusShape.CIRCLE): FancyShowCaseView.Builder {
        return FancyShowCaseView.Builder(activity)
            .focusOn(view)
            .title(title)
            .enableAutoTextPosition()
            .backgroundColor(R.color.dialog_sync_labels)
            .focusShape(focusShape)
            .fitSystemWindows(true)
            .delay(750)
    }

    private fun fancyShowCaseView(activity: FragmentActivity, view: View, title: String, focusShape: FocusShape): FancyShowCaseView {
        return fancyShowCaseViewBuilder(activity, view, title, focusShape).build()
    }

    private fun fancyShowCaseViewRoundedRect(activity: FragmentActivity, view: View, title: String, focusShape: FocusShape = FocusShape.ROUNDED_RECTANGLE): FancyShowCaseView {
        return fancyShowCaseViewBuilder(activity, view, title, focusShape)
            .roundRectRadius(80)
            .build()
    }

    private fun fancyShowCaseViewRoundedRectSkippable(activity: FragmentActivity, view: View, title: String): FancyShowCaseView {
        return fancyShowCaseViewBuilderSkippable(activity, view, title, FocusShape.ROUNDED_RECTANGLE)
            .roundRectRadius(80)
            .build()
    }

    private fun show(vararg view: FancyShowCaseView) {
        queue = FancyShowCaseQueue()
        for (v in view) {
            queue.add(v)
        }
        queue.show()
    }

    private var mClickListener = View.OnClickListener { queue.cancel(true) }
}
}