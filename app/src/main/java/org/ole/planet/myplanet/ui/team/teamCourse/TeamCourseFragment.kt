package org.ole.planet.myplanet.ui.team.teamCourse

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
import org.ole.planet.myplanet.databinding.FragmentTeamCourseBinding
import org.ole.planet.myplanet.databinding.MyLibraryAlertdialogBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.ui.team.BaseTeamFragment
import org.ole.planet.myplanet.utilities.CheckboxListView

class TeamCourseFragment : BaseTeamFragment() {
    private var _binding: FragmentTeamCourseBinding? = null
    private val binding get() = _binding!!
    private var adapterTeamCourse: AdapterTeamCourse? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamCourseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCoursesList()
    }
    
    private fun setupCoursesList() {
        val courses = mRealm.where(RealmMyCourse::class.java).`in`("id", team?.courses?.toTypedArray<String>()).findAll()
        adapterTeamCourse = settings?.let { AdapterTeamCourse(requireActivity(), courses.toMutableList(), mRealm, teamId, it) }
        binding.rvCourse.layoutManager = LinearLayoutManager(activity)
        binding.rvCourse.adapter = adapterTeamCourse
        adapterTeamCourse?.let {
            showNoData(binding.tvNodata, it.itemCount, "teamCourses")
        }
    }
    
    fun updateCoursesList() {
        val courses = mRealm.where(RealmMyCourse::class.java).`in`("id", team?.courses?.toTypedArray<String>()).findAll()
        adapterTeamCourse?.updateList(courses)
        adapterTeamCourse?.let {
            showNoData(binding.tvNodata, it.itemCount, "teamCourses")
        }
    }

    private fun showCourseListDialog() {
        if (!isAdded || activity == null) return
        val safeActivity = activity ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val existing = teamRepository.getTeamCourses(teamId)
            val existingIds = existing.mapNotNull { it.courseId }
            val availableLibraries = courseRepository.getAllCourses()
                .filter { it.courseId !in existingIds }

            val titleView = TextView(safeActivity).apply {
                text = getString(R.string.select_resource)
                setTextColor(context.getColor(R.color.daynight_textColor))
                setPadding(75, 50, 0, 0)
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
            }

            val myLibraryAlertdialogBinding = MyLibraryAlertdialogBinding.inflate(layoutInflater)
            val alertDialogBuilder = AlertDialog.Builder(safeActivity)
                .setCustomTitle(titleView)

            alertDialogBuilder.setView(myLibraryAlertdialogBinding.root)
                .setPositiveButton(R.string.add) { _: DialogInterface?, _: Int ->
                    val selectedResources = myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList
                        .map { index -> availableLibraries[index] }
//                    viewLifecycleOwner.lifecycleScope.launch {
//                        teamRepository.addResourceLinks(teamId, selectedResources, user)
//                        //showLibraryList()
//                    }
                }.setNegativeButton(R.string.cancel, null)

            val alertDialog = alertDialogBuilder.create()
            alertDialog.window?.setBackgroundDrawableResource(R.color.card_bg)
            listSetting(alertDialog, availableLibraries, myLibraryAlertdialogBinding.alertDialogListView)
        }
    }

    private fun listSetting(alertDialog: AlertDialog, courses: List<RealmMyCourse>, lv: CheckboxListView) {
        val names = courses.map { it.courseTitle }
        val adapter = ArrayAdapter(requireActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, names)
        lv.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lv.setCheckChangeListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = lv.selectedItemsList.isNotEmpty()
        }
        lv.adapter = adapter
        alertDialog.show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = lv.selectedItemsList.isNotEmpty()
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
