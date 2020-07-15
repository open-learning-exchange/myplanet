package org.ole.planet.myplanet.base;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.datamanager.Service;
import org.ole.planet.myplanet.model.Download;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRemovedLog;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.CheckboxListView;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.DownloadUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;

import static android.content.Context.MODE_PRIVATE;
import static org.ole.planet.myplanet.ui.dashboard.DashboardActivity.MESSAGE_PROGRESS;

public abstract class BaseResourceFragment extends Fragment {
    public static SharedPreferences settings;
    public static String auth = "";
    static ProgressDialog prgDialog;
    public OnHomeItemClickListener homeItemClickListener;
    public RealmUserModel model;
    public Realm mRealm;
    public UserProfileDbHandler profileDbHandler;
    //    ArrayList<Integer> selectedItemsList = new ArrayList<>();
    CheckboxListView lv;
    View convertView;
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
                    .setNegativeButton("No", (dialogInterface, i) -> {
                        WifiManager wifi = (WifiManager) MainApplication.context.getSystemService(Context.WIFI_SERVICE);
                        if (wifi != null)
                            wifi.setWifiEnabled(false);
                    })
                    .show();
        }
    };
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

    protected void showDownloadDialog(final List<RealmMyLibrary> db_myLibrary) {
        new Service(MainApplication.context).isPlanetAvailable(new Service.PlanetAvailableListener() {
            @Override
            public void isAvailable() {
                if (!db_myLibrary.isEmpty()) {
                    LayoutInflater inflater = getLayoutInflater();
                    convertView = inflater.inflate(R.layout.my_library_alertdialog, null);
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                    alertDialogBuilder.setView(convertView).setTitle(R.string.download_suggestion);
                    alertDialogBuilder.setPositiveButton(R.string.download_selected, (dialogInterface, i) -> startDownload(DownloadUtils.downloadFiles(db_myLibrary, lv.getSelectedItemsList(), settings))).setNeutralButton(R.string.download_all, (dialogInterface, i) -> startDownload(DownloadUtils.downloadAllFiles(db_myLibrary, settings))).setNegativeButton(R.string.txt_cancel, null);
                    AlertDialog alertDialog = alertDialogBuilder.create();
                    createListView(db_myLibrary, alertDialog);
                    alertDialog.show();
                    (alertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(lv.getSelectedItemsList().size() > 0);
                }else{
                    Utilities.toast(requireContext(), "No resources to download");
                }
            }

            @Override
            public void notAvailable() {
                Utilities.toast(requireContext(), "Planet not available");

                Utilities.log("Planet not available");
            }
        });
    }

    public void showPendingSurveyDialog() {
        model = new UserProfileDbHandler(getActivity()).getUserModel();
        List<RealmSubmission> list = mRealm.where(RealmSubmission.class).equalTo("userId", model.getId()).equalTo("status", "pending").equalTo("type", "survey").findAll();
        if (list.size() == 0) {
            return;
        }
        HashMap<String, RealmStepExam> exams = RealmSubmission.getExamMap(mRealm, list);
        ArrayAdapter arrayAdapter = new ArrayAdapter(getActivity(), android.R.layout.simple_list_item_1, list) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null)
                    convertView = LayoutInflater.from(getActivity()).inflate(android.R.layout.simple_list_item_1, null);
                if (exams.containsKey(((RealmSubmission) getItem(position)).getParentId()))
                    ((TextView) convertView).setText(exams.get(list.get(position).getParentId()).getName());
                else {
                    ((TextView) convertView).setText("N/A");
                }
                return convertView;
            }
        };
        new AlertDialog.Builder(getActivity()).setTitle("Pending Surveys").setAdapter(arrayAdapter, (dialogInterface, i) -> AdapterMySubmission.openSurvey(homeItemClickListener, list.get(i).getId(), true)).setPositiveButton("Dismiss", null).show();
    }

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

    public void createListView(List<RealmMyLibrary> db_myLibrary, AlertDialog alertDialog) {
        lv = convertView.findViewById(R.id.alertDialog_listView);
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < db_myLibrary.size(); i++) {
            names.add(db_myLibrary.get(i).getTitle());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity().getBaseContext(), R.layout.rowlayout, R.id.checkBoxRowLayout, names);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        lv.setCheckChangeListener(() -> {
            (alertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(lv.getSelectedItemsList().size() > 0);
        });
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
        return getLibraryList(mRealm, settings.getString("userId", "--"));
    }

    public List<RealmMyLibrary> getLibraryList(Realm mRealm, String userId) {
        RealmResults<RealmMyLibrary> l = mRealm.where(RealmMyLibrary.class).equalTo("isPrivate", false).findAll();
        List<RealmMyLibrary> libList = new ArrayList<>();
        List<RealmMyLibrary> libraries = getLibraries(l);
        for (RealmMyLibrary item : libraries) {
            if (item.getUserId().contains(userId)) {
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
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
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

    public void removeFromShelf(RealmObject object) {
        if (object instanceof RealmMyLibrary) {
            RealmMyLibrary myObject = mRealm.where(RealmMyLibrary.class).equalTo("resourceId", ((RealmMyLibrary) object).getResource_id()).findFirst();
            myObject.removeUserId(model.getId());
            RealmRemovedLog.onRemove(mRealm, "resources", model.getId(), ((RealmMyLibrary) object).getResource_id());
            Utilities.toast(getActivity(), "Removed from myLibrary");
        } else {
            RealmMyCourse myObject = RealmMyCourse.getMyCourse(mRealm, ((RealmMyCourse) object).getCourseId());
            myObject.removeUserId(model.getId());
            RealmRemovedLog.onRemove(mRealm, "courses", model.getId(), ((RealmMyCourse) object).getCourseId());
            Utilities.toast(getActivity(), "Removed from myCourse");
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        registerReceiver();

    }


    public void showTagText(List<RealmTag> list, TextView tvSelected) {
        StringBuilder selected = new StringBuilder("Selected : ");
        for (RealmTag tags :
                list) {
            selected.append(tags.getName()).append(",");
        }
        tvSelected.setText(selected.subSequence(0, selected.length() - 1));
    }

}
