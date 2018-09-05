package org.ole.planet.takeout.feedback;


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_feedback;
import org.ole.planet.takeout.Data.realm_messages;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;

/**
 * A simple {@link Fragment} subclass.
 */
public class FeedbackFragment extends DialogFragment implements View.OnClickListener {


    EditText etMessage;
    RadioGroup rgType, rgUrgent;
    TextInputLayout tlMessage;
    Realm mRealm;
    DatabaseService databaseService;
    realm_UserModel model;
    String user = "";

    public FeedbackFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_feedback, container, false);
        databaseService = new DatabaseService(getActivity());
        mRealm = databaseService.getRealmInstance();
        model = new UserProfileDbHandler(getActivity()).getUserModel();
        user = model.getName();
        etMessage = v.findViewById(R.id.et_message);
        rgUrgent = v.findViewById(R.id.rg_urgent);
        rgType = v.findViewById(R.id.rg_type);
        tlMessage = v.findViewById(R.id.tl_message);
        v.findViewById(R.id.btn_submit).setOnClickListener(this);
        v.findViewById(R.id.btn_cancel).setOnClickListener(this);
        return v;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_submit) {
            validateAndSaveData();
        } else if (view.getId() == R.id.btn_cancel) {
            dismiss();
        }
    }

    private void validateAndSaveData() {
        final String message = etMessage.getText().toString();
        if (message.isEmpty()) {
            tlMessage.setError("This field is required");
            return;
        }
        RadioButton rbUrgent = getView().findViewById(rgUrgent.getCheckedRadioButtonId());
        RadioButton rbType = getView().findViewById(rgType.getCheckedRadioButtonId());
        if (rbType == null && rbType == null) {
            Snackbar.make(getView(), "All fields are necessary", Snackbar.LENGTH_LONG).show();
            return;
        }
        final String urgent = rbUrgent.getText().toString();
        final String type = rbType.getText().toString();

        mRealm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                saveData(realm, urgent, type, message);
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                Utilities.toast(getActivity(), "Feedback Saved..");
            }
        });
    }

    private void saveData(Realm realm, String urgent, String type, String message) {
        realm_feedback feedback = realm.createObject(realm_feedback.class, UUID.randomUUID().toString());
        realm_messages msg = realm.createObject(realm_messages.class, UUID.randomUUID().toString());
        feedback.setTitle("Question regarding /");
        feedback.setOpenTime(new Date().getTime() + "");
        feedback.setUrl("/");
        feedback.setOwner(user);
        feedback.setSource(user);
        feedback.setStatus("Open");
        feedback.setPriority(urgent);
        feedback.setType(type);
        msg.setMessage(message);
        msg.setTime(new Date().getTime() + "");
        msg.setUser(user);
        RealmList<realm_messages> msgArray = new RealmList<>();
        msgArray.add(msg);
        feedback.setMessages(msgArray);
        dismiss();
    }
}
