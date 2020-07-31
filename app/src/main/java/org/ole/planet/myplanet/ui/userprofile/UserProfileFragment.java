package org.ole.planet.myplanet.ui.userprofile;


import android.content.Context;
import android.content.Intent;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.library.AddResourceActivity;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.UUID;

import io.realm.Realm;

import static android.app.Activity.RESULT_OK;

public class UserProfileFragment extends Fragment {

    UserProfileDbHandler handler;
    DatabaseService realmService;
    Realm mRealm;
    RecyclerView rvStat;
    Button addPicture;
    RealmUserModel model;
    File output;
    ImageView imageView;

    static final int IMAGE_TO_USE = 100;
    static String imageUrl = "";

    int type = 0;

    public UserProfileFragment() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null)
            mRealm.close();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_user_profile, container, false);
        handler = new UserProfileDbHandler(getActivity());
        realmService = new DatabaseService(getActivity());
        mRealm = realmService.getRealmInstance();
        rvStat = v.findViewById(R.id.rv_stat);
        rvStat.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvStat.setNestedScrollingEnabled(false);
        addPicture = (Button) v.findViewById(R.id.bt_profile_pic);
        imageView = (ImageView) v.findViewById(R.id.image);

        addPicture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                searchForPhoto();
            }
        });
        populateUserData(v);
        return v;
    }

    public void searchForPhoto() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(intent, IMAGE_TO_USE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMAGE_TO_USE && resultCode == RESULT_OK) {
            Uri url = data.getData();
            imageUrl = url.toString();

            if (!mRealm.isInTransaction()) {
                mRealm.beginTransaction();
            }
            String path = FileUtils.getRealPathFromURI(requireActivity(), url);
            if (TextUtils.isEmpty(path)) {
                path = FileUtils.getImagePath(requireActivity(), url);
            }
            model.setUserImage(path);
            mRealm.commitTransaction();
            imageView.setImageURI(url);
            Utilities.log("Image Url = " + imageUrl);
        }
             /*   getRealm().executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                model.setUserImage(imageUrl);
            }
        });*/
    }

    private void populateUserData(View v) {
        model = handler.getUserModel();
        ((TextView) v.findViewById(R.id.txt_name)).setText(String.format("%s %s %s", model.getFirstName(), model.getMiddleName(), model.getLastName()));
        ((TextView) v.findViewById(R.id.txt_email)).setText("Email : " + Utilities.checkNA(model.getEmail()));
        String dob = TextUtils.isEmpty(model.getDob()) ? "N/A" : TimeUtils.getFormatedDate(model.getDob(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        ((TextView) v.findViewById(R.id.txt_dob)).setText("Date of birth : " +  dob);
        if (!TextUtils.isEmpty(model.getUserImage()))
            Picasso.get().load(model.getUserImage()).placeholder(R.drawable.profile).into(imageView, new Callback() {
                @Override
                public void onSuccess() { }

                @Override
                public void onError(Exception e) {
                    Picasso.get().load(new File(model.getUserImage())).placeholder(R.drawable.profile).error(R.drawable.profile).into(imageView);
                }
            });
        else {
            imageView.setImageResource(R.drawable.profile);
        }
        final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        map.put("Community Name", Utilities.checkNA(model.getPlanetCode()));
        map.put("Last Login : ", Utilities.getRelativeTime(handler.getLastVisit()));
        map.put("Total Visits : ", handler.getOfflineVisits() + "");
        map.put("Most Opened Resource : ", Utilities.checkNA(handler.getMaxOpenedResource()));
        map.put("Number of Resources Opened : ", Utilities.checkNA(handler.getNumberOfResourceOpen()));
        setUpRecyclerView(map, v);
    }

    public void setUpRecyclerView(final HashMap<String, String> map, View v) {
        final LinkedList<String> keys = new LinkedList<>(map.keySet());
        rvStat.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.row_stat, parent, false);
                return new AdapterOtherInfo.ViewHolderOtherInfo(v);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof AdapterOtherInfo.ViewHolderOtherInfo) {
                    ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvTitle.setText(keys.get(position));
                    ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvTitle.setVisibility(View.VISIBLE);
                    ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvDescription.setText(map.get(keys.get(position)));
                    if (position % 2 == 0) {
                        holder.itemView.setBackgroundColor(getResources().getColor(R.color.bg_white));
                        holder.itemView.setBackgroundColor(getResources().getColor(R.color.md_grey_300));
                    }
                }
            }

            @Override
            public int getItemCount() {
                return keys.size();
            }
        });
    }

}
