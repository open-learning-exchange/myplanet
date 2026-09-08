package org.ole.planet.myplanet.ui.life

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnItemDragStateListener
import org.ole.planet.myplanet.callback.OnItemMoveListener
import org.ole.planet.myplanet.callback.OnStartDragListener
import org.ole.planet.myplanet.databinding.RowLifeBinding
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.ui.calendar.CalendarFragment
import org.ole.planet.myplanet.ui.components.FragmentNavigator
import org.ole.planet.myplanet.ui.health.MyHealthFragment
import org.ole.planet.myplanet.ui.personals.PersonalsFragment
import org.ole.planet.myplanet.ui.references.ReferencesFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsFragment.Companion.newInstance
import org.ole.planet.myplanet.ui.user.AchievementFragment
import org.ole.planet.myplanet.utils.DiffUtils

class LifeAdapter(
    private val context: Context,
    private val mDragStartListener: OnStartDragListener,
    private val visibilityCallback: (MyLife, Boolean) -> Unit,
    private val reorderCallback: (List<MyLife>) -> Unit
) : ListAdapter<MyLife, RecyclerView.ViewHolder>(DIFF_CALLBACK), OnItemMoveListener {
    private val hide = 0.5f
    private val show = 1f

    private val drawableCache = mutableMapOf<String, Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowLifeBinding.inflate(LayoutInflater.from(context), parent, false)
        return LifeViewHolder(binding)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val myLife = getItem(position)
        if (holder is LifeViewHolder) {
            holder.title.text = myLife.title
            myLife.imageId?.let { imgId ->
                val resId = drawableCache.getOrPut(imgId) {
                    context.resources.getIdentifier(imgId, "drawable", context.packageName)
                }
                holder.imageView.setImageResource(resId)
            }
            holder.imageView.contentDescription = context.getString(R.string.icon, myLife.title)

            holder.imageView.setOnClickListener { view: View ->
                val fragment = findFragment(myLife.imageId)
                if (fragment != null) {
                    transactionFragment(fragment, view)
                }
            }
            holder.dragImageButton.setOnTouchListener { _: View?, event: MotionEvent ->
                holder.dragImageButton.contentDescription = context.getString(R.string.drag, myLife.title)
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    mDragStartListener.onStartDrag(holder)
                }
                false
            }
            holder.visibility.setOnClickListener {
                holder.visibility.contentDescription = context.getString(R.string.visibility_of, myLife.title)
                updateVisibility(holder)
            }
            if (!myLife.isVisible) {
                changeVisibility(holder, R.drawable.ic_visibility, hide)
            } else {
                changeVisibility(holder, R.drawable.ic_visibility_off, show)
            }
        }
    }

    private fun updateVisibility(holder: LifeViewHolder) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        val myLife = getItem(position)
        val newVisibility = !myLife.isVisible
        myLife.isVisible = newVisibility
        if (newVisibility) {
            changeVisibility(holder, R.drawable.ic_visibility_off, show)
        } else {
            changeVisibility(holder, R.drawable.ic_visibility, hide)
        }
        visibilityCallback(myLife, newVisibility)
    }

    private fun changeVisibility(holder: RecyclerView.ViewHolder, imageId: Int, alpha: Float) {
        (holder as LifeViewHolder).visibility.setImageResource(imageId)
        holder.rvItemContainer.alpha = alpha
    }

    private var dragList: MutableList<MyLife>? = null

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (dragList == null) {
            dragList = currentList.toMutableList()
        }
        val list = dragList ?: return false
        if (fromPosition == toPosition ||
            fromPosition !in list.indices ||
            toPosition !in list.indices
        ) {
            return false
        }
        val movedItem = list.removeAt(fromPosition)
        list.add(toPosition, movedItem)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveFinished() {
        val list = dragList ?: return
        val updatedList = list.mapIndexed { index, item ->
            MyLife().apply {
                _id = item._id
                imageId = item.imageId
                userId = item.userId
                title = item.title
                isVisible = item.isVisible
                weight = index
            }
        }
        dragList = null
        reorderCallback(updatedList)
        submitList(updatedList)
    }

    internal inner class LifeViewHolder(val binding: RowLifeBinding) : RecyclerView.ViewHolder(binding.root),
        OnItemDragStateListener {
        val title get() = binding.titleTextView
        val imageView get() = binding.itemImageView
        val dragImageButton get() = binding.dragImageButton
        val visibility get() = binding.visibilityImageButton
        val rvItemContainer get() = binding.rvItemParentLayout

        override fun onItemSelected() {
            itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.user_profile_background))
        }

        override fun onItemClear(viewHolder: RecyclerView.ViewHolder?) {
            itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.daynight_grey))
            if (viewHolder != null) {
                val myLife = getItem(viewHolder.bindingAdapterPosition)
                if (!myLife.isVisible) {
                    (viewHolder as LifeViewHolder?)?.rvItemContainer?.alpha = hide
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = DiffUtils.itemCallback<MyLife>(
            areItemsTheSame = { oldItem, newItem ->
                if (!oldItem._id.isNullOrBlank() && !newItem._id.isNullOrBlank()) {
                    oldItem._id == newItem._id
                } else if (!oldItem.imageId.isNullOrBlank() && !newItem.imageId.isNullOrBlank()) {
                    oldItem.imageId == newItem.imageId
                } else {
                    oldItem.title == newItem.title
                }
            },
            areContentsTheSame = { oldItem, newItem ->
                oldItem.isVisible == newItem.isVisible && oldItem.weight == newItem.weight && oldItem.title == newItem.title
            }
        )
        private val fragmentCache = mapOf(
            "ic_mypersonals" to { PersonalsFragment() },
            "ic_submissions" to { SubmissionsFragment() },
            "ic_my_survey" to { newInstance("survey") },
            "ic_myhealth" to { MyHealthFragment() },
            "ic_calendar" to { CalendarFragment() },
            "ic_references" to { ReferencesFragment() },
            "my_achievement" to { AchievementFragment() }
        )

        fun findFragment(frag: String?): Fragment? {
            return frag?.let { fragmentCache[it]?.invoke() }
        }

        fun transactionFragment(f: Fragment?, view: View) {
            var context = view.context
            while (context is ContextWrapper) {
                if (context is AppCompatActivity) {
                    break
                }
                context = context.baseContext
            }
            
            val activity = context as? AppCompatActivity
            activity?.let { act ->
                f?.let {
                    FragmentNavigator.replaceFragment(
                        act.supportFragmentManager,
                        R.id.fragment_container,
                        it,
                        addToBackStack = true
                    )
                }
            }
        }
    }
}
