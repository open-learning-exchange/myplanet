package org.ole.planet.takeout.datamanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.ole.planet.takeout.SyncActivity;
import org.ole.planet.takeout.utils.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DownloadService {

    private SharedPreferences preferences;
    private Context context;
    DownloadCallback callback;

    public DownloadService(Context context, DownloadCallback callback) {
        this.context = context;
        preferences = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
        this.callback = callback;

    }

    public interface DownloadCallback {
        void onSuccess(String s);
        void onFailure(String e);
    }

    public void downloadFile(String url) {
        ApiInterface downloadService = ApiClient.getClient().create(ApiInterface.class);
        String header = Base64.encodeToString((preferences.getString("url_user", "") + ":" +
                preferences.getString("url_pwd", "")).getBytes(), Base64.NO_WRAP);
        Utilities.log("Header " + header);
        downloadService.downloadFile("Basic " + header, url).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.body() != null)

                    if (writeResponseBodyToDisk(response.body(), url)) {
                        callback.onSuccess("File downloaded successfully");
                    } else {
                        callback.onFailure("Unable to download file");
                    }

            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                callback.onFailure("Unable to download file");
            }
        });
    }

    private boolean writeResponseBodyToDisk(ResponseBody body, String url) {
        try {
            File futureStudioIconFile = Utilities.getSDPathFromUrl(url);
            InputStream inputStream = null;
            OutputStream outputStream = null;

            try {
                byte[] fileReader = new byte[4096];
                long fileSize = body.contentLength();
                long fileSizeDownloaded = 0;
                inputStream = body.byteStream();
                outputStream = new FileOutputStream(futureStudioIconFile);
                while (true) {
                    int read = inputStream.read(fileReader);
                    if (read == -1) {
                        break;
                    }
                    outputStream.write(fileReader, 0, read);
                    fileSizeDownloaded += read;
                    }

                outputStream.flush();

                return true;
            } catch (IOException e) {
                return false;
            } finally {
               closeStream(inputStream, outputStream);
            }

        } catch (IOException e) {
            return false;
        }
    }

    private void closeStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        if (inputStream != null) {
            inputStream.close();
        }

        if (outputStream != null) {
            outputStream.close();
        }
    }

}
