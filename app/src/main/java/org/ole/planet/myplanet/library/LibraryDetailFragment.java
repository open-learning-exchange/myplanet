package org.ole.planet.myplanet.library;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatRatingBar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_rating;
import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.base.RatingFragment;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.feedback.FeedbackFragment;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;


public class LibraryDetailFragment extends BaseContainerFragment implements OnRatingChangeListener {
    TextView author, pubishedBy, title, media, subjects, license, language, resource, type;
    Button download, remove;
    String libraryId;
    DatabaseService dbService;
    Realm mRealm;
    realm_myLibrary library;

    public LibraryDetailFragment() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            libraryId = getArguments().getString("libraryId");
        }
    }

    @Override
    public void onDownloadComplete() {
        download.setText("Open Resource");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_library_detail, container, false);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        Utilities.log("Library id " + libraryId);
        initView(v);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        library = mRealm.where(realm_myLibrary.class).equalTo("resourceId", libraryId).findFirst();
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
        v.findViewById(R.id.ll_rating).setOnClickListener(view -> homeItemClickListener.showRatingDialog("resource", library.getResource_id(), library.getTitle(), LibraryDetailFragment.this));
        initRatingView(v);
    }

    private void setLibraryData() {
        title.setText(library.getTitle());
        author.setText(library.getAuthor());
        pubishedBy.setText(library.getPublisher());
        media.setText(library.getMediaType());
        subjects.setText(library.getSubjectsAsString());
        language.setText(library.getLanguage());
        license.setText(library.getLinkToLicense());
        resource.setText(realm_myLibrary.listToString(library.getResourceFor()));
        profileDbHandler.setResourceOpenCount(library);
        onRatingChanged();
        download.setVisibility(TextUtils.isEmpty(library.getResourceLocalAddress()) ? View.GONE : View.VISIBLE);
        setClickListeners();
    }


    public void setClickListeners() {
        download.setText(library.getResourceOffline() == null || library.getResourceOffline() ? "Open Resource " : "Download Resource");
        download.setOnClickListener(view -> {
            if (TextUtils.isEmpty(library.getResourceLocalAddress())) {
                Toast.makeText(getActivity(), "Link not available", Toast.LENGTH_LONG).show();
                return;
            }
            openResource(library);
        });
        boolean isAdd = !library.getUserId().contains(profileDbHandler.getUserModel().getId());
        remove.setText(isAdd ? "Add To My Library" : "Remove");
        remove.setOnClickListener(view -> {
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            if (isAdd){
                library.setUserId(profileDbHandler.getUserModel().getId());
            }else {
                library.removeUserId(profileDbHandler.getUserModel().getId());
            }
            mRealm.commitTransaction();
            Utilities.toast(getActivity(), "Resource " + (isAdd ? " added to" : " removed from ") + " my library");
            setLibraryData();
        });
    }

    @Override
    public void onRatingChanged() {
        JsonObject object = realm_rating.getRatingsById(mRealm, "resource", library.getResource_id());
        setRatings(object);
    }
}
