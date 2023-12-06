package org.ole.planet.myplanet.ui.feedback;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.FragmentFeedbackBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

public class FeedbackFragment extends DialogFragment implements View.OnClickListener {
    private FragmentFeedbackBinding fragmentFeedbackBinding;
    Realm mRealm;
    DatabaseService databaseService;
    RealmUserModel model;
    String user = "";

    public FeedbackFragment() {
    }

    public interface OnFeedbackSubmittedListener {
        void onFeedbackSubmitted();
    }

    private OnFeedbackSubmittedListener mListener;

    public void setOnFeedbackSubmittedListener(OnFeedbackSubmittedListener listener) {
        mListener = listener;
    }
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentFeedbackBinding = FragmentFeedbackBinding.inflate(inflater, container, false);
        databaseService = new DatabaseService(getActivity());
        mRealm = databaseService.getRealmInstance();
        model = new UserProfileDbHandler(getActivity()).getUserModel();
        if (model != null) {
            user = model.getName();
        } else {
            user = "Anonymous";
        }
        fragmentFeedbackBinding.btnSubmit.setOnClickListener(this);
        fragmentFeedbackBinding.btnCancel.setOnClickListener(this);
        return fragmentFeedbackBinding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_submit) {
            clearError();
            validateAndSaveData();
        } else if (view.getId() == R.id.btn_cancel) {
            dismiss();
        }
    }

    private void validateAndSaveData() {
        final String message = fragmentFeedbackBinding.etMessage.getText().toString().trim();
        if (message.isEmpty()) {
            fragmentFeedbackBinding.tlMessage.setError(getString(R.string.please_enter_feedback));
            return;
        }
        RadioButton rbUrgent = getView().findViewById(fragmentFeedbackBinding.rgUrgent.getCheckedRadioButtonId());
        RadioButton rbType = getView().findViewById(fragmentFeedbackBinding.rgType.getCheckedRadioButtonId());
        if (rbUrgent == null) {
            fragmentFeedbackBinding.tlUrgent.setError(getString(R.string.feedback_priority_is_required));
            return;
        }
        if (rbType == null) {
            fragmentFeedbackBinding.tlType.setError(getString(R.string.feedback_type_is_required));
            return;
        }
        final String urgent = rbUrgent.getText().toString();
        final String type = rbType.getText().toString();
        Bundle arguments = getArguments();
        if (arguments != null) {
            String[] argumentArray = getArgumentArray(message);
            mRealm.executeTransactionAsync(realm -> saveData(realm, urgent, type, argumentArray), () -> Utilities.toast(getActivity(),
                    String.valueOf(R.string.feedback_saved)));
        } else
            mRealm.executeTransactionAsync(realm -> saveData(realm, urgent, type, message), () ->
                    Utilities.toast(getActivity(), String.valueOf(R.string.feedback_saved)));
        Toast.makeText(getActivity(), R.string.thank_you_your_feedback_has_been_submitted, Toast.LENGTH_SHORT).show();
        if (mListener != null) {
            mListener.onFeedbackSubmitted();
        }
    }

    public String[] getArgumentArray(String message) {
        String[] argumentArray = new String[3];
        argumentArray[0] = message;
        argumentArray[1] = getArguments().getString("item");
        argumentArray[2] = getArguments().getString("state");
        return argumentArray;
    }

    private void clearError() {
        fragmentFeedbackBinding.tlUrgent.setError("");
        fragmentFeedbackBinding.tlType.setError("");
        fragmentFeedbackBinding.tlMessage.setError("");
    }

    private void saveData(Realm realm, String urgent, String type, String message) {
        RealmFeedback feedback = realm.createObject(RealmFeedback.class, UUID.randomUUID().toString());
        feedback.title = "Question regarding /";
        feedback.openTime = new Date().getTime() + "";
        feedback.url = "/";
        feedback.owner = user;
        feedback.source = user;
        feedback.status = "Open";
        feedback.priority = urgent;
        feedback.type = type;
        feedback.parentCode = "dev";
        JsonObject object = new JsonObject();
        object.addProperty("message", message);
        object.addProperty("time", new Date().getTime() + "");
        object.addProperty("user", user + "");
        JsonArray msgArray = new JsonArray();
        msgArray.add(object);
        feedback.setMessages(msgArray);
        dismiss();
    }

    private void saveData(Realm realm, String urgent, String type, String[] argumentArray) {
        RealmFeedback feedback = realm.createObject(RealmFeedback.class, UUID.randomUUID().toString());
        feedback.title = "Question regarding /" + argumentArray[2];
        feedback.openTime = new Date().getTime() + "";
        feedback.url = "/" + argumentArray[2];
        feedback.owner = user;
        feedback.source = user;
        feedback.status = "Open";
        feedback.priority = urgent;
        feedback.type = type;
        feedback.parentCode = "dev";
        feedback.state = argumentArray[2];
        feedback.item = argumentArray[1];
        JsonObject object = new JsonObject();
        object.addProperty("message", argumentArray[0]);
        object.addProperty("time", new Date().getTime() + "");
        object.addProperty("user", user + "");
        JsonArray msgArray = new JsonArray();
        msgArray.add(object);
        feedback.setMessages(msgArray);
        dismiss();
    }
}