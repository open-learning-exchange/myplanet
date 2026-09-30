package org.ole.planet.myplanet.ui.teams.members

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseBindingFragment
import org.ole.planet.myplanet.databinding.FragmentMemberDetailBinding
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.utils.ImageUtils
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class MembersDetailFragment : BaseBindingFragment<FragmentMemberDetailBinding>(FragmentMemberDetailBinding::inflate) {
    private val viewModel: MembersDetailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let { args ->
            val fullName = args.getString("member_name")?.trim()
            val username = args.getString("username")?.trim()
            val imageUrl = args.getString("profile_photo_url")
            val memberId = args.getString("member_id")
            val loginName = args.getString("member_login_name")

            binding.tvProfileName.text = if (fullName.isNullOrEmpty()) username else fullName
            ImageUtils.loadProfileImage(
                imageUrl,
                binding.memberImage,
                resources.getDimensionPixelSize(R.dimen.user_image_size)
            )

            setFieldOrHide(binding.tvFullName, fullName)
            setFieldOrHide(binding.tvProfileEmail, args.getString("profile_email"))
            setFieldOrHide(binding.tvDetailDob, args.getString("detail_dob"))
            setFieldOrHide(binding.tvDetailLanguage, args.getString("detail_language"))
            setFieldOrHide(binding.tvProfilePhone, args.getString("profile_phone"))
            setFieldOrHide(binding.tvLevel, args.getString("user_level"))

            viewModel.loadMemberVisitStats(memberId, loginName)

            collectWhenStarted(viewModel.visitStats) { stats ->
                setFieldOrHide(binding.tvNumberOfVisits, stats?.numberOfVisits)
                setFieldOrHide(
                    binding.tvLastLogin,
                    stats?.let { it.lastLogin ?: getString(R.string.no_logout_record_found) }
                )
            }
        }

        binding.btnClose.setOnClickListener {
            activity?.supportFragmentManager?.let { FragmentNavigator.popBackStack(it) }
        }
    }

    private fun setFieldOrHide(view: View, value: String?) {
        val shouldShow = value != null
                && value != "null"
                && !value.isBlank()
        if (shouldShow) {
            when (view) {
                is AppCompatTextView -> view.text = value
            }
            view.visibility = View.VISIBLE
            (view.parent as? View)?.visibility = View.VISIBLE
        } else {
            view.visibility = View.GONE
            (view.parent as? View)?.visibility = View.GONE
        }
    }

    companion object {
        fun newInstance(args: MemberDetailArgs) = MembersDetailFragment().apply {
            arguments = Bundle().apply {
                putString("member_id", args.id)
                putString("member_login_name", args.loginName)
                putString("member_name", args.name)
                putString("profile_email", args.email)
                putString("detail_dob", args.dob)
                putString("detail_language", args.language)
                putString("profile_phone", args.phone)
                putString("username", args.username)
                putString("user_level", args.memberLevel)
                putString("profile_photo_url", args.imageUrl)
            }
        }
    }
}
