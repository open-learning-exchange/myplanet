package org.ole.planet.myplanet.ui.team.teamMember

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentMemberDetailBinding

class MemberDetailFragment : Fragment() {
    private lateinit var binding: FragmentMemberDetailBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMemberDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let { args ->
            val imageUrl = args.getString("profile_photo_url")
            Glide.with(requireContext())
                .load(imageUrl)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(binding.memberImage)
            binding.tvProfileName.text = args.getString("member_name")
            binding.tvFullName.text = args.getString("member_name")
            binding.tvProfileEmail.text = args.getString("profile_email")
            binding.tvDetailDob.text = args.getString("detail_dob")
            binding.tvDetailLanguage.text = args.getString("detail_language")
            binding.tvProfilePhone.text = args.getString("profile_phone")
            binding.tvNumberOfVisits.text = args.getString("number_of_visits")
            binding.tvLastLogin.text = args.getString("last_login")
            binding.tvLevel.text = args.getString("user_level")
        }

        binding.btnClose.setOnClickListener {
            activity?.supportFragmentManager?.popBackStack()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(name: String, email: String, dob: String, language: String, phone: String, visits: String, lastLogin: String, username: String, memberLevel: String, imageUrl: String?) = MemberDetailFragment().apply {
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
