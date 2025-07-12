package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import org.json.JSONObject
import org.ole.planet.myplanet.databinding.FragmentSubmissionDetailBinding
import org.ole.planet.myplanet.model.RealmSubmission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubmissionDetailFragment : Fragment() {
    private lateinit var fragmentSubmissionDetailBinding: FragmentSubmissionDetailBinding
    private var submissionId: String? = null
    private var submission: RealmSubmission? = null
    private lateinit var mRealm: Realm

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentSubmissionDetailBinding = FragmentSubmissionDetailBinding.inflate(inflater, container, false)
        return fragmentSubmissionDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        submissionId = arguments?.getString("id")
        mRealm = Realm.getDefaultInstance()

        submission = loadSubmission()
        Log.d("SubmissionDetailFragment", "Submission ID: $submission")

        // Now display the submission data in the UI
        displaySubmissionData()
    }

    private fun loadSubmission(): RealmSubmission? {
        submissionId?.let { id ->
            return mRealm.where(RealmSubmission::class.java)
                .equalTo("id", id)
                .findFirst()
        }
        return null
    }

    private fun displaySubmissionData() {
        submission?.let { sub ->
            with(fragmentSubmissionDetailBinding) {
                // Set survey title from parent - Direct approach based on your log
                try {
                    val parentObj = sub.parent
                    Log.d("SubmissionDetailFragment", "Parent object type: ${parentObj?.javaClass?.name}")
                    Log.d("SubmissionDetailFragment", "Parent object: $parentObj")

                    if (parentObj != null) {
                        when (parentObj) {
                            is Map<*, *> -> {
                                val name = parentObj["name"] as? String
                                tvSurveyTitle.text = name ?: "Untitled Survey"
                            }
                            else -> {
                                // Based on your log: {"name":"Working with GitHub as a OLE Virtual Intern - 1",...}
                                val parentString = parentObj.toString()
                                val nameRegex = "\"name\":\"([^\"]+)\"".toRegex()
                                val nameMatch = nameRegex.find(parentString)

                                if (nameMatch != null) {
                                    tvSurveyTitle.text = nameMatch.groupValues[1]
                                } else {
                                    // Try reflection as a last resort
                                    try {
                                        val field = parentObj.javaClass.getDeclaredField("name")
                                        field.isAccessible = true
                                        val nameValue = field.get(parentObj) as? String
                                        tvSurveyTitle.text = nameValue ?: "Untitled Survey"
                                    } catch (e: Exception) {
                                        Log.e("SubmissionDetailFragment", "Error getting parent name: ${e.message}")
                                        tvSurveyTitle.text = "Untitled Survey"
                                    }
                                }
                            }
                        }
                    } else {
                        tvSurveyTitle.text = "Untitled Survey"
                    }
                } catch (e: Exception) {
                    Log.e("SubmissionDetailFragment", "Error getting survey title: ${e.message}", e)
                    tvSurveyTitle.text = "Untitled Survey"
                }

                // Set status chip
                chipStatus.text = sub.status
                // Change chip color based on status
                when (sub.status?.lowercase()) {
                    "complete" -> {
//                        chipStatus.setChipBackgroundColorResource(R.color.green_200)
                    }
                    "requires grading" -> {
//                        chipStatus.setChipBackgroundColorResource(R.color.orange_200)
                    }
                    else -> {
//                        chipStatus.setChipBackgroundColorResource(R.color.gray_200)
                    }
                }

                // Set submission details
                val startDateFormatted = sub.startTime?.let { formatDateTime(it) } ?: "N/A"
                tvStartTime.text = startDateFormatted

                val completedDateFormatted = sub.lastUpdateTime?.let { formatDateTime(it) } ?: "N/A"
                tvCompletedTime.text = completedDateFormatted

                // Set user info - Specific approach based on your log output
                try {
                    // Try to directly access the nested properties shown in your log
                    // From the log: {user:{"firstName":"Gideon","lastName":"Okuro","email":"giddie@test.com"...}}
                    val userObj = sub.user

                    if (userObj != null) {
                        // Log the user object type to debug
                        Log.d("SubmissionDetailFragment", "User object type: ${userObj.javaClass.name}")
                        Log.d("SubmissionDetailFragment", "User object toString: ${userObj.toString()}")

                        when (userObj) {
                            is Map<*, *> -> {
                                // If user is already a Map
                                val firstName = userObj["firstName"] as? String ?: ""
                                val lastName = userObj["lastName"] as? String ?: ""
                                val email = userObj["email"] as? String ?: ""
                                val userDisplayText = "$firstName $lastName ($email)"
                                tvUserInfo.text = userDisplayText
                            }
                            is String -> {
                                // If user is a JSON string
                                try {
                                    val userJson = JSONObject(userObj)
                                    val firstName = userJson.optString("firstName", "")
                                    val lastName = userJson.optString("lastName", "")
                                    val email = userJson.optString("email", "")
                                    val userDisplayText = "$firstName $lastName ($email)"
                                    tvUserInfo.text = userDisplayText
                                } catch (e: Exception) {
                                    Log.e("SubmissionDetailFragment", "Error parsing user JSON: ${e.message}")
                                    tvUserInfo.text = userObj
                                }
                            }
                            else -> {
                                // Last resort - try to extract the data as shown in your log
                                val userString = userObj.toString()
                                if (userString.contains("firstName") && userString.contains("lastName")) {
                                    // Try to extract values using regex
                                    val firstNameRegex = "\"firstName\":\"([^\"]+)\"".toRegex()
                                    val lastNameRegex = "\"lastName\":\"([^\"]+)\"".toRegex()
                                    val emailRegex = "\"email\":\"([^\"]+)\"".toRegex()

                                    val firstNameMatch = firstNameRegex.find(userString)
                                    val lastNameMatch = lastNameRegex.find(userString)
                                    val emailMatch = emailRegex.find(userString)

                                    val firstName = firstNameMatch?.groupValues?.get(1) ?: ""
                                    val lastName = lastNameMatch?.groupValues?.get(1) ?: ""
                                    val email = emailMatch?.groupValues?.get(1) ?: ""

                                    val userDisplayText = "$firstName $lastName ($email)"
                                    tvUserInfo.text = userDisplayText
                                } else {
                                    tvUserInfo.text = "User: $userString"
                                }
                            }
                        }
                    } else {
                        tvUserInfo.text = "No user information"
                    }
                } catch (e: Exception) {
                    Log.e("SubmissionDetailFragment", "Error extracting user info: ${e.message}", e)
                    tvUserInfo.text = "Error parsing user data"
                }
                setupAnswersRecyclerView(sub)
            }
        } ?: run {
            Toast.makeText(context, "Submission not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())
        return formatter.format(date)
    }

    private fun setupAnswersRecyclerView(submission: RealmSubmission) {
        val answers = submission.answers

        // Extract questions from parent based on your log structure
        val questions = try {
            val parentObj = submission.parent

            if (parentObj is Map<*, *>) {
                parentObj["questions"] as? List<*>
            } else if (parentObj != null) {
                // Try to extract questions from the parent string representation
                val parentString = parentObj.toString()

                if (parentString.contains("\"questions\":[")) {
                    // Try parsing the questions as JSON
                    try {
                        val jsonObject = JSONObject(parentString)
                        jsonObject.optJSONArray("questions")?.let { jsonArray ->
                            val questionsList = mutableListOf<Map<String, Any>>()
                            for (i in 0 until jsonArray.length()) {
                                val questionObj = jsonArray.getJSONObject(i)
                                val questionMap = mutableMapOf<String, Any>()

                                // Extract keys from the question JSON
                                val keys = questionObj.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    questionMap[key] = questionObj.get(key)
                                }

                                questionsList.add(questionMap)
                            }
                            questionsList
                        }
                    } catch (e: Exception) {
                        Log.e("SubmissionDetailFragment", "Error parsing questions JSON: ${e.message}")
                        null
                    }
                } else {
                    try {
                        // Try reflection
                        val field = parentObj.javaClass.getDeclaredField("questions")
                        field.isAccessible = true
                        field.get(parentObj) as? List<*>
                    } catch (e: Exception) {
                        Log.e("SubmissionDetailFragment", "Error getting questions via reflection: ${e.message}")
                        null
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SubmissionDetailFragment", "Error extracting questions: ${e.message}")
            null
        }

        Log.d("SubmissionDetailFragment", "Questions: $questions")
        Log.d("SubmissionDetailFragment", "Answers: $answers")

        if (answers?.isEmpty() == true) {
            // Add a message when no answers available
            val noAnswersView = TextView(context).apply {
                text = "No responses available for this submission"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 16, 0, 16)
            }
            fragmentSubmissionDetailBinding.rvQuestionResponses.visibility = View.GONE

            // Find the LinearLayout parent inside the NestedScrollView and add the view to it
            val linearLayoutParent = (fragmentSubmissionDetailBinding.root as NestedScrollView)
                .getChildAt(0) as LinearLayout
            linearLayoutParent.addView(noAnswersView)
            return
        }

        // Setup adapter for answers
        val adapter = AnswerResponseAdapter(answers, questions)
        fragmentSubmissionDetailBinding.rvQuestionResponses.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::mRealm.isInitialized) {
            mRealm.close()
        }
    }
}