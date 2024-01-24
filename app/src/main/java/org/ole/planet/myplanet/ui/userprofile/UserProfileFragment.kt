package org.ole.planet.myplanet.ui.userprofile

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentUserProfileBinding
import org.ole.planet.myplanet.databinding.RowStatBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.util.LinkedList

class UserProfileFragment : Fragment() {
    private lateinit var fragmentUserProfileBinding: FragmentUserProfileBinding
    private lateinit var rowStatBinding: RowStatBinding
    private lateinit var handler: UserProfileDbHandler
    private lateinit var realmService: DatabaseService
    private lateinit var mRealm: Realm
    private lateinit var model: RealmUserModel
    private var imageUrl = ""

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentUserProfileBinding = FragmentUserProfileBinding.inflate(inflater, container, false)
        handler = UserProfileDbHandler(activity)
        realmService = DatabaseService(requireContext())
        mRealm = realmService.realmInstance
        fragmentUserProfileBinding.rvStat.layoutManager = LinearLayoutManager(activity)
        fragmentUserProfileBinding.rvStat.isNestedScrollingEnabled = false

        fragmentUserProfileBinding.btProfilePic.setOnClickListener { searchForPhoto() }
        model = handler.userModel
        fragmentUserProfileBinding.txtName.text = String.format("%s %s %s", model.firstName, model.middleName, model.lastName)
        fragmentUserProfileBinding.txtEmail.text = getString(R.string.email_colon) + Utilities.checkNA(model.email)
        val dob = if (TextUtils.isEmpty(model.dob)) "N/A" else TimeUtils.getFormatedDate(model.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        fragmentUserProfileBinding.txtDob.text = getString(R.string.date_of_birth) + dob

        model.userImage.let {
            Glide.with(requireContext())
                .load(it)
                .apply(RequestOptions().placeholder(R.drawable.profile).error(R.drawable.profile))
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        fragmentUserProfileBinding.image.setImageResource(R.drawable.profile)
                        return false
                    }

                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        return false
                    }

                })
                .into(fragmentUserProfileBinding.image)
        }

        val map = linkedMapOf(
            "Community Name" to Utilities.checkNA(model.planetCode),
            "Last Login : " to Utilities.getRelativeTime(handler.lastVisit),
            "Total Visits : " to handler.offlineVisits.toString(),
            "Most Opened Resource : " to Utilities.checkNA(handler.maxOpenedResource),
            "Number of Resources Opened : " to Utilities.checkNA(handler.numberOfResourceOpen)
        )

        val keys = LinkedList(map.keys)
        fragmentUserProfileBinding.rvStat.adapter = object : RecyclerView.Adapter<ViewHolderRowStat>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderRowStat {
                rowStatBinding = RowStatBinding.inflate(LayoutInflater.from(activity), parent, false)
                return ViewHolderRowStat(rowStatBinding)
            }

            override fun onBindViewHolder(holder: ViewHolderRowStat, position: Int) {
                rowStatBinding.tvTitle.text = keys[position]
                rowStatBinding.tvTitle.visibility = View.VISIBLE
                rowStatBinding.tvDescription.text = map[keys[position]]
                if (position % 2 == 0) {
                    rowStatBinding.root.setBackgroundColor(resources.getColor(R.color.bg_white))
                    rowStatBinding.root.setBackgroundColor(resources.getColor(R.color.md_grey_300))
                }
            }

            override fun getItemCount(): Int {
                return keys.size
            }
        }

        return fragmentUserProfileBinding.root
    }

    private fun searchForPhoto() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
        startActivityForResult(intent, IMAGE_TO_USE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_TO_USE && resultCode == RESULT_OK) {
            val url = data?.data
            imageUrl = url.toString()

            mRealm.let {
                if (!it.isInTransaction) {
                    it.beginTransaction()
                }
                val path = FileUtils.getRealPathFromURI(requireActivity(), url)
                model?.userImage = path
                it.commitTransaction()
            }
            fragmentUserProfileBinding.image.setImageURI(url)
            Utilities.log("Image Url = $imageUrl")
        }
    }

    companion object {
        const val IMAGE_TO_USE = 100
    }

    inner class ViewHolderRowStat(rowStatBinding: RowStatBinding) : RecyclerView.ViewHolder(rowStatBinding.root)
}
