package org.ole.planet.myplanet.library;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;


public class LibraryDetailFragment extends BaseContainerFragment {
    TextView author, pubishedBy, media, subjects, license, rating, language, resource, type;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_library_detail, container, false);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        initView(v);
        return v;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        library = mRealm.where(realm_myLibrary.class).equalTo("id", libraryId).findFirst();
        setLibraryData();
    }

    private void initView(View v) {
        author = v.findViewById(R.id.tv_author);
        pubishedBy = v.findViewById(R.id.tv_published);
        media = v.findViewById(R.id.tv_media);
        subjects = v.findViewById(R.id.tv_subject);
        language = v.findViewById(R.id.tv_language);
        license = v.findViewById(R.id.tv_license);
        rating = v.findViewById(R.id.tv_rating);
        resource = v.findViewById(R.id.tv_resource);
        type = v.findViewById(R.id.tv_type);
        download = v.findViewById(R.id.btn_download);
        remove = v.findViewById(R.id.btn_remove);
    }

    private void setLibraryData() {
        author.setText(library.getAuthor());
        pubishedBy.setText(library.getPublisher());
        media.setText(library.getMediaType());
        subjects.setText(library.getSubjectsAsString());
        language.setText(library.getLanguage());
        license.setText(library.getLinkToLicense());
        rating.setText(library.getAverageRating());
        resource.setText(realm_myLibrary.listToString(library.getResourceFor()));
        // type.setText(library.getMediaType());

        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ArrayList<String> arrayList = new ArrayList<>();
                arrayList.add(Utilities.getUrl(library, settings));
                startDownload(arrayList);
            }
        });
    }

    @Override
    public void playVideo(String videoType, realm_myLibrary items) {

    }
}
