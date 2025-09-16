package org.ole.planet.myplanet.ui.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentCourseDetailBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getCourseSteps
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatingsById
import org.ole.planet.myplanet.model.RealmStepExam.Companion.getNoOfExam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Markdown.prependBaseUrlToImages
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class CourseDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private var _binding: FragmentCourseDetailBinding? = null
    private val binding get() = _binding!!
    var courses: RealmMyCourse? = null
    var user: RealmUserModel? = null
    var id: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            id = requireArguments().getString("courseId")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        courses = databaseService.withRealm { realm ->
            realm.where(RealmMyCourse::class.java)
                .equalTo("courseId", id)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
        user = UserProfileDbHandler(requireContext()).userModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRatingView("course", courses?.courseId, courses?.courseTitle, this)
        setCourseData()
    }

    private fun setCourseData() {
        setTextViewVisibility(binding.subjectLevel, courses?.subjectLevel, binding.ltSubjectLevel)
        setTextViewVisibility(binding.method, courses?.method, binding.ltMethod)
        setTextViewVisibility(binding.gradeLevel, courses?.gradeLevel, binding.ltGradeLevel)
        setTextViewVisibility(binding.language, courses?.languageOfInstruction, binding.ltLanguage)
        val markdownContentWithLocalPaths = prependBaseUrlToImages(
            courses?.description,
            "file://" + MainApplication.context.getExternalFilesDir(null) + "/ole/",
            600,
            350
        )
        setMarkdownText(binding.description, markdownContentWithLocalPaths)
        databaseService.withRealm { realm ->
            binding.noOfExams.text = context?.getString(
                R.string.number_placeholder,
                getNoOfExam(realm, id)
            )
            val resources: List<RealmMyLibrary> = realm.where(RealmMyLibrary::class.java)
                .equalTo("courseId", id)
                .equalTo("resourceOffline", false)
                .isNotNull("resourceLocalAddress")
                .findAll()
                .let { realm.copyFromRealm(it) }
            setResourceButton(resources, binding.btnResources)
            val downloadedResources: List<RealmMyLibrary> = realm.where(RealmMyLibrary::class.java)
                .equalTo("resourceOffline", true)
                .equalTo("courseId", id)
                .isNotNull("resourceLocalAddress")
                .findAll()
                .let { realm.copyFromRealm(it) }
            setOpenResourceButton(downloadedResources, binding.btnOpen)
        }
        onRatingChanged()
        setStepsList()
    }

    private fun setTextViewVisibility(textView: TextView, content: String?, layout: View) {
        if (content?.isEmpty() == true) {
            layout.visibility = View.GONE
        } else {
            textView.text = content
        }
    }

    private fun setStepsList() {
        val steps = databaseService.withRealm { realm ->
            getCourseSteps(realm, courses?.courseId).let { realm.copyFromRealm(it) }
        }
        binding.stepsList.layoutManager = LinearLayoutManager(activity)
        binding.stepsList.adapter = AdapterSteps(requireActivity(), steps, submissionRepository)
    }

    override fun onRatingChanged() {
        databaseService.withRealm { realm ->
            val `object` = getRatingsById(realm, "course", courses?.courseId, user?.id)
            setRatings(`object`)
        }
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        setCourseData()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
