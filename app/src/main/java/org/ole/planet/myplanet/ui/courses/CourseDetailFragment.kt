package org.ole.planet.myplanet.ui.courses

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.AddNoteDialogBinding
import org.ole.planet.myplanet.databinding.ChatShareDialogBinding
import org.ole.planet.myplanet.databinding.FragmentCourseDetailBinding
import org.ole.planet.myplanet.databinding.GrandChildRecyclerviewDialogBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getCourseSteps
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatingsById
import org.ole.planet.myplanet.model.RealmStepExam.Companion.getNoOfExam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.news.ExpandableListAdapter
import org.ole.planet.myplanet.ui.news.GrandChildAdapter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class CourseDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private lateinit var fragmentCourseDetailBinding: FragmentCourseDetailBinding
    lateinit var dbService: DatabaseService
    private lateinit var cRealm: Realm
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
        fragmentCourseDetailBinding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        dbService = DatabaseService(requireActivity())
        cRealm = dbService.realmInstance
        courses = cRealm.where(RealmMyCourse::class.java).equalTo("courseId", id).findFirst()
        user = UserProfileDbHandler(requireContext()).userModel
        return fragmentCourseDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRatingView("course", courses?.courseId, courses?.courseTitle, this)
        setCourseData()
    }

    private fun setCourseData() {
        setTextViewVisibility(fragmentCourseDetailBinding.subjectLevel, courses?.subjectLevel, fragmentCourseDetailBinding.ltSubjectLevel)
        setTextViewVisibility(fragmentCourseDetailBinding.method, courses?.method, fragmentCourseDetailBinding.ltMethod)
        setTextViewVisibility(fragmentCourseDetailBinding.gradeLevel, courses?.gradeLevel, fragmentCourseDetailBinding.ltGradeLevel)
        setTextViewVisibility(fragmentCourseDetailBinding.language, courses?.languageOfInstruction, fragmentCourseDetailBinding.ltLanguage)
        val markdownContentWithLocalPaths = CourseStepFragment.prependBaseUrlToImages(courses?.description, "file://" + MainApplication.context.getExternalFilesDir(null) + "/ole/")
        setMarkdownText(fragmentCourseDetailBinding.description, markdownContentWithLocalPaths)
        fragmentCourseDetailBinding.description.setOnLongClickListener {
            copyToClipboard("${fragmentCourseDetailBinding.description.getText()}")
            true
        }
        fragmentCourseDetailBinding.noOfExams.text = context?.getString(R.string.number_placeholder, getNoOfExam(cRealm, id))
        val resources: List<RealmMyLibrary> = cRealm.where(RealmMyLibrary::class.java).equalTo("courseId", id).equalTo("resourceOffline", false).isNotNull("resourceLocalAddress").findAll()
        setResourceButton(resources, fragmentCourseDetailBinding.btnResources)
        val downloadedResources: List<RealmMyLibrary> = cRealm.where(RealmMyLibrary::class.java).equalTo("resourceOffline", true).equalTo("courseId", id).isNotNull("resourceLocalAddress").findAll()
        setOpenResourceButton(downloadedResources, fragmentCourseDetailBinding.btnOpen)
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
        val steps = getCourseSteps(cRealm, courses?.courseId)
        fragmentCourseDetailBinding.stepsList.layoutManager = LinearLayoutManager(activity)
        fragmentCourseDetailBinding.stepsList.adapter = AdapterSteps(requireActivity(), steps, cRealm)
    }

    override fun onRatingChanged() {
        val `object` = getRatingsById(cRealm, "course", courses?.courseId, user?.id)
        setRatings(`object`)
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        setCourseData()
    }

