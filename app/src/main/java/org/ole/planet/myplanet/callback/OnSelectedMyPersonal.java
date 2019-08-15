package org.ole.planet.myplanet.callback;

import org.ole.planet.myplanet.model.RealmMyPersonal;

public interface OnSelectedMyPersonal {
    void onUpload(RealmMyPersonal personal);

    void onAddedResource();
}
