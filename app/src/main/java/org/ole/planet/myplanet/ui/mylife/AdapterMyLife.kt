package org.ole.planet.myplanet.ui.mylife

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyLife.Companion.updateVisibility
import org.ole.planet.myplanet.model.RealmMyLife.Companion.updateWeight
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.calendar.CalendarFragment
import org.ole.planet.myplanet.ui.helpwanted.HelpWantedFragment
import org.ole.planet.myplanet.ui.myPersonals.MyPersonalsFragment
import org.ole.planet.myplanet.ui.myhealth.MyHealthFragment
import org.ole.planet.myplanet.ui.mylife.helper.ItemTouchHelperAdapter
import org.ole.planet.myplanet.ui.mylife.helper.ItemTouchHelperViewHolder
import org.ole.planet.myplanet.ui.mylife.helper.OnStartDragListener
import org.ole.planet.myplanet.ui.news.NewsFragment
import org.ole.planet.myplanet.ui.references.ReferenceFragment
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment.Companion.newInstance
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment
import org.ole.planet.myplanet.utilities.Utilities

class AdapterMyLife(private val context: Context, private val myLifeList: List<RealmMyLife>, private var mRealm: Realm, private val mDragStartListener: OnStartDragListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), ItemTouchHelperAdapter {
    private val HIDE = 0.5f
    private val SHOW = 1f
    private val user: RealmUserModel? = UserProfileDbHandler(context).userModel

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
            val fragment = find_fragment(myLifeList[position].imageId)
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
                changeVisibility(holder, R.drawable.ic_visibility, HIDE)
            } else {
                changeVisibility(holder, R.drawable.ic_visibility_off, SHOW)
            }
        }
    }

    fun updateVisibility(holder: RecyclerView.ViewHolder, position: Int, isVisible: Boolean) {
        mRealm.executeTransactionAsync({ realm: Realm? ->
            realm?.let {
                updateVisibility(!isVisible, myLifeList[position]._id,
                    it, myLifeList[position].userId)
            }
        }, {
                Handler(Looper.getMainLooper()).post {
                    if (isVisible) {
                        changeVisibility(holder, R.drawable.ic_visibility, HIDE)
                        Utilities.toast(context, myLifeList[position].title + context.getString(R.string.is_now_hidden))
                } else {
                    changeVisibility(holder, R.drawable.ic_visibility_off, SHOW)
                    Utilities.toast(context, myLifeList[position].title + context.getString(R.string.is_now_shown))
                }
            } }) { }
    }

    private fun changeVisibility(holder: RecyclerView.ViewHolder, imageId: Int, alpha: Float) {
        (holder as ViewHolderMyLife).visibility.setImageResource(imageId)
        holder.rv_item_container.alpha = alpha
    }

    fun setmRealm(mRealm: Realm) {
        this.mRealm = mRealm
    }

    override fun getItemCount(): Int {
        return myLifeList.size
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        updateWeight(toPosition + 1, myLifeList[fromPosition]._id, mRealm, myLifeList[fromPosition].userId)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    internal inner class ViewHolderMyLife(itemView: View) : RecyclerView.ViewHolder(itemView),
        ItemTouchHelperViewHolder {
        var title: TextView
        var imageView: ImageView
        private var editImageButton: ImageButton
        var dragImageButton: ImageButton
        var visibility: ImageButton
        var rv_item_container: LinearLayout

        init {
            title = itemView.findViewById(R.id.titleTextView)
            imageView = itemView.findViewById(R.id.itemImageView)
            dragImageButton = itemView.findViewById(R.id.drag_image_button)
            editImageButton = itemView.findViewById(R.id.edit_image_button)
            visibility = itemView.findViewById(R.id.visibility_image_button)
            rv_item_container = itemView.findViewById(R.id.rv_item_parent_layout)
        }

        override fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        override fun onItemClear(viewHolder: RecyclerView.ViewHolder?) {
            itemView.setBackgroundColor(0)
            if (viewHolder != null) {
                if (!myLifeList[viewHolder.bindingAdapterPosition].isVisible) {
                    (viewHolder as ViewHolderMyLife?)?.rv_item_container?.alpha = HIDE
                }
            }
        }
    }

    companion object {
        fun find_fragment(frag: String?): Fragment? {
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
            val activity = view.context as AppCompatActivity
            f?.let {
                activity.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, it)
                    .addToBackStack(null).commit()
            }
        }
    }
}
