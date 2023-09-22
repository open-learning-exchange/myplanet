package org.ole.planet.myplanet.ui.userprofile;


import static android.app.Activity.RESULT_OK;
import static org.ole.planet.myplanet.MainApplication.context;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.FragmentAchievementBinding;
import org.ole.planet.myplanet.databinding.FragmentUserProfileBinding;
import org.ole.planet.myplanet.databinding.ItemTitleDescBinding;
import org.ole.planet.myplanet.databinding.RowStatBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import io.realm.Realm;

public class UserProfileFragment extends Fragment {
    private FragmentUserProfileBinding fragmentUserProfileBinding;
    private RowStatBinding rowStatBinding;
    UserProfileDbHandler handler;
    DatabaseService realmService;
    Realm mRealm;
    RealmUserModel model;
    File output;

    static final int IMAGE_TO_USE = 100;
    static String imageUrl = "";

    int type = 0;

    public UserProfileFragment() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null) mRealm.close();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentUserProfileBinding = FragmentUserProfileBinding.inflate(inflater, container, false);
        handler = new UserProfileDbHandler(getActivity());
        realmService = new DatabaseService(getActivity());
        mRealm = realmService.getRealmInstance();
        fragmentUserProfileBinding.rvStat.setLayoutManager(new LinearLayoutManager(getActivity()));
        fragmentUserProfileBinding.rvStat.setNestedScrollingEnabled(false);

        fragmentUserProfileBinding.btProfilePic.setOnClickListener(v1 -> searchForPhoto());
        model = handler.getUserModel();
        fragmentUserProfileBinding.txtName.setText(String.format("%s %s %s", model.getFirstName(), model.getMiddleName(), model.getLastName()));
        fragmentUserProfileBinding.txtEmail.setText(getString(R.string.email_colon)
                + Utilities.checkNA(model.getEmail()));
        String dob = TextUtils.isEmpty(model.getDob()) ? "N/A" : TimeUtils.getFormatedDate(model.getDob(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        fragmentUserProfileBinding.txtDob.setText(getString(R.string.date_of_birth) + dob);
        if (!TextUtils.isEmpty(model.getUserImage())) {
            Glide.with(context)
                .load(model.getUserImage())
                .apply(new RequestOptions()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        fragmentUserProfileBinding.image.setImageResource(R.drawable.profile);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(fragmentUserProfileBinding.image);
        } else {
            fragmentUserProfileBinding.image.setImageResource(R.drawable.profile);
        }
        final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        map.put("Community Name", Utilities.checkNA(model.getPlanetCode()));
        map.put("Last Login : ", Utilities.getRelativeTime(handler.getLastVisit()));
        map.put("Total Visits : ", handler.getOfflineVisits() + "");
        map.put("Most Opened Resource : ", Utilities.checkNA(handler.getMaxOpenedResource()));
        map.put("Number of Resources Opened : ", Utilities.checkNA(handler.getNumberOfResourceOpen()));

        final LinkedList<String> keys = new LinkedList<>(map.keySet());
        fragmentUserProfileBinding.rvStat.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public ViewHolderRowStat onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                rowStatBinding = RowStatBinding.inflate(LayoutInflater.from(getActivity()), parent, false);
                return new ViewHolderRowStat(rowStatBinding);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof ViewHolderRowStat) {
                    rowStatBinding.tvTitle.setText(keys.get(position));
                    rowStatBinding.tvTitle.setVisibility(View.VISIBLE);
                    rowStatBinding.tvDescription.setText(map.get(keys.get(position)));
                    if (position % 2 == 0) {
                        rowStatBinding.getRoot().setBackgroundColor(getResources().getColor(R.color.bg_white));
                        rowStatBinding.getRoot().setBackgroundColor(getResources().getColor(R.color.md_grey_300));
                    }
                }
            }

            @Override
            public int getItemCount() {
                return keys.size();
            }

            class ViewHolderRowStat extends RecyclerView.ViewHolder {
                public RowStatBinding rowStatBinding;

                public ViewHolderRowStat(RowStatBinding rowStatBinding) {
                    super(rowStatBinding.getRoot());
                    this.rowStatBinding = rowStatBinding;
                }
            }
        });
        return fragmentUserProfileBinding.getRoot();
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
            fragmentUserProfileBinding.image.setImageURI(url);
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
        ((TextView) v.findViewById(R.id.txt_email)).setText(getString(R.string.email_colon)
                + Utilities.checkNA(model.getEmail()));
        String dob = TextUtils.isEmpty(model.getDob()) ? "N/A" : TimeUtils.getFormatedDate(model.getDob(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        ((TextView) v.findViewById(R.id.txt_dob)).setText(getString(R.string.date_of_birth) + dob);

        if (!TextUtils.isEmpty(model.getUserImage())) {
            Glide.with(context)
                    .load(model.getUserImage())
                    .apply(new RequestOptions()
                            .placeholder(R.drawable.profile)
                            .error(R.drawable.profile))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            fragmentUserProfileBinding.image.setImageResource(R.drawable.profile);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(imageView);
        } else {
            fragmentUserProfileBinding.image.setImageResource(R.drawable.profile);
        }

        final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        map.put("Community Name", Utilities.checkNA(model.getPlanetCode()));
        map.put("Last Login : ", Utilities.getRelativeTime(handler.getLastVisit()));
        map.put("Total Visits : ", handler.getOfflineVisits() + "");
        map.put("Most Opened Resource : ", Utilities.checkNA(handler.getMaxOpenedResource()));
        map.put("Number of Resources Opened : ", Utilities.checkNA(handler.getNumberOfResourceOpen()));

        final LinkedList<String> keys = new LinkedList<>(map.keySet());
        fragmentUserProfileBinding.rvStat.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public ViewHolderRowStat onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                rowStatBinding = RowStatBinding.inflate(LayoutInflater.from(getActivity()), parent, false);
                return new ViewHolderRowStat(rowStatBinding);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder instanceof ViewHolderRowStat) {
                    rowStatBinding.tvTitle.setText(keys.get(position));
                    rowStatBinding.tvTitle.setVisibility(View.VISIBLE);
                    rowStatBinding.tvDescription.setText(map.get(keys.get(position)));
                    if (position % 2 == 0) {
                        rowStatBinding.getRoot().setBackgroundColor(getResources().getColor(R.color.bg_white));
                        rowStatBinding.getRoot().setBackgroundColor(getResources().getColor(R.color.md_grey_300));
                    }
                }
            }

            @Override
            public int getItemCount() {
                return keys.size();
            }

            class ViewHolderRowStat extends RecyclerView.ViewHolder {
                public RowStatBinding rowStatBinding;

                public ViewHolderRowStat(RowStatBinding rowStatBinding) {
                    super(rowStatBinding.getRoot());
                    this.rowStatBinding = rowStatBinding;
                }
            }
        });
    }
}
