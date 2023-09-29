package org.ole.planet.myplanet.service;

import static org.ole.planet.myplanet.MainApplication.context;

import android.database.Cursor;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.util.UUID;

public class AudioRecorderService {
    private String outputFile;
    private MediaRecorder myAudioRecorder;
    private AudioRecordListener audioRecordListener;

    public AudioRecorderService() {
    }

    public void forceStop() {
        if (myAudioRecorder != null) {
            myAudioRecorder.stop();
            myAudioRecorder.release();
            myAudioRecorder = null;
        }
        if (audioRecordListener != null) audioRecordListener.onError("Recording stopped");
    }

    public AudioRecorderService setAudioRecordListener(AudioRecordListener audioRecordListener) {
        this.audioRecordListener = audioRecordListener;
        return this;
    }

    public void startRecording() {
        outputFile = createAudioFile();
        myAudioRecorder = new MediaRecorder();
        myAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        myAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        myAudioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        myAudioRecorder.setOutputFile(outputFile);
        try {
            myAudioRecorder.prepare();
            myAudioRecorder.start();
            if (audioRecordListener != null) audioRecordListener.onRecordStarted();
        } catch (Exception e) {
            myAudioRecorder = null;
            if (audioRecordListener != null) {
                audioRecordListener.onError(e.getMessage());
            }
        }
    }

    private String createAudioFile() {
        String audioFileName;
        File audioFile;
        int attempt = 0;

        do {
            audioFileName = UUID.randomUUID().toString() + ".aac";
            audioFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), audioFileName);
            attempt++;
        } while (audioFile.exists() && attempt < 100);

        if (attempt >= 100) {
            return null;
        }

        return audioFile.getAbsolutePath();
    }

    private String getFilePathFromUri(Uri uri) {
        String filePath = null;
        String[] projection = {MediaStore.Audio.Media.DATA};
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            filePath = cursor.getString(columnIndex);
            cursor.close();
        }
        return filePath;
    }

    public boolean isRecording() {
        return myAudioRecorder != null;
    }

    public void stopRecording() {
        if (myAudioRecorder != null) {
            myAudioRecorder.stop();
            myAudioRecorder.release();
            myAudioRecorder = null;
            if (audioRecordListener != null) audioRecordListener.onRecordStopped(outputFile);
        }
    }

    public interface AudioRecordListener {
        void onRecordStarted();

        void onRecordStopped(String outputFile);

        void onError(String error);
    }
}