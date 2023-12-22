package org.ole.planet.myplanet.ui.rating;

import static android.content.Context.MODE_PRIVATE;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.gson.Gson;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.databinding.FragmentRatingBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

public class RatingFragment extends DialogFragment {
    private FragmentRatingBinding fragmentRatingBinding;
    DatabaseService databaseService;
    RealmUserModel model;
    String id = "", type = "", title = "";
    Realm mRealm;
    SharedPreferences settings;
    OnRatingChangeListener listener;
    RealmRating previousRating;

    public void setListener(OnRatingChangeListener listener) {
        this.listener = listener;
    }

    public static RatingFragment newInstance(String type, String id, String title) {
        RatingFragment fragment = new RatingFragment();
        Bundle b = new Bundle();
        b.putString("id", id);
        b.putString("title", title);
        b.putString("type", type);
        fragment.setArguments(b);
        return fragment;
    }

    public RatingFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth);
        if (getArguments() != null) {
            id = getArguments().getString("id");
            type = getArguments().getString("type");
            title = getArguments().getString("title");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentRatingBinding = FragmentRatingBinding.inflate(inflater, container, false);
        databaseService = new DatabaseService(getActivity());
        mRealm = databaseService.getRealmInstance();

        settings = getActivity().getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
        return fragmentRatingBinding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        model = mRealm.where(RealmUserModel.class).equalTo("id", settings.getString("userId", "")).findFirst();
        previousRating = mRealm.where(RealmRating.class).equalTo("type", type).equalTo("userId", settings.getString("userId", "")).equalTo("item", id).findFirst();
        if (previousRating != null) {
            fragmentRatingBinding.ratingBar.setRating(previousRating.rate);
            fragmentRatingBinding.etComment.setText(previousRating.comment);
        }
        fragmentRatingBinding.ratingBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            if (fromUser) {
                fragmentRatingBinding.ratingError.setVisibility(View.GONE);
            }
        });
      
        fragmentRatingBinding.btnCancel.setOnClickListener(view -> dismiss());
        fragmentRatingBinding.btnSubmit.setOnClickListener(view -> {
            if(fragmentRatingBinding.ratingBar.getRating() == 0.0){
                fragmentRatingBinding.ratingError.setVisibility(View.VISIBLE);
                fragmentRatingBinding.ratingError.setText(getString(R.string.kindly_give_a_rating));
            } else {
                saveRating();
            }
        });
    }

    private void saveRating() {
        final String comment = fragmentRatingBinding.etComment.getText().toString();
        float rating = fragmentRatingBinding.ratingBar.getRating();
        mRealm.executeTransactionAsync(realm -> {
            RealmRating ratingObject = realm.where(RealmRating.class).equalTo("type", type).equalTo("userId", settings.getString("userId", "")).equalTo("item", id).findFirst();
            if (ratingObject == null)
                ratingObject = realm.createObject(RealmRating.class, UUID.randomUUID().toString());
            model = realm.where(RealmUserModel.class).equalTo("id", settings.getString("userId", "")).findFirst();
            setData(model, ratingObject, comment, rating);
        }, () -> {
            Utilities.toast(getActivity(), "Thank you, your rating is submitted.");
            if (listener != null) listener.onRatingChanged();
            dismiss();
        });
    }

    private void setData(RealmUserModel model, RealmRating ratingObject, String comment, float rating) {
        ratingObject.isUpdated = true;
        ratingObject.comment = comment;
        ratingObject.rate = (int) rating;
        ratingObject.time = new Date().getTime();
        ratingObject.userId = model.id;
        ratingObject.createdOn = model.parentCode;
        ratingObject.parentCode = model.parentCode;
        ratingObject.planetCode = model.planetCode;
        ratingObject.user = new Gson().toJson(model.serialize());
        ratingObject.type = type;
        ratingObject.item = id;
        ratingObject.title = title;
    }
}