//    private fun shareText(text: String) {
//        val intent = Intent(Intent.ACTION_SEND)
//        intent.type = "text/plain"
//        intent.putExtra(Intent.EXTRA_TEXT, text)
//        startActivity(Intent.createChooser(intent, "Share via"))
//    }

    private fun shareText(selectedText: String) {
        val chatShareDialogBinding = ChatShareDialogBinding.inflate(LayoutInflater.from(requireContext()))
        var dialog: AlertDialog? = null

        val expandableDetailList = getData() as HashMap<String, List<String>>
        val expandableTitleList = ArrayList(expandableDetailList.keys)
        val expandableListAdapter = ExpandableListAdapter(requireContext(), expandableTitleList, expandableDetailList)
        chatShareDialogBinding.listView.setAdapter(expandableListAdapter)

        chatShareDialogBinding.listView.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
            if (expandableTitleList[groupPosition] == "share with team/enterprise") {
                val teamList = getTeamsOrEnterprises("team")
                val enterpriseList = getTeamsOrEnterprises("enterprise")

                if (expandableDetailList[expandableTitleList[groupPosition]]?.get(childPosition) == "teams") {
                    showGrandChildRecyclerView(teamList, "teams", selectedText)
                } else {
                    showGrandChildRecyclerView(enterpriseList, "enterprises", selectedText)
                }
            } else {
                val community = getCommunity()
                showEditTextAndShareButton(community, "community", selectedText)
            }
            dialog?.dismiss()
            false
        }

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(chatShareDialogBinding.root)
        builder.setPositiveButton("Close") { _, _ ->
            dialog?.dismiss()
        }
        dialog = builder.create()
        dialog.show()
    }

    private fun getTeamsOrEnterprises(type: String): List<RealmMyTeam> {
        return mRealm.where(RealmMyTeam::class.java)
            .isEmpty("teamId").notEqualTo("status", "archived")
            .equalTo("type", type).findAll()
    }

    private fun getCommunity(): RealmMyTeam? {
        val settings = requireContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val sParentcode = settings?.getString("parentCode", "")
        val communityName = settings?.getString("communityName", "")
        val teamId = "$communityName@$sParentcode"
        return mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
    }

    private fun showGrandChildRecyclerView(items: List<RealmMyTeam>, section: String, selectedText: String) {
        val grandChildDialogBinding = GrandChildRecyclerviewDialogBinding.inflate(LayoutInflater.from(requireContext()))
        var dialog: AlertDialog? = null

        val titleText = if (section == "teams") getString(R.string.team) else getString(R.string.enterprises)
        grandChildDialogBinding.title.text = titleText

        val grandChildAdapter = GrandChildAdapter(items) { selectedItem ->
            showEditTextAndShareButton(selectedItem, section, selectedText)
            dialog?.dismiss()
        }
        grandChildDialogBinding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        grandChildDialogBinding.recyclerView.adapter = grandChildAdapter

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(grandChildDialogBinding.root)
        builder.setPositiveButton("Close") { _, _ ->
            dialog?.dismiss()
        }
        dialog = builder.create()
        dialog.show()
    }

    private fun showEditTextAndShareButton(team: RealmMyTeam?, section: String, selectedText: String) {
        val addNoteDialogBinding = AddNoteDialogBinding.inflate(LayoutInflater.from(requireContext()))
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(addNoteDialogBinding.root)
        builder.setPositiveButton(getString(R.string.share_chat)) { dialog, _ ->
            val map = HashMap<String?, String>().apply {
                put("message", selectedText)
                put("viewInId", team?._id ?: "")
                put("viewInSection", section)

            }
//            createNews(map)
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun getData(): Map<String, List<String>> {
        val expandableListDetail: MutableMap<String, List<String>> = HashMap()
        val community: MutableList<String> = ArrayList()
        community.add("community")

        val teams: MutableList<String> = ArrayList()
        teams.add("teams")
        teams.add("enterprises")

        expandableListDetail["share with community"] = community
        expandableListDetail["share with team/enterprise"] = teams
        return expandableListDetail
    }

    private fun researchText(text: String) {
//        val intent = Intent(Intent.ACTION_WEB_SEARCH)
//        intent.putExtra(SearchManager.QUERY, text)
//        startActivity(intent)
        val bundle = Bundle()
        bundle.putString("selectedText", text)
        val chatDetailFragment = ChatDetailFragment()
        chatDetailFragment.arguments = bundle
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, chatDetailFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("copied Text", text)
        clipboard.setPrimaryClip(clip)

        val options = arrayOf("Share", "Research")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Select Action")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareText(text)
                    1 -> researchText(text)
                }
            }
        builder.create().show()
        Toast.makeText(context, context?.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }
}
