package org.ole.planet.myplanet.datamanager;


import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.Download;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class MyDownloadService extends IntentService {
    int count;
    byte data[] = new byte[1024 * 4];
    File outputFile;
    private NotificationCompat.Builder notificationBuilder;
    private NotificationManager notificationManager;
    private int totalFileSize;
    private SharedPreferences preferences;
    private String url;
    private ArrayList<String> urls;
    private int currentIndex = 0;
    private Realm mRealm;
    private Call<ResponseBody> request;
    private boolean completeAll;

    public MyDownloadService() {
        super("Download Service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        preferences = getApplicationContext().getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (urls == null) {
            stopSelf();
        }
        notificationBuilder = new NotificationCompat.Builder(this, "11");
        NotificationUtil.setChannel(notificationManager);
        Notification noti = notificationBuilder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("OLE Download")
                .setContentText("Downloading File...")
                .setAutoCancel(true).build();
        notificationManager.notify(0, noti);
        urls = intent.getStringArrayListExtra("urls");
        realmConfig();
        for (int i = 0; i < urls.size(); i++) {
            url = urls.get(i);
            currentIndex = i;
            initDownload();
        }
    }

    private void initDownload() {
        Utilities.log("File url " + url);
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        request = retrofitInterface.downloadFile(Utilities.getHeader(), url);
        try {
            Response r = request.execute();
            if (r.code() == 200) {
                ResponseBody responseBody = (ResponseBody) r.body();
                if (!checkStorage(responseBody.contentLength())) {
                    downloadFile(responseBody);
                }
            } else {
                downloadFiled(r.code() == 404 ? "File Not found " : "Connection failed");
            }
        } catch (IOException e) {
            e.printStackTrace();
            downloadFiled(e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    private void downloadFiled(String message) {
        notificationBuilder.setContentText(message);
        notificationManager.notify(0, notificationBuilder.build());
        Download d = new Download();
        completeAll = false;
        d.setFailed(true);
        d.setMessage(message);
        sendIntent(d);
        stopSelf();
    }


    private void downloadFile(ResponseBody body) throws IOException {
        long fileSize = body.contentLength();
        InputStream bis = new BufferedInputStream(body.byteStream(), 1024 * 8);
        outputFile = FileUtils.getSDPathFromUrl(url);
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
            download.setFileName(FileUtils.getFileNameFromUrl(url));
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

    private boolean checkStorage(long fileSize) {
        if (!FileUtils.externalMemoryAvailable()) {
            downloadFiled("Download Failed : SD card Not available");
            return true;
        } else if (fileSize > FileUtils.getAvailableExternalMemorySize()) {
            downloadFiled("Download Failed : Not enough storage in SD card");
            return true;
        }
        return false;
    }

    private void closeStreams(OutputStream output, InputStream bis) throws IOException {
        output.flush();
        output.close();
        bis.close();
        onDownloadComplete();
    }

    private void sendNotification(Download download) {
        download.setFileName("Downloading : " + FileUtils.getFileNameFromUrl(url));
        sendIntent(download);
        notificationBuilder.setProgress(100, download.getProgress(), false);
        notificationBuilder.setContentText("Downloading file " + download.getCurrentFileSize() + "/" + totalFileSize + " KB");
        notificationManager.notify(0, notificationBuilder.build());
    }

    private void sendIntent(Download download) {
        Intent intent = new Intent(DashboardActivity.MESSAGE_PROGRESS);
        intent.putExtra("download", download);
        LocalBroadcastManager.getInstance(MyDownloadService.this).sendBroadcast(intent);
    }

    private void onDownloadComplete() {
        if (outputFile.length() > 0)
            changeOfflineStatus();
        Download download = new Download();
        download.setFileName(FileUtils.getFileNameFromUrl(url));
        download.setFileUrl(url);
        download.setProgress(100);
        if (currentIndex == urls.size() - 1) {
            completeAll = true;
            download.setCompleteAll(true);
        }
        sendIntent(download);
        notificationManager.cancel(0);
        notificationBuilder.setProgress(0, 0, false);
        notificationBuilder.setContentText("File Downloaded");
        notificationManager.notify(0, notificationBuilder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!completeAll) {
            stopDownload();
        }
    }

    private void stopDownload() {
        if (request != null && outputFile != null) {
            request.cancel();
            outputFile.delete();
            notificationManager.cancelAll();
        }
    }

    private void changeOfflineStatus() {
        final String currentFileName = FileUtils.getFileNameFromUrl(url);
        mRealm.executeTransaction(realm -> {
            RealmMyLibrary obj = realm.where(RealmMyLibrary.class).equalTo("resourceLocalAddress", currentFileName).findFirst();
            if (obj != null) {
                obj.setResourceOffline(true);
                obj.setDownloadedRev(obj.get_rev());
            } else {
                Utilities.log("object Is null");
            }
        });
    }

    public void realmConfig() {
        Realm.init(this);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(4)
                .build();
        Realm.setDefaultConfiguration(config);
        mRealm = Realm.getInstance(config);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        notificationManager.cancel(0);
    }

}
