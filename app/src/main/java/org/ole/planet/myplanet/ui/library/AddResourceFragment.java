package org.ole.planet.myplanet.ui.library;


import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.jetbrains.annotations.NotNull;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.AudioRecorderService;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.Date;
import java.util.UUID;

import io.realm.Realm;

import static android.app.Activity.RESULT_OK;

/**
 * A simple {@link Fragment} subclass.
 */
public class AddResourceFragment extends BottomSheetDialogFragment {

    static final int REQUEST_VIDEO_CAPTURE = 1;
    static final int REQUEST_RECORD_SOUND = 0;
    static final int REQUEST_CAPTURE_PICTURE = 2;
    int type = 0;
    TextView tvTime;
    FloatingActionButton floatingActionButton;
    AudioRecorderService audioRecorderService;
    File output;

    public AddResourceFragment() {
    }

    public static void showAlert(Context context, String path) {
        View v = LayoutInflater.from(context).inflate(R.layout.alert_my_personal, null);
        EditText etTitle = v.findViewById(R.id.et_title);
        EditText etDesc = v.findViewById(R.id.et_description);
        RealmUserModel realmUserModel = new UserProfileDbHandler(MainApplication.context).getUserModel();
        String userId = realmUserModel.getId();
        String userName = realmUserModel.getName();
        new AlertDialog.Builder(context).setTitle("Enter resource detail").setView(v).setPositiveButton("Save", (dialogInterface, i) -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Utilities.toast(context, "Title is required.");
                return;
            }
            String desc = etDesc.getText().toString().trim();
            Realm realm = new DatabaseService(context).getRealmInstance();
            realm.executeTransactionAsync(realm1 -> {
                RealmMyPersonal myPersonal = realm1.createObject(RealmMyPersonal.class, UUID.randomUUID().toString());
                myPersonal.setTitle(title);
                myPersonal.setUserId(userId);
                myPersonal.setUserName(userName);
                myPersonal.setPath(path);
                myPersonal.setDate(new Date().getTime());
                myPersonal.setDescription(desc);
            }, () -> Utilities.toast(MainApplication.context, "Resource Saved to my personal"));
        }).setNegativeButton("Dismiss", null).show();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = getArguments().getInt("type", 0);
        }
    }

    @NotNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        bottomSheetDialog.setOnShowListener(d -> {
            BottomSheetDialog dialog = (BottomSheetDialog) d;
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            BottomSheetBehavior.from(bottomSheet).setSkipCollapsed(true);
            BottomSheetBehavior.from(bottomSheet).setHideable(true);
        });
        return bottomSheetDialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_add_resource, container, false);
        v.findViewById(R.id.ll_record_video).setOnClickListener(view -> dispatchTakeVideoIntent());
        v.findViewById(R.id.ll_record_audio).setOnClickListener(view -> {
            showAudioRecordAlert();
        });
        v.findViewById(R.id.ll_capture_image).setOnClickListener(view -> takePhoto());
        v.findViewById(R.id.ll_draft).setOnClickListener(view -> FileUtils.openOleFolder(this, 100));
        return v;
    }

    private void showAudioRecordAlert() {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_sound_recorder, null);
        tvTime = v.findViewById(R.id.tv_time);
        floatingActionButton = v.findViewById(R.id.fab_record);
        AlertDialog dialog = new AlertDialog.Builder(getActivity()).setTitle("Record Audio").setView(v).setCancelable(false).create();
        createAudioRecorderService(dialog);
        floatingActionButton.setOnClickListener(view -> {
            if (!audioRecorderService.isRecording()) {
                audioRecorderService.startRecording();
            } else {
                audioRecorderService.stopRecording();
            }
        });
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Dismiss", (dialogInterface, i) -> {
            if (audioRecorderService != null && audioRecorderService.isRecording()) {
                audioRecorderService.forceStop();
            }
            dialog.dismiss();
        });
        dialog.show();
    }

    private void createAudioRecorderService(AlertDialog dialog) {
        audioRecorderService = new AudioRecorderService().setAudioRecordListener(new AudioRecorderService.AudioRecordListener() {
            @Override
            public void onRecordStarted() {
                tvTime.setText(R.string.recording_audio);
                floatingActionButton.setImageResource(R.drawable.ic_stop);
            }

            @Override
            public void onRecordStopped(String outputFile) {
                tvTime.setText("");
                dialog.dismiss();
                startIntent(outputFile);
                floatingActionButton.setImageResource(R.drawable.ic_mic);
            }

            @Override
            public void onError(String error) {
                Utilities.toast(getActivity(), error);
            }
        });
    }

    private void dispatchTakeVideoIntent() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(Utilities.SD_PATH + "/video/" + UUID.randomUUID().toString() + ".mp4")));
        if (takeVideoIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }

    public void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File dir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        output = new File(dir, UUID.randomUUID().toString() + ".jpg");
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(output));
        startActivityForResult(intent, REQUEST_CAPTURE_PICTURE);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri url = null;
            String path = "";
            if (requestCode == REQUEST_CAPTURE_PICTURE) {
                if (output != null) {
                    url = Uri.fromFile(output);
                    path = url.getPath();
                }
            } else {
                url = data.getData();
                path = FileUtils.getRealPathFromURI(getActivity(), url);
                if (TextUtils.isEmpty(path)) {
                    path = FileUtils.getImagePath(getActivity(), url);
                }
            }
            startIntent(path);
        }

    }


    private void startIntent(String path) {
        if (!TextUtils.isEmpty(path)) {
            addResource(path);
        } else {
            Utilities.toast(getActivity(), "Invalid resource url");
        }
    }


    private void addResource(String path) {
        if (type == 0) {
            startActivity(new Intent(getActivity(), AddResourceActivity.class).putExtra("resource_local_url", path));
        } else {
            showAlert(getActivity(), path);
        }
    }
}
