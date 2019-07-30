package org.ole.planet.myplanet.ui.library;


import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetDialog;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;


import org.jetbrains.annotations.NotNull;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import io.realm.Realm;
import retrofit2.http.Url;

import static android.app.Activity.RESULT_OK;

/**
 * A simple {@link Fragment} subclass.
 */
public class AddResourceFragment extends BottomSheetDialogFragment {

    int type = 0;

    public AddResourceFragment() {
        // Required empty public constructor
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
            FrameLayout bottomSheet = dialog.findViewById(android.support.design.R.id.design_bottom_sheet);
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
        v.findViewById(R.id.ll_record_audio).setOnClickListener(view -> dispatchRecordAudioIntent());
        v.findViewById(R.id.ll_capture_image).setOnClickListener(view -> takePhoto());
        v.findViewById(R.id.ll_draft).setOnClickListener(view -> openOleFolder());
        return v;
    }

    private void openOleFolder() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        Uri uri = Uri.parse(Utilities.SD_PATH);
        intent.setDataAndType(uri, "*/*");
        startActivityForResult(Intent.createChooser(intent, "Open folder"), 100);
    }

    static final int REQUEST_VIDEO_CAPTURE = 1;
    static final int REQUEST_RECORD_SOUND = 0;
    static final int REQUEST_CAPTURE_PICTURE = 2;

    private void dispatchTakeVideoIntent() {

        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
//        Uri photoURI = FileProvider.getUriForFile(getActivity(), , new File(Utilities.SD_PATH + "/video/" + UUID.randomUUID().toString() + ".mp4"));
        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(Utilities.SD_PATH + "/video/" + UUID.randomUUID().toString() + ".mp4")));
//        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        if (takeVideoIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }

    private void dispatchRecordAudioIntent() {

        Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(Utilities.SD_PATH + "/audio/" + UUID.randomUUID().toString() + ".mp3")));

        if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_RECORD_SOUND);
        } else {
            Utilities.toast(getActivity(), "Your phone does not have audio recorder app, please download and try again");
        }
    }

    File output;

    public void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File dir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        output = new File(dir, UUID.randomUUID().toString() + ".jpg");
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(output));
        startActivityForResult(intent, REQUEST_CAPTURE_PICTURE);
    }


    public String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
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
                path = getRealPathFromURI(getActivity(), url);
            }
            if (!TextUtils.isEmpty(path)) {
                if (type == 0) {
                    startActivity(new Intent(getActivity(), AddResourceActivity.class).putExtra("resource_local_url", path));
                } else {
                    showAlert(path);
                }
            } else {
                Utilities.toast(getActivity(), "Invalid resource url");
            }
        }

    }

    private void showAlert(String path) {

        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_my_personal, null);
        EditText etTitle = v.findViewById(R.id.et_title);
        EditText etDesc = v.findViewById(R.id.et_description);
        RealmUserModel realmUserModel = new UserProfileDbHandler(getActivity()).getUserModel();
        String userId = realmUserModel.getId();
        new AlertDialog.Builder(getActivity()).setTitle("Enter resource detail")
                .setView(v)
                .setPositiveButton("Save", (dialogInterface, i) -> {
                    String title = etTitle.getText().toString();
                    if (title.isEmpty()) {
                        Utilities.toast(getActivity(), "Title is required.");
                        return;
                    }
                    String desc = etDesc.getText().toString();
                    Realm realm = new DatabaseService(getActivity()).getRealmInstance();
                    realm.executeTransactionAsync(realm1 -> {
                        RealmMyPersonal myPersonal = realm1.createObject(RealmMyPersonal.class, UUID.randomUUID().toString());
                        myPersonal.setTitle(title);
                        myPersonal.setUserId(userId);
                        myPersonal.setPath(path);
                        myPersonal.setDate(new Date().getTime());
                        myPersonal.setDescription(desc);
                    }, () -> Utilities.toast(getActivity(), "Resource Saved"));

                }).setNegativeButton("Dismiss", null).show();
    }
}
