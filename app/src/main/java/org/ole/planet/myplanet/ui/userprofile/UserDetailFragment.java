package org.ole.planet.myplanet.ui.userprofile;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
    RealmUserModel user;
    UserProfileDbHandler db ;
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
        rvUserDetail.setLayoutManager(new GridLayoutManager(getActivity(), 2));
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        db = new UserProfileDbHandler(getActivity());
        user = mRealm.where(RealmUserModel.class).equalTo("id", userId).findFirst();
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
        if (user == null) {
            Utilities.toast(getActivity(), "User not available in our database");
            return;
        }
        List<Detail> list = getList(user, db);
        rvUserDetail.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new AdapterOtherInfo.ViewHolderOtherInfo(LayoutInflater.from(getActivity()).inflate(R.layout.item_title_desc, parent, false));
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof AdapterOtherInfo.ViewHolderOtherInfo) {
                    ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvTitle.setText(list.get(position).title);
                    ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvDescription.setText(list.get(position).description);
                }
            }
            @Override
            public int getItemCount() { return list.size(); }
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
