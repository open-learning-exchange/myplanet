package org.ole.planet.myplanet.ui.userprofile;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;

public class UserDetailFragment extends Fragment {

    RecyclerView rvUserDetail;
    String userId;
    Realm mRealm;

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_user_detail, container, false);
        rvUserDetail = v.findViewById(R.id.rv_user_detail);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        return v;
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
        RealmUserModel user = mRealm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
        if (user == null) {
            Utilities.toast(getActivity(), "User not available in our database");
            return;
        }
        UserProfileDbHandler db = new UserProfileDbHandler(getActivity());

        rvUserDetail.setLayoutManager(new GridLayoutManager(getActivity(), 2));
        List<Detail> list = getList(user, db);
        list.add(new Detail("Last Login", Utilities.getRelativeTime(db.getLastVisit()) + ""));
        rvUserDetail.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(getActivity()).inflate(R.layout.item_title_desc, parent, false);
                return new AdapterOtherInfo.ViewHolderOtherInfo(v);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof AdapterOtherInfo.ViewHolderOtherInfo) {
                    ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvTitle.setText(list.get(position).title);
                    ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvDescription.setText(list.get(position).description);
                }
            }

            @Override
            public int getItemCount() {
                return list.size();
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
        return list;
    }
}
