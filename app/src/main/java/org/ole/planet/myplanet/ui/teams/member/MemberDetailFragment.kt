package org.ole.planet.myplanet.ui.teams.member

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentMemberDetailBinding
import org.ole.planet.myplanet.ui.navigation.NavigationHelper

class MemberDetailFragment : Fragment() {
    private var _binding: FragmentMemberDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemberDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let { args ->
            val fullName = args.getString("member_name")?.trim()
            val username = args.getString("username")?.trim()
            val imageUrl = args.getString("profile_photo_url")
            binding.tvProfileName.text = if (fullName.isNullOrEmpty()) username else fullName
            Glide.with(requireContext())
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(binding.memberImage)

            setFieldOrHide(binding.tvFullName, fullName)
            setFieldOrHide(binding.tvProfileEmail, args.getString("profile_email"))
            setFieldOrHide(binding.tvDetailDob, args.getString("detail_dob"))
            setFieldOrHide(binding.tvDetailLanguage, args.getString("detail_language"))
            setFieldOrHide(binding.tvProfilePhone, args.getString("profile_phone"))
            setFieldOrHide(binding.tvNumberOfVisits, args.getString("number_of_visits"))
            setFieldOrHide(binding.tvLastLogin, args.getString("last_login"))
            setFieldOrHide(binding.tvLevel, args.getString("user_level"))
        }

        binding.btnClose.setOnClickListener {
            activity?.supportFragmentManager?.let { NavigationHelper.popBackStack(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setFieldOrHide(view: View, value: String?) {
        val shouldShow = value != null
                && value != "null"
                && !value.isBlank()
        if (shouldShow) {
            when (view) {
                is androidx.appcompat.widget.AppCompatTextView -> view.text = value
            }
            view.visibility = View.VISIBLE
            (view.parent as? View)?.visibility = View.VISIBLE
        } else {
            view.visibility = View.GONE
            (view.parent as? View)?.visibility = View.GONE
        }
    }


    companion object {
        @JvmStatic
        fun newInstance(
            name: String,
            email: String,
            dob: String,
            language: String,
            phone: String,
            visits: String,
            lastLogin: String,
            username: String,
            memberLevel: String,
            imageUrl: String?
        ) = MemberDetailFragment().apply {
            arguments = Bundle().apply {
                putString("member_name", name)
                putString("profile_email", email)
                putString("detail_dob", dob)
                putString("detail_language", language)
                putString("profile_phone", phone)
                putString("number_of_visits", visits)
                putString("last_login", lastLogin)
                putString("username", username)
                putString("user_level", memberLevel)
                putString("profile_photo_url", imageUrl)
            }
        }
    }
}
