package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.ole.planet.myplanet.base.BaseBindingFragment
import org.ole.planet.myplanet.databinding.FragmentInActiveDashboardBinding
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment

class InactiveDashboardFragment : BaseBindingFragment<FragmentInActiveDashboardBinding>(FragmentInActiveDashboardBinding::inflate) {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        binding.btnFeedback.setOnClickListener {
            FeedbackFragment().show(childFragmentManager, "")
        }
        return view
    }
}
