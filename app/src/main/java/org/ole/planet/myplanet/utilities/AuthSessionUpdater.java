package org.ole.planet.myplanet.utilities;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class AuthSessionUpdater {

    // Updates Auth Session Token every 15 mins to prevent Timing Out

    public interface AuthCallback {
        void setAuthSession(Map<String, List<String>> responseHeader);

        void onError(String s);
    }

    SharedPreferences settings;
    AuthCallback callback;

    public AuthSessionUpdater(AuthCallback callback, SharedPreferences settings) {
        this.callback = callback;
        this.settings = settings;
        timerSendPostNewAuthSessionID();
    }

    public void timerSendPostNewAuthSessionID() {
        Timer timer = new Timer();
        TimerTask hourlyTask = new TimerTask() {
            @Override
            public void run() {
                sendPost(settings);
            }
        };
        timer.schedule(hourlyTask, 0, 1000 * 60 * 15);
    }

    // sendPost() - Meant to get New AuthSession Token for viewing Online resources such as Video, and basically any file.
    // It creates a session of about 20mins after which a new AuthSession Token will be needed.
    // During these 20mins items.getResourceRemoteAddress() will work in obtaining the files necessary.
    public void sendPost(final SharedPreferences settings) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) getSessionUrl(settings).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    os.writeBytes(getJsonObject(settings).toString());

                    os.flush();
                    os.close();

                    callback.setAuthSession(conn.getHeaderFields());
                    conn.disconnect();
                } catch (Exception e) {
                    callback.onError(e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }

    private static JSONObject getJsonObject(SharedPreferences settings) {
        try {
            JSONObject jsonParam = new JSONObject();
            jsonParam.put("name", settings.getString("url_user", ""));
            jsonParam.put("password", settings.getString("url_pwd", ""));
            return jsonParam;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static URL getSessionUrl(SharedPreferences settings) {
        try {
            String pref = Utilities.getUrl();
            pref += "/_session";
            URL SERVER_URL = new URL(pref);
            Utilities.log("Url " + SERVER_URL);
            return SERVER_URL;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
