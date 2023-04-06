package org.ole.planet.myplanet.utilities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import org.ole.planet.myplanet.BuildConfig;
import org.ole.planet.myplanet.di.IOExecutor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;

public class FileUtils {
    public static final String SD_PATH = Environment.getExternalStorageDirectory() + "/ole";
    private static final String LogTag = FileUtils.class.getSimpleName();

    @Inject
    @IOExecutor
    public ExecutorService ioExecutor; //public because dagger does not support private field injection
    @ApplicationContext
    public Context context;

    @Inject
    public StorageManager storageManager;
    private static final class GlobalFileUtilsInstanceHolder {
        static final FileUtils globalFileUtilsInstance = new FileUtils();
    }

    public static FileUtils getInstance(){
        return GlobalFileUtilsInstanceHolder.globalFileUtilsInstance;
    }

    public static byte[] fullyReadFileToBytes(File f) throws IOException {
        int size = (int) f.length();
        CountDownLatch latch = new CountDownLatch(1);
        byte[] bytes = new byte[size];

        FileUtils.getInstance().ioExecutor.execute(()->{
            byte[] tmpBuff = new byte[size];
            try (FileInputStream fis = new FileInputStream(f)) {

                int read = fis.read(bytes, 0, size);
                if (read < size) {
                    int remain = size - read;
                    while (remain > 0) {
                        read = fis.read(tmpBuff, 0, remain);
                        System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
                        remain -= read;
                    }
                }
            } catch (IOException e) {
                Log.e(LogTag,"the following exception occurred while reading file into bytes",e);
            }
            latch.countDown();
        });

        try{
            latch.await();
        }catch (InterruptedException ignored){}
        return bytes;
    }

    private static File createFilePath(String folder, String filename) {
        File f = new File(folder);
        if (!f.exists())
            f.mkdirs();
        Utilities.log("Return file " + folder + "/" + filename);
        return new File(f, filename);
    }

    public static File getSDPathFromUrl(String url) {

        return createFilePath(SD_PATH + "/" + getIdFromUrl(url), getFileNameFromUrl(url));
    }

    public static boolean checkFileExist(String url) {
        if (url == null || url.isEmpty())
            return false;
        File f = createFilePath(SD_PATH + "/" + getIdFromUrl(url), getFileNameFromUrl(url));
        return f.exists();
    }

