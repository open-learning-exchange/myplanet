package org.ole.planet.myplanet.ui.library;

import static android.app.Activity.RESULT_OK;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

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

public class AddResourceFragment extends BottomSheetDialogFragment {
    static final int REQUEST_VIDEO_CAPTURE = 1;
    static final int REQUEST_RECORD_SOUND = 0;
    static final int REQUEST_CAPTURE_PICTURE = 2;
    int type = 0;
    TextView tvTime;
    FloatingActionButton floatingActionButton;
    AudioRecorderService audioRecorderService;
    File output;
    private Uri photoURI;

    public AddResourceFragment() {
    }

    public static void showAlert(Context context, String path) {
        View v = LayoutInflater.from(context).inflate(R.layout.alert_my_personal, null);
        EditText etTitle = v.findViewById(R.id.et_title);
        EditText etDesc = v.findViewById(R.id.et_description);
        RealmUserModel realmUserModel = new UserProfileDbHandler(MainApplication.context).getUserModel();
        String userId = realmUserModel.getId();
        String userName = realmUserModel.getName();
        new AlertDialog.Builder(context).setTitle(R.string.enter_resource_detail).setView(v).setPositiveButton("Save", (dialogInterface, i) -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Utilities.toast(context, String.valueOf(R.string.title_is_required));
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
            }, () -> Utilities.toast(MainApplication.context, context.getString(R.string.resource_saved_to_my_personal)));
        }).setNegativeButton(R.string.dismiss, null).show();
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss), (dialogInterface, i) -> {
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
//                startIntent(outputFile);
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
        Uri videoUri = FileProvider.getUriForFile(getActivity(), "org.ole.planet.myplanet.fileprovider", createVideoFile());
        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
        takeVideoIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (takeVideoIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }

    private File createVideoFile() {
        File videoDir = new File(Utilities.SD_PATH + "/video/");
        videoDir.mkdirs();
        File videoFile = new File(videoDir, UUID.randomUUID().toString() + ".mp4");
        return videoFile;
    }

    public void takePhoto() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Photo_" + UUID.randomUUID().toString());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ole/photo");
        }

        photoURI = requireActivity().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CAPTURE_PICTURE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Uri uri = null;
//            String path = "";
            if (requestCode == REQUEST_CAPTURE_PICTURE) {
                uri = photoURI;
            } else {
//                url = data.getData();
//                path = FileUtils.getRealPathFromURI(getActivity(), url);
//                if (TextUtils.isEmpty(path)) {
//                    path = FileUtils.getImagePath(getActivity(), url);
//                }
            }
            startIntent(uri, requestCode);
        }
    }

    private void startIntent(Uri uri, int requestCode) {
        String path = null;

        if (requestCode == REQUEST_CAPTURE_PICTURE || requestCode == REQUEST_VIDEO_CAPTURE) {
            path = getRealPathFromUri(uri);
        }

        if (path != null && !path.isEmpty()) {
            addResource(path);
        } else {
            Utilities.toast(getActivity(), getString(R.string.invalid_resource_url));
        }
    }

    private String getRealPathFromUri(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};

        try (Cursor cursor = requireActivity().getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(columnIndex);
            }
        }

        return "";
    }

    private void addResource(String path) {
        if (type == 0) {
            startActivity(new Intent(getActivity(), AddResourceActivity.class).putExtra("resource_local_url", path));
        } else {
            showAlert(getActivity(), path);
        }
    }
}
