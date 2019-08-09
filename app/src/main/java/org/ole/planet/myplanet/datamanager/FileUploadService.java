package org.ole.planet.myplanet.datamanager;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FileUploadService {
    public void uploadAttachment(String id, String rev, RealmMyPersonal personal, SuccessListener listener) {
        ApiInterface apiInterface = ApiClient.getClient().create(ApiInterface.class);
        File f = new File(personal.getPath());
        String name = FileUtils.getFileNameFromUrl(personal.getPath());
        try {
            URLConnection connection = f.toURL().openConnection();
            String mimeType = connection.getContentType();
            RequestBody body = RequestBody.create(MediaType.parse("application/octet"), FileUtils.fullyReadFileToBytes(f));
            String url = String.format("%s/resources/%s/%s", Utilities.getUrl(), id, name);
            apiInterface.uploadResource(Utilities.getHeader(), mimeType, rev, url, body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    onDataReceived(response.body(), listener);
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    listener.onSuccess("Unable to upload resource");
                }
            });
        } catch (IOException e) {
            listener.onSuccess("Unable to upload resource");
        }
    }

    private void onDataReceived(JsonObject object, SuccessListener listener) {
        if (object != null) {
            if (JsonUtils.getBoolean("ok", object)) {
                listener.onSuccess("Uploaded successfully");
                return;
            }
        }
        listener.onSuccess("Unable to upload resource");
    }

}
