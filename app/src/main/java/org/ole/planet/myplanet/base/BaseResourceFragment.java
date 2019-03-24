package org.ole.planet.myplanet.base;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.datamanager.Service;
import org.ole.planet.myplanet.model.Download;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.CheckboxListView;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.DownloadUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

import static android.content.Context.MODE_PRIVATE;
import static org.ole.planet.myplanet.ui.dashboard.DashboardActivity.MESSAGE_PROGRESS;

public abstract class BaseResourceFragment extends Fragment {
    public static SharedPreferences settings;
    static ProgressDialog prgDialog;
    public OnHomeItemClickListener homeItemClickListener;

    //    ArrayList<Integer> selectedItemsList = new ArrayList<>();
    CheckboxListView lv;
    View convertView;

    public UserProfileDbHandler profileDbHandler;
    public static String auth = "";
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Utilities.log("Broad cast received");
            showDownloadDialog(getLibraryList(new DatabaseService(context).getRealmInstance()));
        }
    };

    BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            new AlertDialog.Builder(getActivity())
                    .setMessage("Do you want to stay online?")
                    .setPositiveButton("Yes", null)
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            WifiManager wifi = (WifiManager) MainApplication.context.getSystemService(Context.WIFI_SERVICE);
                            if (wifi != null)
                                wifi.setWifiEnabled(false);
                        }
                    })
                    .show();
        }
    };

    protected void showDownloadDialog(final List<RealmMyLibrary> db_myLibrary) {
        new Service(MainApplication.context).isPlanetAvailable(new Service.PlanetAvailableListener() {
            @Override
            public void isAvailable() {
                if (!db_myLibrary.isEmpty()) {
                    LayoutInflater inflater = getLayoutInflater();
                    convertView = inflater.inflate(R.layout.my_library_alertdialog, null);
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                    alertDialogBuilder.setView(convertView).setTitle(R.string.download_suggestion);
                    createListView(db_myLibrary);
                    alertDialogBuilder.setPositiveButton(R.string.download_selected, (dialogInterface, i) -> startDownload(DownloadUtils.downloadFiles(db_myLibrary, lv.getSelectedItemsList(), settings))).setNeutralButton(R.string.download_all, (dialogInterface, i) -> startDownload(DownloadUtils.downloadAllFiles(db_myLibrary, settings))).setNegativeButton(R.string.txt_cancel, null).show();
                }
            }

            @Override
            public void notAvailable() {
                Utilities.log("Planet not available");
            }
        });


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
        new Service(getActivity()).isPlanetAvailable(new Service.PlanetAvailableListener() {
            @Override
            public void isAvailable() {
                if (!urls.isEmpty()) {
                    prgDialog.show();
                    Utilities.openDownloadService(getActivity(), urls);
                }
            }

            @Override
            public void notAvailable() {
                Utilities.toast(getActivity(), "Device not connected to planet.");
            }
        });

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
    }


    private void registerReceiver() {
        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(getActivity());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MESSAGE_PROGRESS);
        bManager.registerReceiver(broadcastReceiver, intentFilter);
        IntentFilter intentFilter2 = new IntentFilter();
        intentFilter2.addAction("ACTION_NETWORK_CHANGED");
        LocalBroadcastManager.getInstance(MainApplication.context).registerReceiver(receiver, intentFilter2);
        IntentFilter intentFilter3 = new IntentFilter();
        intentFilter3.addAction("SHOW_WIFI_ALERT");
        LocalBroadcastManager.getInstance(MainApplication.context).registerReceiver(stateReceiver, intentFilter3);
    }

    public List<RealmMyLibrary> getLibraryList(Realm mRealm) {
        RealmResults<RealmMyLibrary> l = mRealm.where(RealmMyLibrary.class).findAll();
        List<RealmMyLibrary> libList = new ArrayList<>();
        List<RealmMyLibrary> libraries = getLibraries(l);
        for (RealmMyLibrary item : libraries) {
            if (item.getUserId().contains(settings.getString("userId", "--"))) {
                libList.add(item);
            }
        }
        return libList;
    }

    private List<RealmMyLibrary> getLibraries(RealmResults<RealmMyLibrary> l) {
        List<RealmMyLibrary> libraries = new ArrayList<>();
        for (RealmMyLibrary lib : l) {
                if (lib.needToUpdate()) {
                    libraries.add(lib);
                }
        }
        return libraries;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prgDialog = DialogUtils.getProgressDialog(getActivity());
        settings = getActivity().getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);

    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(receiver);
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(broadcastReceiver);
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(stateReceiver);

    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver();

    }
}
