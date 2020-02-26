package org.ole.planet.myplanet.ui.survey;


import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseDialogFragment;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmExamQuestion;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.CheckboxListView;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.Sort;

/**
 * A simple {@link Fragment} subclass.
 */
public class SendSurveyFragment extends BaseDialogFragment {

    CheckboxListView listView;
    Realm mRealm;
    DatabaseService dbService;
//    ArrayList<Integer> selectedItemsList = new ArrayList<>();

    public SendSurveyFragment() {
    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_send_survey, container, false);

        listView = v.findViewById(R.id.list_users);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        if (TextUtils.isEmpty(id)) {
            dismiss();
            return v;
        }
        v.findViewById(R.id.btn_cancel).setOnClickListener(view -> dismiss());
        return v;
    }

    private void createSurveySubmission(String userId) {
        Realm mRealm = new DatabaseService(getActivity()).getRealmInstance();
        RealmStepExam exam = mRealm.where(RealmStepExam.class).equalTo("id", id).findFirst();
        mRealm.beginTransaction();
        RealmSubmission sub = mRealm.where(RealmSubmission.class)
                .equalTo("userId", userId)
                .equalTo("parentId", (!TextUtils.isEmpty(exam.getCourseId() ) ) ? id + "@"+exam.getCourseId() : id  )
                .sort("lastUpdateTime", Sort.DESCENDING)
                .equalTo("status", "pending")
                .findFirst();
        sub = RealmSubmission.createSubmission(sub, mRealm);
        sub.setParentId((!TextUtils.isEmpty(exam.getCourseId())) ? id + "@"+exam.getCourseId() : id );
     //   sub.setParentId(id + "@" + exam.getCourseId());
        sub.setUserId(userId);
        sub.setType("survey");
        sub.setStatus("pending");
        sub.setStartTime(new Date().getTime());
        mRealm.commitTransaction();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        List<RealmUserModel> users = mRealm.where(RealmUserModel.class).findAll();
        initListView(users);
        getView().findViewById(R.id.send_survey).setOnClickListener(view -> {
            for (int i = 0; i < listView.getSelectedItemsList().size(); i++) {
                RealmUserModel u = users.get(i);
                createSurveySubmission(u.getId());
            }
            Utilities.toast(getActivity(), "Survey sent to users");
            dismiss();
        });
    }

    private void initListView(List<RealmUserModel> users) {
        ArrayAdapter<RealmUserModel> adapter = new ArrayAdapter<RealmUserModel>(getActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, users);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(adapter);
//        listView.setOnItemClickListener((adapterView, view, i, l) -> {
//            String itemSelected = ((TextView) view).getText().toString();
//            if (selectedItemsList.contains(itemSelected)) {
//                selectedItemsList.remove(itemSelected);
//            } else {
//                selectedItemsList.add(i);
//            }
//        });

    }

    @Override
    protected String getKey() {
        return "surveyId";
    }
}
