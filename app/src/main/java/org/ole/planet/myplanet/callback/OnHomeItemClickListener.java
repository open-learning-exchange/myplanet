package org.ole.planet.myplanet.callback;

import android.support.v4.app.Fragment;

import org.ole.planet.myplanet.Data.realm_myLibrary;

public interface OnHomeItemClickListener {
    void openCallFragment(Fragment f);

    void openLibraryDetailFragment(realm_myLibrary library);
}
