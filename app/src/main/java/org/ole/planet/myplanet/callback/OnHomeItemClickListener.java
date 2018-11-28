package org.ole.planet.myplanet.callback;

import android.support.v4.app.Fragment;

import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_stepExam;

public interface OnHomeItemClickListener {
    void openCallFragment(Fragment f);

    void openLibraryDetailFragment(realm_myLibrary library);

    void showRatingDialog(String resource, String resource_id, String title);

    void sendSurvey(realm_stepExam current);
}
