package org.ole.planet.myplanet.ui.teams.courses

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseTeamFragment
import org.ole.planet.myplanet.callback.OnTeamPageListener
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.databinding.MyLibraryAlertdialogBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.components.CheckboxListView
import org.ole.planet.myplanet.utils.Utilities

class TeamCoursesFragment : BaseTeamFragment(), OnTeamPageListener {
    private var _binding: FragmentTeamCourseBinding? = null
    private val binding get() = _binding!!
    private var adapterTeamCourse: TeamCoursesAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamCourseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCoursesList()
    }

    private fun setupCoursesList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val courses = teamsRepository.getTeamCourses(teamId)
            adapterTeamCourse = TeamCoursesAdapter(requireActivity(), courses.toMutableList(), mRealm, teamId, settings)
            binding.rvCourse.layoutManager = LinearLayoutManager(activity)
            binding.rvCourse.adapter = adapterTeamCourse
            adapterTeamCourse?.let {
                showNoData(binding.tvNodata, it.itemCount, "teamCourses")
            }
        }
    }

    fun updateCoursesList() {
        setupCoursesList()
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun onAddCourse() {
        if (isAdded && activity != null) {
            Utilities.toast(requireActivity(), getString(R.string.courses_loading))
        }
        showAddCourseDialog()
    }

    override fun onAddDocument() {
        showAddCourseDialog()
    }

    private fun showAddCourseDialog() {
        if (!isAdded || activity == null) {
            return
        }
        val safeActivity = activity ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val existingCourses = teamsRepository.getTeamCourses(teamId)
                val existingIds = existingCourses.mapNotNull { it.courseId }
                val allCourses = coursesRepository.getAllCourses()
                val availableCourses = allCourses.filter { it.courseId !in existingIds }

                if (availableCourses.isEmpty()) {
                    Utilities.toast(safeActivity, getString(R.string.no_courses))
                    return@launch
                }

                val titleView = TextView(safeActivity).apply {
                    text = getString(R.string.select_courses)
                    setTextColor(context.getColor(R.color.daynight_textColor))
                    setPadding(75, 50, 0, 0)
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                }

                val dialogBinding = MyLibraryAlertdialogBinding.inflate(layoutInflater)
                val alertDialogBuilder = AlertDialog.Builder(safeActivity)
                    .setCustomTitle(titleView)

                alertDialogBuilder.setView(dialogBinding.root)
                    .setPositiveButton(R.string.add) { _: DialogInterface?, _: Int ->
                        val selectedIndices = dialogBinding.alertDialogListView.selectedItemsList
                        val selectedCourses = selectedIndices.map { availableCourses[it] }
                        addCoursesToTeam(selectedCourses)
                    }.setNegativeButton(R.string.cancel, null)

                val alertDialog = alertDialogBuilder.create()
                alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
                setupCourseListDialog(alertDialog, availableCourses, dialogBinding.alertDialogListView)
            } catch (e: Exception) {
                if (isAdded) {
                    Utilities.toast(safeActivity, getString(R.string.error, e.message))
                }
            }
        }
    }

    private fun setupCourseListDialog(alertDialog: AlertDialog, courses: List<RealmMyCourse>, lv: CheckboxListView) {
        val names = courses.map { it.courseTitle ?: getString(R.string.untitled_course) }
        val adapter = ArrayAdapter(requireActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, names)
        lv.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lv.setCheckChangeListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = lv.selectedItemsList.isNotEmpty()
        }
        lv.adapter = adapter
        alertDialog.show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
    }

    private fun addCoursesToTeam(courses: List<RealmMyCourse>) {
        if (courses.isEmpty()) return
        val courseIds = courses.mapNotNull { it.courseId }
        if (courseIds.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                teamsRepository.addCoursesToTeam(teamId, courseIds)
                if (isAdded) {
                    Utilities.toast(requireActivity(), getString(R.string.added_to_my_courses))
                    updateCoursesList()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    Utilities.toast(requireActivity(), getString(R.string.error, e.message))
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
