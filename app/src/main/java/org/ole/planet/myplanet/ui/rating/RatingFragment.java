package org.ole.planet.myplanet.ui.rating;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatRatingBar;
import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.google.gson.Gson;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

import static android.content.Context.MODE_PRIVATE;

public class RatingFragment extends DialogFragment {

    DatabaseService databaseService;
    RealmUserModel model;
    String id = "", type = "", title = "";
    Realm mRealm;
    Button submit, cancel;
    AppCompatRatingBar ratingBar;
    EditText etComment;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_rating, container, false);
        submit = v.findViewById(R.id.btn_submit);
        cancel = v.findViewById(R.id.btn_cancel);
        etComment = v.findViewById(R.id.et_comment);
        ratingBar = v.findViewById(R.id.rating_bar);
        databaseService = new DatabaseService(getActivity());
        mRealm = databaseService.getRealmInstance();

        settings = getActivity().getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        model = mRealm.where(RealmUserModel.class).equalTo("id", settings.getString("userId", ""))
                .findFirst();
        previousRating =   mRealm.where(RealmRating.class).equalTo("type", type).equalTo("userId", settings.getString("userId", "")).equalTo("item", id).findFirst();
        if (previousRating!=null){
            ratingBar.setRating(previousRating.getRate());
            etComment.setText(previousRating.getComment());
        }
        cancel.setOnClickListener(view -> dismiss());
        submit.setOnClickListener(view -> saveRating());
    }

    private void saveRating() {
        final String comment = etComment.getText().toString();
        float rating = ratingBar.getRating();
        mRealm.executeTransactionAsync(realm -> {
            RealmRating ratingObject = realm.where(RealmRating.class).equalTo("type", type).equalTo("userId", settings.getString("userId", "")).equalTo("item", id).findFirst();
            if (ratingObject == null)
                ratingObject = realm.createObject(RealmRating.class, UUID.randomUUID().toString());
            model = realm.where(RealmUserModel.class).equalTo("id", settings.getString("userId", ""))
                    .findFirst();
            setData(model, ratingObject, comment, rating);
        }, () -> {
            Utilities.toast(getActivity(), "Thank you, your rating is submitted.");
            if (listener!=null)
                listener.onRatingChanged();
            dismiss();
        });
    }

    private void setData(RealmUserModel model, RealmRating ratingObject, String comment, float rating) {
        ratingObject.setUpdated(true);
        ratingObject.setComment(comment);
        ratingObject.setRate((int) rating);
        ratingObject.setTime(new Date().getTime());
        ratingObject.setUserId(model.getId());
        ratingObject.setCreatedOn(model.getParentCode());
        ratingObject.setParentCode(model.getParentCode());
        ratingObject.setPlanetCode(model.getPlanetCode());
        ratingObject.setUser(new Gson().toJson(model.serialize()));
        ratingObject.setType(type);
        ratingObject.setItem(id);
        ratingObject.setTitle(title);
    }
}
