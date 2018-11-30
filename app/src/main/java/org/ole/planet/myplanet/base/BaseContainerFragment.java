package org.ole.planet.myplanet.base;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.Rating;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatRatingBar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.AuthSessionUpdater;
import org.ole.planet.myplanet.CSVViewerActivity;
import org.ole.planet.myplanet.DashboardFragment;
import org.ole.planet.myplanet.Data.Download;
import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_examQuestion;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.DownloadFiles;
import org.ole.planet.myplanet.ExoPlayerVideo;
import org.ole.planet.myplanet.ImageViewerActivity;
import org.ole.planet.myplanet.MarkdownViewerActivity;
import org.ole.planet.myplanet.PDFReaderActivity;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.SyncActivity;
import org.ole.planet.myplanet.TextFileViewerActivity;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.courses.AdapterCourses;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.survey.SendSurveyFragment;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

import static android.content.Context.MODE_PRIVATE;
import static org.ole.planet.myplanet.Dashboard.MESSAGE_PROGRESS;

public abstract class BaseContainerFragment extends BaseResourceFragment {
    public TextView timesRated, rating;
    public AppCompatRatingBar ratingBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        profileDbHandler = new UserProfileDbHandler(getActivity());
        AuthSessionUpdater.timerSendPostNewAuthSessionID(settings);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    public void setRatings(JsonObject object) {
        if (object != null) {
            AdapterCourses.showRating(object, rating, timesRated, ratingBar);
        }
    }

    public void initRatingView(View v) {
        timesRated = v.findViewById(R.id.times_rated);
        rating = v.findViewById(R.id.tv_rating);
        ratingBar = v.findViewById(R.id.rating_bar);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnHomeItemClickListener) {
            homeItemClickListener = (OnHomeItemClickListener) context;
        }
    }

    public void openIntent(realm_myLibrary items, Class typeClass) {
        Intent fileOpenIntent = new Intent(getActivity(), typeClass);
        fileOpenIntent.putExtra("TOUCHED_FILE", items.getResourceLocalAddress());
        startActivity(fileOpenIntent);
    }


    public void openResource(realm_myLibrary items) {

        if (items.getResourceOffline() != null && items.getResourceOffline()) {
            profileDbHandler.setResourceOpenCount(items);
            openFileType(items, "offline");
        } else if (FileUtils.getFileExtension(items.getResourceLocalAddress()).equals("mp4")) {
            openFileType(items, "online");
        } else {
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add(Utilities.getUrl(items, settings));
            startDownload(arrayList);
        }
    }


    public void checkFileExtension(realm_myLibrary items) {
        String filenameArray[] = items.getResourceLocalAddress().split("\\.");
        String extension = filenameArray[filenameArray.length - 1];
        switch (extension) {
            case "pdf":
                openIntent(items, PDFReaderActivity.class);
                break;
            case "bmp":
            case "gif":
            case "jpg":
            case "png":
            case "webp":
                openIntent(items, ImageViewerActivity.class);
                break;
            case "mp4":
                playVideo("offline", items);
            default:
                checkMoreFileExtensions(extension, items);
                break;
        }
    }

    public void checkMoreFileExtensions(String extension, realm_myLibrary items) {
        switch (extension) {
            case "txt":
                openIntent(items, TextFileViewerActivity.class);
                break;
            case "md":
                openIntent(items, MarkdownViewerActivity.class);
                break;
            case "csv":
                openIntent(items, CSVViewerActivity.class);
                break;
            default:
                Toast.makeText(getActivity(), "This file type is currently unsupported", Toast.LENGTH_LONG).show();
                break;
        }

    }

    public void openFileType(final realm_myLibrary items, String videotype) {
        if (FileUtils.getFileExtension(items.getResourceLocalAddress()).equals("mp4")) {
            playVideo(videotype, items);
        } else {
            checkFileExtension(items);
        }
    }

    public void setAuthSession(Map<String, List<String>> responseHeader) {
        String headerauth[] = responseHeader.get("Set-Cookie").get(0).split(";");
        auth = headerauth[0];
    }

    public void playVideo(String videoType, final realm_myLibrary items) {
        Intent intent = new Intent(getActivity(), ExoPlayerVideo.class);
        Bundle bundle = new Bundle();
        bundle.putString("videoType", videoType);
        if (videoType.equals("online")) {
            bundle.putString("videoURL", "" + items.getResourceRemoteAddress());
            Log.e("AUTH", "" + auth);
            bundle.putString("Auth", "" + auth);
        } else if (videoType.equals("offline")) {
            bundle.putString("videoURL", "" + Uri.fromFile(new File("" + Utilities.getSDPathFromUrl(items.getResourceRemoteAddress()))));
            bundle.putString("Auth", "");
        }
        intent.putExtras(bundle);
        startActivity(intent);
    }
}
