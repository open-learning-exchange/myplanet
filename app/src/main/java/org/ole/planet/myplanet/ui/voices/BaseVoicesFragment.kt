package org.ole.planet.myplanet.ui.voices

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.model.RealmVoices
import org.ole.planet.myplanet.utilities.FileUtils

abstract class BaseVoicesFragment : BaseResourceFragment() {
    lateinit var llImage: LinearLayout
    var imageList: MutableList<String> = ArrayList()
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        llImage = requireView().findViewById(R.id.ll_images)
    }

    val openFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            val imagePath = FileUtils.getRealPathFromURI(requireContext(), uri)
            imageList.add(imagePath!!)
            val imageView = ImageView(requireActivity()).apply {
                layoutParams = LinearLayout.LayoutParams(200, 200)
            }
            llImage.addView(imageView)
            FileUtils.loadImageFromFile(requireContext(), imagePath, imageView)
        }
    }

    fun changeLayoutManager(orientation: Int, rv: RecyclerView) {
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            rv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(activity, 2)
        } else {
            rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        }
    }

    fun showNoData(textView: TextView, count: Int, type: String) {
        val message = when (type) {
            "voices" -> getString(R.string.no_voices)
            "messages" -> getString(R.string.no_messages)
            "chats" -> getString(R.string.no_chats)
            "surveys" -> getString(R.string.no_surveys)
            "courses" -> getString(R.string.no_courses)
            "teams" -> getString(R.string.no_teams)
            "achievements" -> getString(R.string.no_achievements)
            "exams" -> getString(R.string.no_exams)
            "resources" -> getString(R.string.no_resources)
            "members" -> getString(R.string.no_members)
            "tasks" -> getString(R.string.no_tasks)
            "requests" -> getString(R.string.no_requests)
            "discussions" -> getString(R.string.no_discussions)
            "teamCourses" -> getString(R.string.no_team_courses)
            "teamResources" -> getString(R.string.no_team_resources)
            "finance" -> getString(R.string.no_finance_record)
            "enterprises" -> getString(R.string.no_enterprise)
            "feedback" -> getString(R.string.no_feedback)
            "submissions" -> getString(R.string.no_submissions)
            "reports" -> getString(R.string.no_reports)
            else -> getString(R.string.no_data_available)
        }
        textView.visibility = if (count == 0) android.view.View.VISIBLE else android.view.View.GONE
        textView.text = message
    }

    abstract fun setData(list: List<RealmVoices?>?)
    abstract fun onVoicesItemClick(voices: RealmVoices?)
    abstract fun clearImages()
}