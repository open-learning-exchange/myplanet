package org.ole.planet.myplanet.survey;


import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_examQuestion;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.Sort;

/**
 * A simple {@link Fragment} subclass.
 */
public class SendSurveyFragment extends DialogFragment {

    String surveyId;
    ListView listView;
    Realm mRealm;
    DatabaseService dbService;
    ArrayList<Integer> selectedItemsList = new ArrayList<>();

    public SendSurveyFragment() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth);
        if (getArguments() != null) {
            surveyId = getArguments().getString("surveyId");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_send_survey, container, false);

        listView = v.findViewById(R.id.list_users);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        if (TextUtils.isEmpty(surveyId)){
            dismiss();
            return v;
        }
        v.findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        return v;
    }

    private void createSurveySubmission(String userId) {
        Realm mRealm = new DatabaseService(getActivity()).getRealmInstance();
        List<realm_examQuestion> questions = mRealm.where(realm_examQuestion.class).equalTo("examId", surveyId).findAll();
        mRealm.beginTransaction();
        realm_submissions sub = mRealm.where(realm_submissions.class)
                .equalTo("userId", userId)
                .equalTo("parentId", surveyId)
                .sort("date", Sort.DESCENDING)
                .equalTo("status", "pending")
                .findFirst();
        if (sub == null || questions.size() == sub.getAnswers().size())
            sub = mRealm.createObject(realm_submissions.class, UUID.randomUUID().toString());
        sub.setParentId(surveyId);
        sub.setUserId(userId);
        sub.setType("survey");
        sub.setStatus("pending");
        sub.setDate(new Date().getTime());
        mRealm.commitTransaction();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        List<realm_UserModel> users = mRealm.where(realm_UserModel.class).findAll();
        ArrayAdapter<realm_UserModel> adapter = new ArrayAdapter<realm_UserModel>(getActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, users);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String itemSelected = ((TextView) view).getText().toString();
                if (selectedItemsList.contains(itemSelected)) {
                    selectedItemsList.remove(itemSelected);
                } else {
                    selectedItemsList.add(i);
                }
            }
        });
        getView().findViewById(R.id.send_survey).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (int i = 0 ; i < selectedItemsList.size() ; i ++){
                    realm_UserModel u = users.get(i);
                    createSurveySubmission(u.getId());
                }
                Utilities.toast(getActivity(), "Survey sent to users");
                dismiss();
            }
        });
    }
}
