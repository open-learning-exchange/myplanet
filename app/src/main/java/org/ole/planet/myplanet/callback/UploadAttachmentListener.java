package org.ole.planet.myplanet.callback;

import com.google.gson.JsonObject;

public interface UploadAttachmentListener {
    void onUploaded(JsonObject object);
    void onError(String message);
}
