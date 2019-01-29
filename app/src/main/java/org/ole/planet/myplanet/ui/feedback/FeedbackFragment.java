package org.ole.planet.myplanet.ui.feedback;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.model.RealmMessage;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;

/**
 * A simple {@link Fragment} subclass.
 */
public class FeedbackFragment extends DialogFragment implements View.OnClickListener {


    EditText etMessage;
    RadioGroup rgType, rgUrgent;
    TextInputLayout tlMessage, tlUrgent, tlType;
    Realm mRealm;
    DatabaseService databaseService;
    RealmUserModel model;
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
        tlUrgent = v.findViewById(R.id.tl_urgent);
        tlType = v.findViewById(R.id.tl_type);
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
        clearError();
        final String message = etMessage.getText().toString();
        if (message.isEmpty()) {
            tlMessage.setError("Please enter feedback.");
            return;
        }
        RadioButton rbUrgent = getView().findViewById(rgUrgent.getCheckedRadioButtonId());
        RadioButton rbType = getView().findViewById(rgType.getCheckedRadioButtonId());
        if (rbUrgent == null) {
            tlUrgent.setError("Feedback priority is required.");
            return;
        }
        if (rbType == null) {
            tlType.setError("Feedback type is required.");
            return;
        }
        final String urgent = rbUrgent.getText().toString();
        final String type = rbType.getText().toString();
        mRealm.executeTransactionAsync(realm -> saveData(realm, urgent, type, message), () -> Utilities.toast(getActivity(), "Feedback Saved.."));
        Toast.makeText(getActivity(), "Thank you, your feedback has been submitted", Toast.LENGTH_SHORT).show();
    }

    private void clearError() {
        tlUrgent.setError("");
        tlType.setError("");
        tlMessage.setError("");
    }


    private void saveData(Realm realm, String urgent, String type, String message) {
        RealmFeedback feedback = realm.createObject(RealmFeedback.class, UUID.randomUUID().toString());
        RealmMessage msg = realm.createObject(RealmMessage.class, UUID.randomUUID().toString());
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
        RealmList<RealmMessage> msgArray = new RealmList<>();
        msgArray.add(msg);
        feedback.setMessages(msgArray);
        dismiss();
    }
}
