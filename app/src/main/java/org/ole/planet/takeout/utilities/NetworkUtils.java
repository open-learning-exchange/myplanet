package org.ole.planet.takeout.utilities;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.WifiManager;

import org.ole.planet.takeout.MainApplication;

public class NetworkUtils {
    public static boolean isWifiEnabled() {
        WifiManager mng = (WifiManager) MainApplication.context.getSystemService(Context.WIFI_SERVICE);
        return mng != null && mng.isWifiEnabled();
    }

    public static boolean isWifiBluetoothEnabled(){
        return isBluetoothEnabled() || isWifiEnabled();
    }
    public static boolean isBluetoothEnabled() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }
}
