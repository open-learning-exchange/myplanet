package org.ole.planet.takeout.courses.exam;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;

import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_examQuestion;
import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.Data.realm_submissions;
import org.ole.planet.takeout.datamanager.DatabaseService;

import io.realm.Realm;
import io.realm.RealmResults;

public class BaseExamFragment extends Fragment {
    realm_stepExam exam;
    DatabaseService db;
    Realm mRealm;
    String stepId;
    String id = "";
    String type = "exam";
    int currentIndex = 0;
    RealmResults<realm_examQuestion> questions;
    String ans = "";
    realm_UserModel user;
    realm_submissions sub;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            stepId = getArguments().getString("stepId");
            checkId();
            checkType();
        }
    }

    private void checkId() {
        if (TextUtils.isEmpty(stepId)) {
            id = getArguments().getString("id");
        }
    }

    private void checkType() {
        if (getArguments().containsKey("type")) {
            type = getArguments().getString("type");
        }
    }

    public void initExam() {
        if (!TextUtils.isEmpty(stepId)) {
            exam = mRealm.where(realm_stepExam.class).equalTo("stepId", stepId).findFirst();
        } else {
            exam = mRealm.where(realm_stepExam.class).equalTo("id", id).findFirst();
        }
    }


}
