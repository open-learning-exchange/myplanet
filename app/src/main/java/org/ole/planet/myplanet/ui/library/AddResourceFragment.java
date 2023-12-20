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

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.jetbrains.annotations.NotNull;
import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.AlertSoundRecorderBinding;
import org.ole.planet.myplanet.databinding.FragmentAddResourceBinding;
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
    private FragmentAddResourceBinding fragmentAddResourceBinding;
    static final int REQUEST_VIDEO_CAPTURE = 1;
    static final int REQUEST_RECORD_SOUND = 0;
    static final int REQUEST_CAPTURE_PICTURE = 2;
    int type = 0;
    TextView tvTime;
    FloatingActionButton floatingActionButton;
    AudioRecorderService audioRecorderService;
    File output;
    private Uri photoURI;
    private Uri videoUri;

    public AddResourceFragment() {
    }

    public static void showAlert(Context context, String path) {
        View v = LayoutInflater.from(context).inflate(R.layout.alert_my_personal, null);
        EditText etTitle = v.findViewById(R.id.et_title);
        EditText etDesc = v.findViewById(R.id.et_description);
        RealmUserModel realmUserModel = new UserProfileDbHandler(MainApplication.context).getUserModel();
        String userId = realmUserModel.id;
        String userName = realmUserModel.name;
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
                myPersonal.title = title;
                myPersonal.userId = userId;
                myPersonal.userName = userName;
                myPersonal.path = path;
                myPersonal.date = new Date().getTime();
                myPersonal.description = desc;
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
        fragmentAddResourceBinding = FragmentAddResourceBinding.inflate(inflater, container, false);
        fragmentAddResourceBinding.llRecordVideo.setOnClickListener(view -> dispatchTakeVideoIntent());
        fragmentAddResourceBinding.llRecordAudio.setOnClickListener(view -> {
            showAudioRecordAlert();
        });
        fragmentAddResourceBinding.llCaptureImage.setOnClickListener(view -> takePhoto());
        fragmentAddResourceBinding.llDraft.setOnClickListener(view -> FileUtils.openOleFolder(this, 100));
        return fragmentAddResourceBinding.getRoot();
    }

    private void showAudioRecordAlert() {
        AlertSoundRecorderBinding alertSoundRecorderBinding = AlertSoundRecorderBinding.inflate(LayoutInflater.from(getActivity()));
        tvTime = alertSoundRecorderBinding.tvTime;
        floatingActionButton = alertSoundRecorderBinding.fabRecord;
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle("Record Audio")
                .setView(alertSoundRecorderBinding.getRoot())
                .setCancelable(false)
                .create();

        createAudioRecorderService(dialog);

        alertSoundRecorderBinding.fabRecord.setOnClickListener(view -> {
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
                audioStartIntent(outputFile);
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
        videoUri = createVideoFileUri(); // Assign the Uri to videoURI
        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);

        if (takeVideoIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }
    private Uri createVideoFileUri() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.TITLE, "Video_" + UUID.randomUUID().toString());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ole/video");
        }

        videoUri = requireActivity().getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        return videoUri;
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
            if (requestCode == REQUEST_CAPTURE_PICTURE) {
                uri = photoURI;
            } else if (requestCode == REQUEST_VIDEO_CAPTURE) {
                uri = videoUri;
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

    private void audioStartIntent(String path) {
        if (!TextUtils.isEmpty(path)) {
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
