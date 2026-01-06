package org.ole.planet.myplanet.ui.teams.courses

import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.TeamPageListener
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.databinding.MyLibraryAlertdialogBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.components.CheckboxListView
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.ui.teams.BaseTeamFragment

class TeamCoursesFragment : BaseTeamFragment(), TeamPageListener {
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
        val freshTeam = mRealm.where(org.ole.planet.myplanet.model.RealmMyTeam::class.java)
            .equalTo("_id", teamId)
            .findFirst()
        
        team = freshTeam
        
        val courseIds = freshTeam?.courses?.toTypedArray<String>() ?: emptyArray()
        val courses = if (courseIds.isNotEmpty()) {
            mRealm.where(RealmMyCourse::class.java).`in`("courseId", courseIds).findAll()
        } else {
            mRealm.where(RealmMyCourse::class.java).alwaysFalse().findAll()
        }

        adapterTeamCourse = TeamCoursesAdapter(requireActivity(), courses.toMutableList(), mRealm, teamId, settings)
        binding.rvCourse.layoutManager = LinearLayoutManager(activity)
        binding.rvCourse.adapter = adapterTeamCourse
        adapterTeamCourse?.let {
            showNoData(binding.tvNodata, it.itemCount, "teamCourses")
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
        Log.d("TeamCoursesFragment", "onAddCourse() called")
        showAddCourseDialog()
    }

    private fun showAddCourseDialog() {
        Log.d("TeamCoursesFragment", "showAddCourseDialog() called, isAdded: $isAdded, activity: ${activity != null}")
        if (!isAdded || activity == null) {
            Log.w("TeamCoursesFragment", "Cannot show dialog - fragment not added or activity is null")
            return
        }
        val safeActivity = activity ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val freshTeam = mRealm.where(org.ole.planet.myplanet.model.RealmMyTeam::class.java)
                .equalTo("_id", teamId)
                .findFirst()

            val existingIds = freshTeam?.courses?.toList() ?: emptyList()
            val allCourses = mRealm.where(RealmMyCourse::class.java).findAll()
            val availableCourses = allCourses.filter { it.courseId !in existingIds }

            if (availableCourses.isEmpty()) {
                Utilities.toast(safeActivity, getString(R.string.no_courses))
                return@launch
            }

            val titleView = TextView(safeActivity).apply {
                text = context.getString(R.string.select_courses)
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
        }
    }

    private fun setupCourseListDialog(alertDialog: AlertDialog, courses: List<RealmMyCourse>, lv: CheckboxListView) {
        val names = courses.map { it.courseTitle ?: "Untitled Course" }
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
        if (team == null || courses.isEmpty()) return
        val teamId = team?._id ?: return

        val courseIds = courses.mapNotNull { it.courseId }
        if (courseIds.isEmpty()) return

        mRealm.executeTransactionAsync(
            { realm ->
                val managedTeam = realm.where(org.ole.planet.myplanet.model.RealmMyTeam::class.java)
                    .equalTo("_id", teamId)
                    .findFirst()

                managedTeam?.let {
                    courseIds.forEach { courseId ->
                        if (!it.courses!!.contains(courseId)) {
                            it.courses!!.add(courseId)
                        }
                    }
                    it.updated = true
                }
            },
            {
                if (isAdded) {
                    Utilities.toast(requireActivity(), getString(R.string.added_to_my_courses))
                    updateCoursesList()
                }
            },
            { error ->
                if (isAdded) {
                    Utilities.toast(requireActivity(), "Error adding courses: ${error.message}")
                    Log.d("TeamCoursesFragment", "Error adding courses: ${error.message}")
                }
            }
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
