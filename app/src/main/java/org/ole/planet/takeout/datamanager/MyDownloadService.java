package org.ole.planet.takeout.datamanager;


import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.widget.Toast;


import org.ole.planet.takeout.Dashboard;
import org.ole.planet.takeout.Data.Download;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.SyncActivity;
import org.ole.planet.takeout.utilities.Utilities;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import okhttp3.ResponseBody;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

public class MyDownloadService extends IntentService {

    public MyDownloadService() {
        super("Download Service");
    }

    private NotificationCompat.Builder notificationBuilder;
    private NotificationManager notificationManager;
    private int totalFileSize;
    private SharedPreferences preferences;
    private String url;
    private ArrayList<String> urls;
    private int currentIndex = 0;

    @Override
    protected void onHandleIntent(Intent intent) {

        preferences = getApplicationContext().getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (urls == null) {
            stopSelf();
        }
        notificationBuilder = new NotificationCompat.Builder(this, "11");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel notificationChannel = new NotificationChannel("11", "ole", importance);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        Notification noti = notificationBuilder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("OLE Download")
                .setContentText("Downloading File...")
                .setAutoCancel(true).build();
        notificationManager.notify(0, noti);
        urls = intent.getStringArrayListExtra("urls");
        for (int i = 0; i < urls.size(); i++) {
            url = urls.get(i);
            currentIndex = i;
            initDownload();
        }

    }

    private void initDownload() {
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        Utilities.log("Url " + url);
        Utilities.log("Header " + getHeader());
        Call<ResponseBody> request = retrofitInterface.downloadFile(getHeader(), url);
        try {
            Response r = request.execute();
            if (r.code() == 200) {
                downloadFile((ResponseBody) r.body());
            } else {
                Utilities.log("Error " + r.code() + " " + r.message());
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public String getHeader() {
        return "Basic " + Base64.encodeToString((preferences.getString("url_user", "") + ":" +
                preferences.getString("url_pwd", "")).getBytes(), Base64.NO_WRAP);
    }


    int count;
    byte data[] = new byte[1024 * 4];

    private void downloadFile(ResponseBody body) throws IOException {
        long fileSize = body.contentLength();
        InputStream bis = new BufferedInputStream(body.byteStream(), 1024 * 8);
        File outputFile = Utilities.getSDPathFromUrl(url);
        OutputStream output = new FileOutputStream(outputFile);
        long total = 0;
        long startTime = System.currentTimeMillis();
        int timeCount = 1;
        while ((count = bis.read(data)) != -1) {
            total += count;
            totalFileSize = (int) (fileSize / (Math.pow(1024, 1)));
            double current = Math.round(total / (Math.pow(1024, 1)));
            int progress = (int) ((total * 100) / fileSize);
            long currentTime = System.currentTimeMillis() - startTime;

            Download download = new Download();
            download.setFileName(Utilities.getFileNameFromUrl(url));
            download.setTotalFileSize(totalFileSize);

            if (currentTime > 1000 * timeCount) {
                download.setCurrentFileSize((int) current);
                download.setProgress(progress);
                sendNotification(download);
                timeCount++;
            }
            output.write(data, 0, count);
        }
        closeStreams(output, bis);

    }

    private void closeStreams(OutputStream output, InputStream bis) throws IOException {
        onDownloadComplete();
        output.flush();
        output.close();
        bis.close();
    }

    private void sendNotification(Download download) {
        download.setFileName("Downloading : " + Utilities.getFileNameFromUrl(url));
        sendIntent(download);
        notificationBuilder.setProgress(100, download.getProgress(), false);
        notificationBuilder.setContentText("Downloading file " + download.getCurrentFileSize() + "/" + totalFileSize + " KB");
        notificationManager.notify(0, notificationBuilder.build());
    }

    private void sendIntent(Download download) {
        Intent intent = new Intent(Dashboard.MESSAGE_PROGRESS);
        intent.putExtra("download", download);
        LocalBroadcastManager.getInstance(MyDownloadService.this).sendBroadcast(intent);
    }

    private void onDownloadComplete() {

        Download download = new Download();
        download.setProgress(100);
        sendIntent(download);
        if (currentIndex == urls.size() - 1) {
            download.setCompleteAll(true);
        }
        notificationManager.cancel(0);
        notificationBuilder.setProgress(0, 0, false);
        notificationBuilder.setContentText("File Downloaded");
        notificationManager.notify(0, notificationBuilder.build());

    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        notificationManager.cancel(0);
    }

}
