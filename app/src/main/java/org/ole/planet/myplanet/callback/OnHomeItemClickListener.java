package org.ole.planet.myplanet.callback;

import androidx.fragment.app.Fragment;

import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmStepExam;

public interface OnHomeItemClickListener {
    void openCallFragment(Fragment f);

    void openLibraryDetailFragment(RealmMyLibrary library);

    void showRatingDialog(String resource, String resource_id, String title, OnRatingChangeListener listener);

    void sendSurvey(RealmStepExam current);
}
