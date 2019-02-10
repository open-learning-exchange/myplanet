package org.ole.planet.myplanet.callback;

import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmTag;

import java.util.List;

public interface OnLibraryItemSelected {
    void onSelectedListChange(List<RealmMyLibrary> list);
    void onTagClicked( RealmTag realmTag);
}
