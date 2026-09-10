package org.ole.planet.myplanet.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

abstract class BaseBindingBottomSheetFragment<VB : ViewBinding>(
    inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> VB
) : BottomSheetDialogFragment() {
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
