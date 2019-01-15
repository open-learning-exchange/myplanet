package org.ole.planet.myplanet.base;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.ole.planet.myplanet.model.Download;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.utilities.DownloadUtils;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static org.ole.planet.myplanet.ui.dashboard.DashboardActivity.MESSAGE_PROGRESS;

public abstract class BaseResourceFragment extends Fragment {
    public static SharedPreferences settings;
    static ProgressDialog prgDialog;
    public OnHomeItemClickListener homeItemClickListener;
    ArrayList<Integer> selectedItemsList = new ArrayList<>();
    ListView lv;
    View convertView;
    public UserProfileDbHandler profileDbHandler;
    public static String auth = ""; // Main Auth Session Token for any Online File Streaming/ Viewing -- Constantly Updating Every 15 mins

    protected void showDownloadDialog(final List<RealmMyLibrary> db_myLibrary) {
        if (!db_myLibrary.isEmpty()) {
            LayoutInflater inflater = getLayoutInflater();
            convertView = inflater.inflate(R.layout.my_library_alertdialog, null);
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
            alertDialogBuilder.setView(convertView).setTitle(R.string.download_suggestion);
            createListView(db_myLibrary);
            alertDialogBuilder.setPositiveButton(R.string.download_selected, (dialogInterface, i) -> startDownload(DownloadUtils.downloadFiles(db_myLibrary, selectedItemsList, settings))).setNeutralButton(R.string.download_all, (dialogInterface, i) -> startDownload(DownloadUtils.downloadAllFiles(db_myLibrary, settings))).setNegativeButton(R.string.txt_cancel, null).show();
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MESSAGE_PROGRESS) && prgDialog != null) {
                Download download = intent.getParcelableExtra("download");
                if (!download.isFailed()) {
                    setProgress(download);
                } else {
                    DialogUtils.showError(prgDialog, download.getMessage());
                }
            }
        }
    };


    public void startDownload(ArrayList urls) {
        if (!urls.isEmpty()) {
            prgDialog.show();
            Utilities.openDownloadService(getActivity(), urls);
        }
    }

    public void setProgress(Download download) {
        prgDialog.setProgress(download.getProgress());
        if (!TextUtils.isEmpty(download.getFileName())) {
            prgDialog.setTitle(download.getFileName());
        }
        if (download.isCompleteAll()) {
            DialogUtils.showError(prgDialog, "All files downloaded successfully");
            onDownloadComplete();
        }
    }


    public void onDownloadComplete() {
    }

    public void createListView(List<RealmMyLibrary> db_myLibrary) {
        lv = convertView.findViewById(R.id.alertDialog_listView);
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < db_myLibrary.size(); i++) {
            names.add(db_myLibrary.get(i).getTitle());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity().getBaseContext(), R.layout.rowlayout, R.id.checkBoxRowLayout, names);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((adapterView, view, i, l) -> {
            String itemSelected = ((TextView) view).getText().toString();
            if (selectedItemsList.contains(itemSelected)) {
                selectedItemsList.remove(itemSelected);
            } else {
                selectedItemsList.add(i);
            }
            Toast.makeText(getContext(), "Clicked on  : " + itemSelected + "Number " + i, Toast.LENGTH_SHORT).show();
        });
    }


    private void registerReceiver() {
        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(getActivity());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MESSAGE_PROGRESS);
        bManager.registerReceiver(broadcastReceiver, intentFilter);
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prgDialog = DialogUtils.getProgressDialog(getActivity());
        settings = getActivity().getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
        registerReceiver();
    }

}
