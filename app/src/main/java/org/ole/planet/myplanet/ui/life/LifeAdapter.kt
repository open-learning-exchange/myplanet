package org.ole.planet.myplanet.ui.life

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.ItemTouchHelperListener
import org.ole.planet.myplanet.callback.ItemTouchHelperViewHolder
import org.ole.planet.myplanet.callback.OnStartDragListener
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.ui.calendar.CalendarFragment
import org.ole.planet.myplanet.ui.health.MyHealthFragment
import org.ole.planet.myplanet.ui.personals.PersonalsFragment
import org.ole.planet.myplanet.ui.references.ReferencesFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsFragment
import org.ole.planet.myplanet.ui.submissions.SubmissionsFragment.Companion.newInstance
import org.ole.planet.myplanet.ui.user.AchievementFragment
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.NavigationHelper

class LifeAdapter(
    private val context: Context,
    private val mDragStartListener: OnStartDragListener,
    private val visibilityCallback: (RealmMyLife, Boolean) -> Unit,
    private val reorderCallback: (List<RealmMyLife>) -> Unit
) : ListAdapter<RealmMyLife, RecyclerView.ViewHolder>(DIFF_CALLBACK), ItemTouchHelperListener {
    private val hide = 0.5f
    private val show = 1f
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.row_life, parent, false)
        return LifeViewHolder(v)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val myLife = getItem(position)
        if (holder is LifeViewHolder) {
            holder.title.text = myLife.title
            holder.imageView.setImageResource(context.resources.getIdentifier(myLife.imageId, "drawable", context.packageName))
            holder.imageView.contentDescription = context.getString(R.string.icon, myLife.title)
            val fragment = findFragment(myLife.imageId)
            if (fragment != null) {
                holder.imageView.setOnClickListener { view: View ->
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
                updateVisibility(holder.bindingAdapterPosition, myLife.isVisible)
            }
            if (!myLife.isVisible) {
                changeVisibility(holder, R.drawable.ic_visibility, hide)
            } else {
                changeVisibility(holder, R.drawable.ic_visibility_off, show)
            }
        }
    }

    private fun updateVisibility(position: Int, isVisible: Boolean) {
        val myLife = getItem(position)
        visibilityCallback(myLife, !isVisible)
    }

    private fun changeVisibility(holder: RecyclerView.ViewHolder, imageId: Int, alpha: Float) {
        (holder as LifeViewHolder).visibility.setImageResource(imageId)
        holder.rvItemContainer.alpha = alpha
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        val newList = currentList.toMutableList()
        val movedItem = newList.removeAt(fromPosition)
        newList.add(toPosition, movedItem)
        reorderCallback(newList)
        submitList(newList)
        return true
    }

    internal inner class LifeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        ItemTouchHelperViewHolder {
        var title: TextView = itemView.findViewById(R.id.titleTextView)
        var imageView: ImageView = itemView.findViewById(R.id.itemImageView)
        var dragImageButton: ImageButton = itemView.findViewById(R.id.drag_image_button)
        var visibility: ImageButton = itemView.findViewById(R.id.visibility_image_button)
        var rvItemContainer: LinearLayout = itemView.findViewById(R.id.rv_item_parent_layout)

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
        private val DIFF_CALLBACK = DiffUtils.itemCallback<RealmMyLife>(
            areItemsTheSame = { oldItem, newItem -> oldItem._id == newItem._id },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
        )
        fun findFragment(frag: String?): Fragment? {
            when (frag) {
                "ic_mypersonals" -> return PersonalsFragment()
                "ic_submissions" -> return SubmissionsFragment()
                "ic_my_survey" -> return newInstance("survey")
                "ic_myhealth" -> return MyHealthFragment()
                "ic_calendar" -> return CalendarFragment()
                "ic_references" -> return ReferencesFragment()
                "my_achievement" -> return AchievementFragment()
            }
            return null
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
                    NavigationHelper.replaceFragment(
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
