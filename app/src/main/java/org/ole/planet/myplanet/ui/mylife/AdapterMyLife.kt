package org.ole.planet.myplanet.ui.mylife

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
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.repository.MyLifeRepository
import org.ole.planet.myplanet.ui.calendar.CalendarFragment
import org.ole.planet.myplanet.ui.helpwanted.HelpWantedFragment
import org.ole.planet.myplanet.ui.myPersonals.MyPersonalsFragment
import org.ole.planet.myplanet.ui.myhealth.MyHealthFragment
import org.ole.planet.myplanet.ui.mylife.helper.ItemTouchHelperAdapter
import org.ole.planet.myplanet.ui.mylife.helper.ItemTouchHelperViewHolder
import org.ole.planet.myplanet.ui.mylife.helper.OnStartDragListener
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.news.NewsFragment
import org.ole.planet.myplanet.ui.references.ReferenceFragment
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment.Companion.newInstance
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment
import org.ole.planet.myplanet.utilities.Utilities

class AdapterMyLife(
    private val context: Context,
    private val myLifeList: MutableList<RealmMyLife>,
    private val userId: String?,
    private val myLifeRepository: MyLifeRepository,
    private val mDragStartListener: OnStartDragListener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), ItemTouchHelperAdapter {
    private val hide = 0.5f
    private val show = 1f

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.row_life, parent, false)
        return ViewHolderMyLife(v)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyLife) {
            holder.title.text = myLifeList[position].title
            holder.imageView.setImageResource(context.resources.getIdentifier(myLifeList[position].imageId, "drawable", context.packageName))
            holder.imageView.contentDescription = context.getString(R.string.icon, myLifeList[position].title)
            val fragment = findFragment(myLifeList[position].imageId)
            if (fragment != null) {
                holder.imageView.setOnClickListener { view: View ->
                    transactionFragment(fragment, view)
                }
            }
            holder.dragImageButton.setOnTouchListener { _: View?, event: MotionEvent ->
                holder.dragImageButton.contentDescription = context.getString(R.string.drag, myLifeList[position].title)
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    mDragStartListener.onStartDrag(holder)
                }
                false
            }
            holder.visibility.setOnClickListener {
                holder.visibility.contentDescription = context.getString(R.string.visibility_of, myLifeList[position].title)
                updateVisibility(holder, holder.bindingAdapterPosition, myLifeList[holder.bindingAdapterPosition].isVisible)
            }
            if (!myLifeList[position].isVisible) {
                changeVisibility(holder, R.drawable.ic_visibility, hide)
            } else {
                changeVisibility(holder, R.drawable.ic_visibility_off, show)
            }
        }
    }

    private fun updateVisibility(holder: RecyclerView.ViewHolder, position: Int, isVisible: Boolean) {
        val item = myLifeList[position]
        val newVisibility = !isVisible
        val messageSuffix = if (newVisibility) {
            context.getString(R.string.is_now_shown)
        } else {
            context.getString(R.string.is_now_hidden)
        }
        MainApplication.applicationScope.launch(Dispatchers.IO) {
            myLifeRepository.updateVisibility(newVisibility, item._id)
            withContext(Dispatchers.Main) {
                val adapterPosition = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: position
                if (adapterPosition in myLifeList.indices) {
                    myLifeList[adapterPosition].isVisible = newVisibility
                    val icon = if (newVisibility) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                    val alpha = if (newVisibility) show else hide
                    changeVisibility(holder, icon, alpha)
                }
                Utilities.toast(context, (item.title ?: "") + messageSuffix)
            }
        }
    }

    private fun changeVisibility(holder: RecyclerView.ViewHolder, imageId: Int, alpha: Float) {
        (holder as ViewHolderMyLife).visibility.setImageResource(imageId)
        holder.rvItemContainer.alpha = alpha
    }

    override fun getItemCount(): Int {
        return myLifeList.size
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition == toPosition) return false

        val movedItem = myLifeList.removeAt(fromPosition)
        myLifeList.add(toPosition, movedItem)
        notifyItemMoved(fromPosition, toPosition)

        MainApplication.applicationScope.launch(Dispatchers.IO) {
            myLifeRepository.updateWeight(toPosition + 1, movedItem._id, userId)
            reloadData()
        }
        return true
    }

    private suspend fun reloadData() {
        val currentUserId = userId ?: return
        val updatedList = myLifeRepository.getMyLifeByUserId(currentUserId)
        withContext(Dispatchers.Main) {
            myLifeList.clear()
            myLifeList.addAll(updatedList)
            notifyDataSetChanged()
        }
    }

    internal inner class ViewHolderMyLife(itemView: View) : RecyclerView.ViewHolder(itemView),
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
                val adapterPosition = viewHolder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition in myLifeList.indices) {
                    if (!myLifeList[adapterPosition].isVisible) {
                        (viewHolder as ViewHolderMyLife?)?.rvItemContainer?.alpha = hide
                    }
                }
            }
        }
    }

    companion object {
        fun findFragment(frag: String?): Fragment? {
            when (frag) {
                "ic_mypersonals" -> return MyPersonalsFragment()
                "ic_news" -> return NewsFragment()
                "ic_submissions" -> return MySubmissionFragment()
                "ic_my_survey" -> return newInstance("survey")
                "ic_myhealth" -> return MyHealthFragment()
                "ic_calendar" -> return CalendarFragment()
                "ic_help_wanted" -> return HelpWantedFragment()
                "ic_references" -> return ReferenceFragment()
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
