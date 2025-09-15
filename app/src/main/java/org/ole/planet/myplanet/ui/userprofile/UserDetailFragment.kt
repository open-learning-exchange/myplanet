package org.ole.planet.myplanet.ui.userprofile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentUserDetailBinding
import org.ole.planet.myplanet.databinding.ItemTitleDescBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate
import org.ole.planet.myplanet.utilities.TimeUtils.getRelativeTime
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class UserDetailFragment : Fragment() {
    private var _binding: FragmentUserDetailBinding? = null
    private val binding get() = _binding!!
    private var userId: String? = null
    private var user: RealmUserModel? = null
    @Inject
    lateinit var userRepository: UserRepository
    private lateinit var db: UserProfileDbHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString("id")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserDetailBinding.inflate(inflater, container, false)
        binding.rvUserDetail.layoutManager = GridLayoutManager(activity, 2)
        db = UserProfileDbHandler(requireActivity())
        return binding.root
    }

    data class Detail(val title: String, val description: String)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            user = userId?.let { userRepository.getUserById(it) }
            user?.let { user ->
                val list = getList(user, db)
                binding.rvUserDetail.adapter = object : RecyclerView.Adapter<ViewHolderUserDetail>() {
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderUserDetail {
                        val binding = ItemTitleDescBinding.inflate(LayoutInflater.from(activity), parent, false)
                        return ViewHolderUserDetail(binding)
                    }

                    override fun onBindViewHolder(holder: ViewHolderUserDetail, position: Int) {
                        val detail = list[position]
                        holder.binding.tvTitle.text = detail.title
                        holder.binding.tvDescription.text = detail.description
                    }

                    override fun getItemCount() = list.size
                }
            } ?: run {
                Utilities.toast(activity, getString(R.string.user_not_available_in_our_database))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getList(user: RealmUserModel, db: UserProfileDbHandler?): List<Detail> {
        val list: MutableList<Detail> = ArrayList()
        list.add(Detail("Full Name", user.getFullName()))
        list.add(Detail("DOB", getFormattedDate(user.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")))
        list.add(Detail("Email", user.email!!))
        list.add(Detail("Phone", user.phoneNumber!!))
        list.add(Detail("Language", user.language!!))
        list.add(Detail("Level", user.level!!))
        list.add(Detail("Number of Visits", db!!.offlineVisits.toString() + ""))
        list.add(Detail("Last Login", getRelativeTime(db.lastVisit!!)))
        return list
    }

    class ViewHolderUserDetail(val binding: ItemTitleDescBinding) : RecyclerView.ViewHolder(binding.root)
}
