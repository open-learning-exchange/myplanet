package org.ole.planet.myplanet.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.base.BaseBindingFragment
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.callback.OnChangedListener
import org.ole.planet.myplanet.databinding.FragmentFeedbackListBinding
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class FeedbackListFragment : BaseBindingFragment<FragmentFeedbackListBinding>(FragmentFeedbackListBinding::inflate), OnChangedListener {

    private val viewModel: FeedbackListViewModel by viewModels()

    private lateinit var feedbackAdapter: FeedbackAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        binding.fab.setOnClickListener {
            val feedbackFragment = FeedbackFragment()
            feedbackFragment.setOnFeedbackSubmittedListener(this)
            if (!childFragmentManager.isStateSaved) {
                feedbackFragment.show(childFragmentManager, "")
            }
        }
        return view
    }

    private fun refreshFeedbackListData() {
        viewModel.refreshFeedback()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        feedbackAdapter = FeedbackAdapter()
        binding.rvFeedback.layoutManager = LinearLayoutManager(activity)
        binding.rvFeedback.adapter = feedbackAdapter
        observeFeedbackList()
    }

    private fun observeFeedbackList() {
        collectWhenStarted(viewModel.feedbackList) { feedbackList ->
            updatedFeedbackList(feedbackList)
        }
    }

    override fun onChanged() {
        refreshFeedbackListData()
    }

    private fun updatedFeedbackList(updatedList: List<Feedback>?) {
        if (_binding == null) return
        feedbackAdapter.submitList(updatedList) {
            binding.rvFeedback.scrollToPosition(0)
        }
        val itemCount = updatedList?.size ?: 0
        showNoData(binding.tvMessage, itemCount, "feedback")
        updateTextViewsVisibility(itemCount)
    }

    private fun updateTextViewsVisibility(itemCount: Int) {
        val visibility = if (itemCount == 0) View.GONE else View.VISIBLE
        binding.tvTitle.visibility = visibility
        binding.tvType.visibility = visibility
        binding.tvPriority.visibility = visibility
        binding.tvStatus.visibility = visibility
        binding.tvOpenDate.visibility = visibility
    }
}
