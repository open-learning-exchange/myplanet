package org.ole.planet.myplanet.ui.userprofile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.FragmentUserDetailBinding;
import org.ole.planet.myplanet.databinding.ItemTitleDescBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;

public class UserDetailFragment extends Fragment {
    private FragmentUserDetailBinding fragmentUserDetailBinding;
    private ItemTitleDescBinding itemTitleDescBinding;
    String userId;
    Realm mRealm;
    RealmUserModel user;
    UserProfileDbHandler db;

    public UserDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userId = getArguments().getString("id");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentUserDetailBinding = FragmentUserDetailBinding.inflate(inflater, container, false);
        fragmentUserDetailBinding.rvUserDetail.setLayoutManager(new GridLayoutManager(getActivity(), 2));
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        db = new UserProfileDbHandler(getActivity());
        user = mRealm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
        return fragmentUserDetailBinding.getRoot();
    }

    class Detail {
        public String title, description;

        public Detail(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (user == null) {
            Utilities.toast(getActivity(), getString(R.string.user_not_available_in_our_database));
            return;
        }
        List<Detail> list = getList(user, db);
        fragmentUserDetailBinding.rvUserDetail.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public ViewHolderUserDetail onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                itemTitleDescBinding = ItemTitleDescBinding.inflate(LayoutInflater.from(getActivity()), parent, false);
                return new ViewHolderUserDetail(itemTitleDescBinding);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof ViewHolderUserDetail) {
                    itemTitleDescBinding.tvTitle.setText(list.get(position).title);
                    itemTitleDescBinding.tvDescription.setText(list.get(position).description);
                }
            }

            @Override
            public int getItemCount() {
                return list.size();
            }

            class ViewHolderUserDetail extends RecyclerView.ViewHolder {
                public ItemTitleDescBinding itemTitleDescBinding;

                public ViewHolderUserDetail(ItemTitleDescBinding itemTitleDescBinding) {
                    super(itemTitleDescBinding.getRoot());
                    this.itemTitleDescBinding = itemTitleDescBinding;
                }
            }
        });
    }

    private List<Detail> getList(RealmUserModel user, UserProfileDbHandler db) {
        List<Detail> list = new ArrayList<>();
        list.add(new Detail("Full Name", user.getFullName()));
        list.add(new Detail("DOB", TimeUtils.getFormatedDate(user.getDob(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")));
        list.add(new Detail("Email", user.getEmail()));
        list.add(new Detail("Phone", user.getPhoneNumber()));
        list.add(new Detail("Language", user.getLanguage()));
        list.add(new Detail("Level", user.getLevel()));
        list.add(new Detail("Number of Visits", db.getOfflineVisits() + ""));
        list.add(new Detail("Last Login", Utilities.getRelativeTime(db.getLastVisit()) + ""));
        return list;
    }
}
