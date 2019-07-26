package org.ole.planet.myplanet.utilities;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;

public class CameraUtils {
  public  interface ImageCaptureCallback{
        void onImageCapture(String fileUri);
    }
    public static void CapturePhoto(final ImageCaptureCallback callback) {

        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int frontCamera = 1;
        //int backCamera=0;
        Camera camera;
        try {
            Camera.getCameraInfo(frontCamera, cameraInfo);
            camera = Camera.open(frontCamera);
        } catch (RuntimeException e) {
            Utilities.log("Front Camera not available");
            return;
        }
        try {
            camera.setPreviewTexture(new SurfaceTexture(0));
            camera.startPreview();
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    savePicture(data, callback);
                    camera.release();
                }
            });
        } catch (Exception e) {
            camera.release();
        }
    }

    private static void savePicture(byte[] data, ImageCaptureCallback callback) {
        File pictureFileDir = new File(Utilities.SD_PATH + "/userimages");
        if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {
            pictureFileDir.mkdirs();
        }
        String photoFile = new Date().getTime() + ".jpg";
        String filename = pictureFileDir.getPath() + File.separator + photoFile;
        File mainPicture = new File(filename);
        try {
            FileOutputStream fos = new FileOutputStream(mainPicture);
            fos.write(data);
            fos.close();
            Utilities.log("image saved");
            callback.onImageCapture(mainPicture.getAbsolutePath());
        } catch (Exception error) {
            Log.d("kkkk", "Image could not be saved");
        }
    }
}