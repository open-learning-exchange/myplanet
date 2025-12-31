package org.ole.planet.myplanet.ui.community

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

class HomeCommunityDialogFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentTeamDetailBinding? = null
    private val binding get() = _binding!!
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
            val parent = view.parent as View
            bottomSheetBehavior = BottomSheetBehavior.from(parent)

            bottomSheetBehavior?.isFitToContents = false
            bottomSheetBehavior?.peekHeight = resources.displayMetrics.heightPixels / 7

            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
            bottomSheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                dismiss()
                            }
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val screenHeight = resources.displayMetrics.heightPixels
                    val newHeight = (screenHeight * (0.25f + (0.75f * slideOffset))).toInt()

                    bottomSheet.layoutParams.height = newHeight
                    bottomSheet.requestLayout()

                    when {
                        slideOffset > 0.5f -> bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                        slideOffset > 0.2f -> bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                        slideOffset < -0.3f -> bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                    }
                }
            })
        }

        initCommunityTab()
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { d ->
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                it.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                it.requestLayout()
            }
        }
    }

    private fun initCommunityTab() {
        binding.llActionButtons.visibility = View.GONE
        val settings = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val sParentcode = settings.getString("parentCode", "")
        val communityName = settings.getString("communityName", "")
        binding.viewPager2.adapter = CommunityTabsAdapter(requireActivity(), "$communityName@$sParentcode", true, settings)
        TabLayoutMediator(binding.tabLayout, binding.viewPager2) { tab, position ->
            tab.text = (binding.viewPager2.adapter as CommunityTabsAdapter).getPageTitle(position)
        }.attach()
        binding.title.text = communityName
        binding.title.setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
        binding.subtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
        binding.subtitle.text = settings.getString("planetType", "")
        binding.appBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_bg))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
