package org.ole.planet.myplanet.ui.survey;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseDialogFragment;
import org.ole.planet.myplanet.databinding.FragmentSendSurveyBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.Sort;

public class SendSurveyFragment extends BaseDialogFragment {
    private FragmentSendSurveyBinding fragmentSendSurveyBinding;
    Realm mRealm;
    DatabaseService dbService;

    public SendSurveyFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentSendSurveyBinding = FragmentSendSurveyBinding.inflate(inflater, container, false);

        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        if (TextUtils.isEmpty(id)) {
            dismiss();
            return fragmentSendSurveyBinding.getRoot();
        }
        fragmentSendSurveyBinding.btnCancel.setOnClickListener(view -> dismiss());
        return fragmentSendSurveyBinding.getRoot();
    }

    private void createSurveySubmission(String userId) {
        Realm mRealm = new DatabaseService(getActivity()).getRealmInstance();
        RealmStepExam exam = mRealm.where(RealmStepExam.class).equalTo("id", id).findFirst();
        mRealm.beginTransaction();
        RealmSubmission sub = mRealm.where(RealmSubmission.class).equalTo("userId", userId).equalTo("parentId", (!TextUtils.isEmpty(exam.courseId)) ? id + "@" + exam.courseId : id).sort("lastUpdateTime", Sort.DESCENDING).equalTo("status", "pending").findFirst();
        sub = RealmSubmission.createSubmission(sub, mRealm);
        sub.parentId = (!TextUtils.isEmpty(exam.courseId)) ? id + "@" + exam.courseId : id;
        sub.userId = userId;
        sub.type = "survey";
        sub.status = "pending";
        sub.startTime = new Date().getTime();
        mRealm.commitTransaction();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        List<RealmUserModel> users = mRealm.where(RealmUserModel.class).findAll();
        initListView(users);
        fragmentSendSurveyBinding.sendSurvey.setOnClickListener(view -> {
            for (int i = 0; i < fragmentSendSurveyBinding.listUsers.getSelectedItemsList().size(); i++) {
                RealmUserModel u = users.get(i);
                createSurveySubmission(u.id);
            }
            Utilities.toast(getActivity(), getString(R.string.survey_sent_to_users));
            dismiss();
        });
    }

    private void initListView(List<RealmUserModel> users) {
        ArrayAdapter<RealmUserModel> adapter = new ArrayAdapter<RealmUserModel>(getActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, users);
        fragmentSendSurveyBinding.listUsers.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        fragmentSendSurveyBinding.listUsers.setAdapter(adapter);
    }

    @Override
    protected String getKey() {
        return "surveyId";
    }
}
