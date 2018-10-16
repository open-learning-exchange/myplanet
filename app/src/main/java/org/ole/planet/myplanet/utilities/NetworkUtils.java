package org.ole.planet.myplanet.utilities;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.WifiManager;

import org.ole.planet.myplanet.MainApplication;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class NetworkUtils {
    public static boolean isWifiEnabled() {
        WifiManager mng = (WifiManager) MainApplication.context.getSystemService(Context.WIFI_SERVICE);
        return mng != null && mng.isWifiEnabled();
    }

    public static boolean isWifiBluetoothEnabled() {
        return isBluetoothEnabled() || isWifiEnabled();
    }

    public static boolean isBluetoothEnabled() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    public static String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

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
        } catch (Exception ex) {
            //handle exception
        }
        return "";
    }
}
