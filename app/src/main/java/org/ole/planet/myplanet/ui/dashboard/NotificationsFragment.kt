package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.ole.planet.myplanet.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {
    lateinit var fragmentNotificationsBinding: FragmentNotificationsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentNotificationsBinding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return fragmentNotificationsBinding.root
    }

}