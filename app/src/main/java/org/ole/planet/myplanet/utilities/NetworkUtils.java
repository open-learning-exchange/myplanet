package org.ole.planet.myplanet.utilities;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.ui.dashboard.DashboardFragment;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class NetworkUtils {
    public static boolean isWifiEnabled() {
        WifiManager mng = (WifiManager) MainApplication.context.getSystemService(Context.WIFI_SERVICE);
        return mng != null && mng.isWifiEnabled();
    }

    public static boolean isWifiConnected() {
        ConnectivityManager connManager = (ConnectivityManager) MainApplication.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi.isConnected();
    }

    public static boolean isWifiBluetoothEnabled() {
        return isBluetoothEnabled() || isWifiEnabled();
    }

    public static boolean isBluetoothEnabled() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    public static int getCurrentNetworkId(Context context) {
        int ssid = -1;
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {
                ssid = connectionInfo.getNetworkId();

            }

        }
        return ssid;
    }

    public static boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) MainApplication.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null) {
            return false;
        } else {
            return true;
        }
    }


    public static String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;
                return getAddress(nif);
            }
        } catch (Exception ex) {
        }
        return "";
    }


    private static String getAddress(NetworkInterface nif) throws Exception {
        byte[] macBytes = nif.getHardwareAddress();
        if (macBytes == null) {
            return "";
        }

        StringBuilder res1 = new StringBuilder();
        for (byte b : macBytes) {
            res1.append(String.format("%02X:", b));
        }

        if (res1.length() > 0) {
            res1.deleteCharAt(res1.length() - 1);
        }
        return res1.toString();
    }

    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return model.toUpperCase();
        }
        return manufacturer.toUpperCase() + " " + model;
    }
    public static String getCustomDeviceName(Context context) {
        return  context.getSharedPreferences(DashboardFragment.PREFS_NAME, Context.MODE_PRIVATE).getString("customDeviceName", "");
    }
}
