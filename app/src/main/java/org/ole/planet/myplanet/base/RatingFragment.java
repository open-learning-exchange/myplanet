package org.ole.planet.myplanet.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatRatingBar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;

import com.google.gson.Gson;

import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_feedback;
import org.ole.planet.myplanet.Data.realm_rating;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.SyncActivity;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

import static android.content.Context.MODE_PRIVATE;

public class RatingFragment extends DialogFragment {

    DatabaseService databaseService;
    realm_UserModel model;
    String id = "", type = "", title = "";
    Realm mRealm;
    Button submit, cancel;
    AppCompatRatingBar ratingBar;
    EditText etComment;
    SharedPreferences settings;
    OnRatingChangeListener listener;


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
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveRating();
            }
        });
    }

    private void saveRating() {
        final String comment = etComment.getText().toString();
        float rating = ratingBar.getRating();
        mRealm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                Utilities.log("User id " + settings.getString("userId", "") );
                realm_rating ratingObject = realm.where(realm_rating.class).equalTo("type", type).equalTo("userId", settings.getString("userId", "")).equalTo("item", id).findFirst();
                if (ratingObject == null)
                    ratingObject = realm.createObject(realm_rating.class, UUID.randomUUID().toString());
                model = realm.where(realm_UserModel.class).equalTo("id", settings.getString("userId", ""))
                        .findFirst();
                setData(model, ratingObject, comment, rating);
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                Utilities.toast(getActivity(), "Thank you, your rating is submitted.");
                if (listener!=null)
                    listener.onRatingChanged();
                dismiss();
            }
        });
    }

    private void setData(realm_UserModel model, realm_rating ratingObject, String comment, float rating) {
        ratingObject.setUpdated(true);
        ratingObject.setComment(comment);
        ratingObject.setRate((int) rating);
        ratingObject.setTime(new Date().getTime());
        ratingObject.setUserId(model.getId());
        ratingObject.setUser(new Gson().toJson(model.serialize()));
        ratingObject.setType(type);
        ratingObject.setItem(id);
        ratingObject.setTitle(title);
    }
}
