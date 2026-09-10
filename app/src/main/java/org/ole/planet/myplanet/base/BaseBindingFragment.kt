package org.ole.planet.myplanet.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseBindingFragment<VB : ViewBinding>(
    inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> VB
) : Fragment() {
    private val bindingDelegate = FragmentViewBindingDelegate(inflateBinding)
    protected val binding: VB get() = bindingDelegate.value
    protected val _binding: VB? get() = bindingDelegate.bindingOrNull

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return bindingDelegate.inflate(inflater, container)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingDelegate.clear()
    }
}
