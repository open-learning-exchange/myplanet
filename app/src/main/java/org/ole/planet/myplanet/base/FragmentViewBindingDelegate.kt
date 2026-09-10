package org.ole.planet.myplanet.base

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

internal class FragmentViewBindingDelegate<VB : ViewBinding>(
    private val inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> VB
) {
    private var binding: VB? = null

    val value: VB get() = binding!!
    val bindingOrNull: VB? get() = binding

    fun inflate(inflater: LayoutInflater, container: ViewGroup?): View {
        val created = inflateBinding(inflater, container, false)
        binding = created
        return created.root
    }

    fun clear() {
        binding = null
    }
}
