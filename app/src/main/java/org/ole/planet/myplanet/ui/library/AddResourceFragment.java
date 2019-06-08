package org.ole.planet.myplanet.ui.library;


import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetDialog;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.amosyuen.videorecorder.activity.FFmpegRecorderActivity;
import com.amosyuen.videorecorder.activity.params.FFmpegRecorderActivityParams;
import com.amosyuen.videorecorder.camera.CameraControllerI;
import com.amosyuen.videorecorder.recorder.common.ImageFit;
import com.amosyuen.videorecorder.recorder.common.ImageScale;
import com.amosyuen.videorecorder.recorder.common.ImageSize;
import com.amosyuen.videorecorder.recorder.params.EncoderParamsI;

import org.jetbrains.annotations.NotNull;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;

import static android.app.Activity.RESULT_OK;

/**
 * A simple {@link Fragment} subclass.
 */
public class AddResourceFragment extends BottomSheetDialogFragment {

    private static final int RECORD_VIDEO_REQUEST = 1000;

    public AddResourceFragment() {
        // Required empty public constructor
    }

    @NotNull
    @Override
    public Dialog onCreateDialog( Bundle savedInstanceState) {
        BottomSheetDialog bottomSheetDialog=(BottomSheetDialog)super.onCreateDialog(savedInstanceState);
        bottomSheetDialog.setOnShowListener(d -> {
            BottomSheetDialog dialog = (BottomSheetDialog) d;
            FrameLayout bottomSheet =  dialog .findViewById(android.support.design.R.id.design_bottom_sheet);
            BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            BottomSheetBehavior.from(bottomSheet).setSkipCollapsed(true);
            BottomSheetBehavior.from(bottomSheet).setHideable(true);
        });
        return bottomSheetDialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_add_resource, container, false);
        v.findViewById(R.id.ll_record_video).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startVideoActivity();
            }
        });
        return v;
    }

    public void startVideoActivity() {
        String videoFileName = "ole-video-" + Integer.toString(4) + ".mp4";
        File videoFile = new File(Utilities.SD_PATH + "/videos", videoFileName);

        String thumbnailFileName =
                "ole-thumb-" + Integer.toString(4) + ".jpg";
        File thumbFile = new File(Utilities.SD_PATH + "/videos", thumbnailFileName);
        FFmpegRecorderActivityParams.Builder paramsBuilder =
                FFmpegRecorderActivityParams.builder(getContext())
                        .setVideoOutputFileUri(videoFile)
                        .setVideoThumbnailOutputFileUri(thumbFile);

        paramsBuilder.recorderParamsBuilder()
                .setVideoSize(new ImageSize(640, 480))
                .setVideoCodec(EncoderParamsI.VideoCodec.H264)
                .setVideoBitrate(100000)
                .setVideoFrameRate(30)
                .setVideoImageFit(ImageFit.FILL)
                .setVideoImageScale(ImageScale.DOWNSCALE)
                .setShouldCropVideo(true)
                .setShouldPadVideo(true)
                .setVideoCameraFacing(CameraControllerI.Facing.BACK)
                .setAudioCodec(EncoderParamsI.AudioCodec.AAC)
                .setAudioSamplingRateHz(44100)
                .setAudioBitrate(100000)
                .setAudioChannelCount(2)
                .setOutputFormat(EncoderParamsI.OutputFormat.MP4);

        Intent intent = new Intent(getActivity(), FFmpegRecorderActivity.class);
        intent.putExtra(FFmpegRecorderActivity.REQUEST_PARAMS_KEY, paramsBuilder.build());
        startActivityForResult(intent, RECORD_VIDEO_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RECORD_VIDEO_REQUEST:
                switch (resultCode) {
                    case RESULT_OK:
                        Uri videoUri = data.getData();
                        Uri thumbnailUri =
                                data.getParcelableExtra(FFmpegRecorderActivity.RESULT_THUMBNAIL_URI_KEY);
                        Utilities.log("Video uri.. " + videoUri);
                        break;
                    case Activity.RESULT_CANCELED:
                        break;
                    case FFmpegRecorderActivity.RESULT_ERROR:
                        Exception error = (Exception)
                                data.getSerializableExtra(FFmpegRecorderActivity.RESULT_ERROR_PATH_KEY);
                        new AlertDialog.Builder(getActivity())
                                .setCancelable(false)
                                .setTitle("Unable to record video")
                                .setMessage(error.getLocalizedMessage())
                                .setPositiveButton(R.string.ok, null)
                                .show();
                        break;
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}
