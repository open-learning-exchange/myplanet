package org.ole.planet.myplanet.service;

import android.media.MediaRecorder;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
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
        if (audioRecordListener != null)
            audioRecordListener.onError("Recording stopped");
    }

    public AudioRecorderService setAudioRecordListener(AudioRecordListener audioRecordListener) {
        this.audioRecordListener = audioRecordListener;
        return this;
    }

    public void startRecording() {
        outputFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + UUID.randomUUID().toString() + ".aac";
        File f = new File(outputFile);
        createFileIfNotExists(f);
        myAudioRecorder = new MediaRecorder();
        myAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        myAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        myAudioRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
        myAudioRecorder.setOutputFile(outputFile);
        try {
            myAudioRecorder.prepare();
            myAudioRecorder.start();
            if (audioRecordListener != null)
                audioRecordListener.onRecordStarted();
        } catch (Exception e) {
            myAudioRecorder = null;
            if (audioRecordListener != null) {
                audioRecordListener.onError(e.getMessage());
            }

        }
    }

    private void createFileIfNotExists(File f) {
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isRecording() {
        return myAudioRecorder != null;
    }

    public void stopRecording() {
        if (myAudioRecorder != null) {
            myAudioRecorder.stop();
            myAudioRecorder.release();
            myAudioRecorder = null;
            if (audioRecordListener != null)
                audioRecordListener.onRecordStopped(outputFile);
        }
    }

    public interface AudioRecordListener {
        void onRecordStarted();

        void onRecordStopped(String outputFile);

        void onError(String error);
    }
}
