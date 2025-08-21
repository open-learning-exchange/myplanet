package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.databinding.FragmentInActiveDashboardBinding
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment

class InactiveDashboardFragment : Fragment() {
    private lateinit var binding: FragmentInActiveDashboardBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentInActiveDashboardBinding.inflate(inflater, container, false)
        binding.btnFeedback.setOnClickListener {
            FeedbackFragment().show(childFragmentManager, "")
        }
        return binding.root
    }
}
