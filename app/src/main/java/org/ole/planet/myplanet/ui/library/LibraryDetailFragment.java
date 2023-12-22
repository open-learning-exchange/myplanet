package org.ole.planet.myplanet.ui.library;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.databinding.FragmentLibraryDetailBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmRemovedLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.Realm;

public class LibraryDetailFragment extends BaseContainerFragment implements OnRatingChangeListener {
    private FragmentLibraryDetailBinding fragmentLibraryDetailBinding;
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
        fragmentLibraryDetailBinding.btnDownload.setImageResource(R.drawable.ic_play);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        fragmentLibraryDetailBinding = FragmentLibraryDetailBinding.inflate(inflater, container, false);
        View v = inflater.inflate(R.layout.fragment_library_detail, container, false);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        userModel = new UserProfileDbHandler(getActivity()).getUserModel();
        library = mRealm.where(RealmMyLibrary.class).equalTo("resourceId", libraryId).findFirst();
        return fragmentLibraryDetailBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initRatingView("resource", library.resourceId, library.title, LibraryDetailFragment.this);
        setLibraryData();
    }

    private void setLibraryData() {
        fragmentLibraryDetailBinding.tvTitle.setText(String.format("%s%s", openFrom.isEmpty() ? "" : openFrom + "-", library.title));
        fragmentLibraryDetailBinding.tvAuthor.setText(library.author);
        fragmentLibraryDetailBinding.tvPublished.setText(library.getPublisher());
        fragmentLibraryDetailBinding.tvMedia.setText(library.mediaType);
        fragmentLibraryDetailBinding.tvSubject.setText(library.getSubjectsAsString());
        fragmentLibraryDetailBinding.tvLanguage.setText(library.language);
        fragmentLibraryDetailBinding.tvLicense.setText(library.linkToLicense);
        fragmentLibraryDetailBinding.tvResource.setText(RealmMyLibrary.listToString(library.resourceFor));
        profileDbHandler.setResourceOpenCount(library);
        try {
            onRatingChanged();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        fragmentLibraryDetailBinding.btnDownload.setVisibility(TextUtils.isEmpty(library.resourceLocalAddress) ? View.GONE : View.VISIBLE);
        fragmentLibraryDetailBinding.btnDownload.setImageResource(!library.resourceOffline || library.isResourceOffline() ? R.drawable.ic_eye : R.drawable.ic_download);
        if (FileUtils.getFileExtension(library.resourceLocalAddress).equals("mp4")) {
            fragmentLibraryDetailBinding.btnDownload.setImageResource(R.drawable.ic_play);
        }
        setClickListeners();
    }

    public void setClickListeners() {
        fragmentLibraryDetailBinding.btnDownload.setOnClickListener(view -> {
            if (TextUtils.isEmpty(library.resourceLocalAddress)) {
                Toast.makeText(getActivity(), getString(R.string.link_not_available), Toast.LENGTH_LONG).show();
                return;
            }
            openResource(library);
        });
        Utilities.log("user id " + profileDbHandler.getUserModel().id + " " + library.getUserId().contains(profileDbHandler.getUserModel().id));
        boolean isAdd = !library.getUserId().contains(profileDbHandler.getUserModel().id);
        fragmentLibraryDetailBinding.btnRemove.setImageResource(isAdd ? R.drawable.ic_add_library : R.drawable.close_x);
        fragmentLibraryDetailBinding.btnRemove.setOnClickListener(view -> {
            if (!mRealm.isInTransaction()) mRealm.beginTransaction();
            if (isAdd) {
                library.setUserId(profileDbHandler.getUserModel().id);
                RealmRemovedLog.onAdd(mRealm, "resources", profileDbHandler.getUserModel().id, libraryId);
            } else {
                library.removeUserId(profileDbHandler.getUserModel().id);
                RealmRemovedLog.onRemove(mRealm, "resources", profileDbHandler.getUserModel().id, libraryId);
            }
            Utilities.toast(getActivity(), getString(R.string.resources) + (isAdd ? getString(R.string.added_to) : getString(R.string.removed_from) + getString(R.string.my_library)));
            setLibraryData();
        });
        fragmentLibraryDetailBinding.btnBack.setOnClickListener(view ->
                getActivity().onBackPressed()
        );
    }

    @Override
    public void onRatingChanged() {
        JsonObject object = RealmRating.getRatingsById(mRealm, "resource", library.resourceId, userModel.id);
        setRatings(object);
    }
}
