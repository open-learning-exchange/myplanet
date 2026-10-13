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
    private val bindingHolder = FragmentBindingHolder(inflateBinding)
    protected val binding: VB get() = bindingHolder.value
    protected val _binding: VB? get() = bindingHolder.bindingOrNull

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return bindingHolder.inflate(inflater, container)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingHolder.clear()
    }
}