    public static String getFileNameFromUrl(String url) {
        try {
            return url.substring(url.lastIndexOf("/") + 1);
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String getIdFromUrl(String url) {
        try {
            String[] sp = url.substring(url.indexOf("resources/")).split("/");
            Utilities.log("Id " + sp[1]);
            return sp[1];
        } catch (Exception ignored) {
        }
        return "";
    }


    public static String getFileExtension(String address) {
        if (TextUtils.isEmpty(address))
            return "";
        String[] filenameArray = address.split("\\.");
        return filenameArray[filenameArray.length - 1];
    }

    public static void installApk(Context activity, String file) {
        try {
            if (!file.endsWith("apk")) return;
            File toInstall = FileUtils.getSDPathFromUrl(file);
            toInstall.setReadable(true, false);
            Uri apkUri;
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", toInstall);
                intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setData(apkUri);
            } else {
                apkUri = Uri.fromFile(toInstall);
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            activity.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(extension);
        }
        return type;
    }

    public static void copyAssets(Context context) {
        String[] tiles = {"dhulikhel.mbtiles", "somalia.mbtiles"};
        AssetManager assetManager = context.getAssets();
        FileUtils.getInstance().ioExecutor.execute(()->{
            try {
                for (String s : tiles) {
                    InputStream in;
                    OutputStream out;
                    in = assetManager.open(s);
                    Utilities.log("MAP " + s);
                    File outFile = new File(Environment.getExternalStorageDirectory() + "/osmdroid", s);
                    out = new FileOutputStream(outFile);
                    copyFile(in, out);
                    out.close();
                    in.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(LogTag, "Failed to copy asset file: " + e);
            }
        });

    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }


    public static String getRealPathFromURI(Context context, Uri contentUri) {
        CountDownLatch latch = new CountDownLatch(1);
        String[] paths = new String[1];
        FileUtils.getInstance().ioExecutor.execute(()->{
            String[] proj = {MediaStore.Images.Media.DATA};
            try (Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null)) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                paths[0] = cursor.getString(column_index);
            }

            latch.countDown();

        });

        try{
            latch.await();
        }catch (InterruptedException ignored){}

        return paths[0];
    }



    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static String getStringFromFile(File fl) throws Exception {

        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);
        fin.close();
        return ret;
    }
    public static void openOleFolder(Fragment context, int request) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        Uri uri = Uri.parse(Utilities.SD_PATH);
        intent.setDataAndType(uri, "*/*");
        context.startActivityForResult(Intent.createChooser(intent, "Open folder"), request);
    }


    public static String getImagePath(Context context, Uri uri) {
        CountDownLatch  latch = new CountDownLatch(1);
        String[] imagePaths = new String[1];
        FileUtils.getInstance().ioExecutor.execute(()->{
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            cursor.moveToFirst();
            String document_id = cursor.getString(0);
            document_id = document_id.substring(document_id.lastIndexOf(":") + 1);
            cursor.close();

            cursor = context.getContentResolver().query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    null, MediaStore.Images.Media._ID + " = ? ", new String[]{document_id}, null);
            cursor.moveToFirst();
            int columnIndex=cursor.getColumnIndex(MediaStore.Images.Media.DATA);
            if (columnIndex!=-1){
               imagePaths[0]= cursor.getString(columnIndex);
            }
            cursor.close();
            latch.countDown();
        });

        try{
            latch.await();
        }catch (InterruptedException ignored){}

        return imagePaths[0];
    }

    public static String getMediaType(String path) {
        String ext = getFileExtension(path);
        if (ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("png"))
            return "image";
        else if (ext.equalsIgnoreCase("mp4"))
            return "mp4";
        else if (ext.equalsIgnoreCase("mp3") || ext.equalsIgnoreCase("aac"))
            return "audio";
        return "";
    }

    // Disk space utilities

    /**
     *
     * @return Total internal memory capacity.
     */
    public static long getTotalInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        return totalBlocks * blockSize;
    }

    /**
     * Find space left in the internal memory.
     */
    public static long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        return availableBlocks * blockSize;
    }

    public static boolean externalMemoryAvailable() {
        return android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static long getAvailableExternalMemorySizePostNougat(){
       StorageManager storageManager1 = FileUtils.getInstance().storageManager;
       StorageVolume primaryStorageVolume= storageManager1.getPrimaryStorageVolume();
       if (primaryStorageVolume==null) return 0;
       if (!primaryStorageVolume.getState().equals(Environment.MEDIA_MOUNTED)) return 0;
       UUID primaryStorageUUID = UUID.fromString(primaryStorageVolume.getUuid());
       try{
           return storageManager1.getAllocatableBytes(primaryStorageUUID);
       }catch (IOException ex){
           Log.e(LogTag,"The following exception occurred while determining the available memory space post nougat",ex);
           return 0;
       }
    }

    private static long getAvailableExternalMemorySizePreNougat(){
        if (!externalMemoryAvailable()) return 0;
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        return availableBlocks * blockSize;

    }

    /**
     * Find space left in the external memory.
     */
    public static long getAvailableExternalMemorySize() {
        CountDownLatch latch = new CountDownLatch(1);
        long[] memorySizes = new long[1];
        FileUtils.getInstance().ioExecutor.execute(()->{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                memorySizes[0]=getAvailableExternalMemorySizePostNougat();
            }else{
                memorySizes[0]=getAvailableExternalMemorySizePreNougat();
            }
            latch.countDown();
        });

        try{
            latch.await();
        }catch (InterruptedException ignored){}
    return memorySizes[0];
    }

    /**
     *
     * @return Total capacity of the external memory
     */
    public static long getTotalExternalMemorySize() {
        if (externalMemoryAvailable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            return totalBlocks * blockSize;
        } else {
            return 0;
        }
    }

    /**
     * Coverts Bytes to KB/MB/GB and changes magnitude accordingly.
     * @param size
     * @return A string with size followed by an appropriate suffix
     */
    public static String formatSize(long size) {
        return Formatter.formatShortFileSize(FileUtils.getInstance().context,size);
    }

    public static long getTotalAvailableMemory() {
        long internalAvailableMemory = getAvailableInternalMemorySize();
        long externalAvailableMemory = getAvailableExternalMemorySize();
        // Temporary Check till we find a better way to do it
        if (internalAvailableMemory == externalAvailableMemory) {
            return internalAvailableMemory;
        }
        return internalAvailableMemory + externalAvailableMemory;
    }

    public static long getTotalMemoryCapacity() {
        long internalTotalMemory = getTotalInternalMemorySize();
        long externalTotalMemory = getTotalExternalMemorySize();
        // Temporary Check till we find a better way to do it
        if (internalTotalMemory == externalTotalMemory) {
            return internalTotalMemory;
        }
        return internalTotalMemory + externalTotalMemory;
    }

    public static long getTotalAvailableMemoryRatio() {
        return Math.round(((double) getTotalAvailableMemory() / (double) getTotalMemoryCapacity()) * 100);
    }

    /**
     * A method that returns a formatted string
     * of the format "Available Space / Total Space".
     * param None
     * @return Available space and total space
     */
    public static String getAvailableOverTotalMemoryFormattedString() {
        long available = getTotalAvailableMemory();
        long total = getTotalMemoryCapacity();
        return "Available Space: "
                + formatSize(available)
                + "/"
                + formatSize(total);
    }
}
