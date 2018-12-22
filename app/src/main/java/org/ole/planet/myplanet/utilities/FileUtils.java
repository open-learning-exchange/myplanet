package org.ole.planet.myplanet.utilities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import org.ole.planet.myplanet.BuildConfig;

import java.io.File;

public class FileUtils {
    public static final String SD_PATH = Environment.getExternalStorageDirectory() + "/ole";

    public static boolean externalMemoryAvailable() {
        return android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
    }

    public static long getAvailableExternalMemorySize() {
        if (externalMemoryAvailable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long availableBlocks = stat.getAvailableBlocksLong();
            return availableBlocks * blockSize;
        } else {
            return 0;
        }
    }

    private static File createFilePath(String folder, String filename) {
        File f = new File(folder);
        if (!f.exists())
            f.mkdirs();
        Utilities.log("Return file " + folder + "/" + filename);
        return new File(f, filename);
    }

    public static File getSDPathFromUrl(String url) {
        return createFilePath(SD_PATH, getFileNameFromUrl(url));
    }

    public static boolean checkFileExist(String url) {
        if (url == null || url.isEmpty())
            return false;
        File f = createFilePath(SD_PATH, getFileNameFromUrl(url));
        return f.exists();

    }

    public static String getFileNameFromUrl(String url) {
        try {
            return url.substring(url.lastIndexOf("/") + 1);
        } catch (Exception e) {
        }
        return "";
    }

    public static String getFileExtension(String address) {
        if (TextUtils.isEmpty(address))
            return "";
        String filenameArray[] = address.split("\\.");
        return filenameArray[filenameArray.length - 1];
    }

    public static void installApk(AppCompatActivity activity, String file) {
        try {
            File toInstall = new File(Utilities.SD_PATH, file);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkUri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", toInstall);
                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                intent.setData(apkUri);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(intent);
            } else {
                Uri apkUri = Uri.fromFile(toInstall);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            }
        } catch (Exception e) {
        }
    }

}
