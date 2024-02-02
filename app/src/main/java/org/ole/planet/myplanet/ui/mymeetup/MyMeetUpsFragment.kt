package org.ole.planet.myplanet.ui.mymeetup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.databinding.FragmentMyMeetUpsBinding

class MyMeetUpsFragment : Fragment() {
    private lateinit var fragmentMyMeetUpsBinding: FragmentMyMeetUpsBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentMyMeetUpsBinding = FragmentMyMeetUpsBinding.inflate(inflater, container, false)
        return fragmentMyMeetUpsBinding.root
    }
}
