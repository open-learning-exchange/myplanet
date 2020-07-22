package org.ole.planet.myplanet.ui.library;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmRemovedLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.Realm;


public class LibraryDetailFragment extends BaseContainerFragment implements OnRatingChangeListener {
    TextView author, pubishedBy, title, media, subjects, license, language, resource, type;
    AppCompatImageButton download, remove;
    String libraryId;
    DatabaseService dbService;
    Realm mRealm;
    RealmMyLibrary library;
    RealmUserModel userModel;
    String openFrom = "";

    public LibraryDetailFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            libraryId = getArguments().getString("libraryId");
            if (getArguments().containsKey("openFrom"))
                openFrom = getArguments().getString("openFrom");
        }
    }

    @Override
    public void onDownloadComplete() {
        download.setImageResource(R.drawable.ic_play);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_library_detail, container, false);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        userModel = new UserProfileDbHandler(getActivity()).getUserModel();
        library = mRealm.where(RealmMyLibrary.class).equalTo("resourceId", libraryId).findFirst();
        initView(v);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initRatingView("resource", library.getResource_id(), library.getTitle(), LibraryDetailFragment.this);
        setLibraryData();
    }

    private void initView(View v) {
        author = v.findViewById(R.id.tv_author);
        title = v.findViewById(R.id.tv_title);
        pubishedBy = v.findViewById(R.id.tv_published);
        media = v.findViewById(R.id.tv_media);
        subjects = v.findViewById(R.id.tv_subject);
        language = v.findViewById(R.id.tv_language);
        license = v.findViewById(R.id.tv_license);
        resource = v.findViewById(R.id.tv_resource);
        type = v.findViewById(R.id.tv_type);
        download = v.findViewById(R.id.btn_download);
        remove = v.findViewById(R.id.btn_remove);
//        LinearLayout llRating = v.findViewById(R.id.ll_rating);
//        llRating.setVisibility(Constants.showBetaFeature(Constants.KEY_RATING, getActivity()) ? View.VISIBLE : View.GONE);
//        TextView average = v.findViewById(R.id.average);
//        average.setVisibility(Constants.showBetaFeature(Constants.KEY_RATING, getActivity()) ? View.VISIBLE : View.GONE);
//        TextView tv_rating = v.findViewById(R.id.tv_rating);
//        tv_rating.setVisibility(Constants.showBetaFeature(Constants.KEY_RATING, getActivity()) ? View.VISIBLE : View.GONE);
    }

    private void setLibraryData() {
        title.setText(String.format("%s%s", openFrom.isEmpty() ? "" : openFrom + "-", library.getTitle()));
        author.setText(library.getAuthor());
        pubishedBy.setText(library.getPublisher());
        media.setText(library.getMediaType());
        subjects.setText(library.getSubjectsAsString());
        language.setText(library.getLanguage());
        license.setText(library.getLinkToLicense());
        resource.setText(RealmMyLibrary.listToString(library.getResourceFor()));
        profileDbHandler.setResourceOpenCount(library);
        try {
            onRatingChanged();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        download.setVisibility(TextUtils.isEmpty(library.getResourceLocalAddress()) ? View.GONE : View.VISIBLE);
        download.setImageResource(library.getResourceOffline() == null || library.isResourceOffline() ?R.drawable.ic_eye : R.drawable.ic_download);
//        download.setText(library.getResourceOffline() == null || library.isResourceOffline() ? "Open Resource " : "Download Resource");
        if (FileUtils.getFileExtension(library.getResourceLocalAddress()).equals("mp4")) {
            download.setImageResource(R.drawable.ic_play);
        }
        setClickListeners();
    }


    public void setClickListeners() {

        download.setOnClickListener(view -> {
            if (TextUtils.isEmpty(library.getResourceLocalAddress())) {
                Toast.makeText(getActivity(), "Link not available", Toast.LENGTH_LONG).show();
                return;
            }
            openResource(library);
        });
        Utilities.log("user id " + profileDbHandler.getUserModel().getId() + " " + library.getUserId().contains(profileDbHandler.getUserModel().getId()));
        boolean isAdd = !library.getUserId().contains(profileDbHandler.getUserModel().getId());
//        remove.setText(isAdd ? "Add To My Library" : "Remove from myLibrary");
        remove.setImageResource(isAdd? R.drawable.ic_add_library : R.drawable.close_x);
        remove.setOnClickListener(view -> {
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            if (isAdd) {
                library.setUserId(profileDbHandler.getUserModel().getId());
                RealmRemovedLog.onAdd(mRealm, "resources", profileDbHandler.getUserModel().getId(), libraryId);
            } else {
                library.removeUserId(profileDbHandler.getUserModel().getId());
                RealmRemovedLog.onRemove(mRealm, "resources", profileDbHandler.getUserModel().getId(), libraryId);
            }
            Utilities.toast(getActivity(), "Resource " + (isAdd ? " added to" : " removed from ") + " my library");
            setLibraryData();
        });
    }

    @Override
    public void onRatingChanged() {
        JsonObject object = RealmRating.getRatingsById(mRealm, "resource", library.getResource_id(), userModel.getId());
        setRatings(object);
    }
}
