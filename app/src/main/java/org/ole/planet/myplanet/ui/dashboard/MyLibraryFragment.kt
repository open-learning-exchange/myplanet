package org.ole.planet.myplanet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemLibraryHomeBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import javax.inject.Inject

@AndroidEntryPoint
class MyLibraryFragment : Fragment() {
    private val viewModel: MyLibraryViewModel by viewModels()
    private var homeItemClickListener: OnHomeItemClickListener? = null
    @Inject
    lateinit var profileDbHandler: UserProfileDbHandler

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadLibrary(profileDbHandler.userModel?.id)
        observeUiState()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect {
                renderMyLibrary(it.library)
            }
        }
    }

    private fun renderMyLibrary(dbMylibrary: List<RealmMyLibrary>) {
        val flexboxLayout = view?.findViewById<FlexboxLayout>(R.id.flexboxLayout)
        flexboxLayout?.removeAllViews()
        flexboxLayout?.flexDirection = FlexDirection.ROW
        if (dbMylibrary.isEmpty()) {
            view?.findViewById<TextView>(R.id.count_library)?.visibility = View.GONE
        } else {
            view?.findViewById<TextView>(R.id.count_library)?.text =
                getString(R.string.number_placeholder, dbMylibrary.size)
        }
        for ((itemCnt, items) in dbMylibrary.withIndex()) {
            val itemLibraryHomeBinding =
                ItemLibraryHomeBinding.inflate(LayoutInflater.from(activity))
            val v = itemLibraryHomeBinding.root
            val colorResId =
                if (itemCnt % 2 == 0) R.color.card_bg else R.color.dashboard_item_alternative
            val color = context?.let { ContextCompat.getColor(it, colorResId) }
            if (color != null) {
                v.setBackgroundColor(color)
            }

            itemLibraryHomeBinding.title.text = items.title
            itemLibraryHomeBinding.detail.setOnClickListener {
                if (homeItemClickListener != null) {
                    homeItemClickListener?.openLibraryDetailFragment(items)
                }
            }
            itemLibraryHomeBinding.title.setOnClickListener {
                homeItemClickListener?.openResource(items)
            }
            val width = resources.getDimensionPixelSize(R.dimen.library_item_width)
            val height = resources.getDimensionPixelSize(R.dimen.library_item_height)
            val params = LinearLayout.LayoutParams(width, height)
            flexboxLayout?.addView(v, params)
            setupItemViewStyle(itemLibraryHomeBinding.title, itemCnt)
        }
    }

    private fun setupItemViewStyle(textView: TextView, itemCnt: Int) {
        textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
        setBackgroundColor(textView, itemCnt)
    }

    private fun setBackgroundColor(v: View, count: Int) {
        if (count % 2 == 0) {
            v.setBackgroundResource(R.drawable.light_rect)
        } else {
            v.setBackgroundResource(R.color.dashboard_item_alternative)
        }
    }

    fun setHomeItemClickListener(homeItemClickListener: OnHomeItemClickListener) {
        this.homeItemClickListener = homeItemClickListener
    }
}
