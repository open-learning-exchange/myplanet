package org.ole.planet.myplanet.ui.submission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.databinding.FragmentSubmissionDetailBinding

class SubmissionDetailFragment : Fragment() {
    private lateinit var fragmentSubmissionDetailBinding: FragmentSubmissionDetailBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentSubmissionDetailBinding = FragmentSubmissionDetailBinding.inflate(inflater, container, false)
        return fragmentSubmissionDetailBinding.root
    }
}
