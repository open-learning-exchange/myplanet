package org.ole.planet.myplanet.ui.user

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.DialogCourseCertificateBinding
import org.ole.planet.myplanet.model.gamification.CourseCertificate

class CertificateDialogFragment : DialogFragment() {

    private var _binding: DialogCourseCertificateBinding? = null
    private val binding get() = _binding!!

    private var learnerName: String = ""
    private var courseTitle: String = ""
    private var completionDate: String = ""
    private var certificateId: String = ""
    private var organization: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            learnerName = it.getString(ARG_LEARNER_NAME, "")
            courseTitle = it.getString(ARG_COURSE_TITLE, "")
            completionDate = it.getString(ARG_COMPLETION_DATE, "")
            certificateId = it.getString(ARG_CERTIFICATE_ID, "")
            organization = it.getString(ARG_ORGANIZATION, "Open Learning Exchange")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCourseCertificateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvDialogLearnerName.text = learnerName
        binding.tvDialogCourseTitle.text = courseTitle
        binding.tvDialogCertDate.text = getString(R.string.date_issued_label, completionDate)
        binding.tvDialogCertId.text = getString(R.string.certificate_id_label, certificateId)

        binding.btnCloseCert.setOnClickListener {
            dismiss()
        }

        binding.btnShareCert.setOnClickListener {
            shareCertificate()
        }
    }

    private fun shareCertificate() {
        val shareText = """
            🎓 Certificate of Completion
            This certifies that $learnerName has successfully completed $courseTitle on $completionDate.
            Certificate ID: $certificateId
            Issued by $organization
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.share_certificate))
        startActivity(shareIntent)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_LEARNER_NAME = "arg_learner_name"
        private const val ARG_COURSE_TITLE = "arg_course_title"
        private const val ARG_COMPLETION_DATE = "arg_completion_date"
        private const val ARG_CERTIFICATE_ID = "arg_certificate_id"
        private const val ARG_ORGANIZATION = "arg_organization"

        fun newInstance(certificate: CourseCertificate): CertificateDialogFragment {
            return CertificateDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LEARNER_NAME, certificate.learnerName)
                    putString(ARG_COURSE_TITLE, certificate.courseTitle)
                    putString(ARG_COMPLETION_DATE, certificate.completionDate)
                    putString(ARG_CERTIFICATE_ID, certificate.certificateId)
                    putString(ARG_ORGANIZATION, certificate.organization)
                }
            }
        }
    }
}
