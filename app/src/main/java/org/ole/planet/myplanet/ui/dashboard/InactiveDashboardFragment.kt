package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.databinding.FragmentInActiveDashboardBinding
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment

class InactiveDashboardFragment : Fragment() {
    private var _binding: FragmentInActiveDashboardBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInActiveDashboardBinding.inflate(inflater, container, false)
        binding.btnFeedback.setOnClickListener {
            FeedbackFragment().show(childFragmentManager, "")
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
