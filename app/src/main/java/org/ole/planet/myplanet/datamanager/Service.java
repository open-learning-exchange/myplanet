package org.ole.planet.myplanet.datamanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.callback.SuccessListener;
import org.ole.planet.myplanet.model.DocumentResponse;
import org.ole.planet.myplanet.model.MyPlanet;
import org.ole.planet.myplanet.model.RealmCommunity;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.model.Rows;
import org.ole.planet.myplanet.service.UploadToShelfService;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.AndroidDecrypter;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.NetworkUtils;
import org.ole.planet.myplanet.utilities.Sha256Utils;
import org.ole.planet.myplanet.utilities.Utilities;
import org.ole.planet.myplanet.utilities.VersionUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static org.ole.planet.myplanet.utilities.Constants.KEY_UPGRADE_MAX;

public class Service {
    private Context context;
    private SharedPreferences preferences;

    public Service(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }


    public void healthAccess(SuccessListener listener) {
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        retrofitInterface.healthAccess(Utilities.getHealthAccessUrl(preferences)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.code() == 200) {
                    listener.onSuccess("Successfully synced");
                } else {
                    listener.onSuccess("");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                listener.onSuccess("");
            }
        });
    }

    public void checkCheckSum(ChecksumCallback callback, String path) {
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        retrofitInterface.getChecksum(Utilities.getChecksumUrl(preferences)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.code() == 200) {
                    try {
                        String checksum = response.body().string();
                        if (TextUtils.isEmpty(checksum)) {
                            File f = FileUtils.getSDPathFromUrl(path);
                            if (f.exists()) {
                                String sha256 = new Sha256Utils().getCheckSumFromFile(f);
                                if (checksum.contains(sha256)) {
                                    callback.onMatch();
                                    return;
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                callback.onFail();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onFail();
            }
        });
    }

    public void checkVersion(CheckVersionCallback callback, SharedPreferences settings) {
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        if (settings.getString("couchdbURL", "").isEmpty()) {
            callback.onError("Config not awailable.", true);
            return;
        }
        retrofitInterface.checkVersion(Utilities.getUpdateUrl(settings)).enqueue(new Callback<MyPlanet>() {
            @Override
            public void onResponse(Call<MyPlanet> call, retrofit2.Response<MyPlanet> response) {
                preferences.edit().putInt("LastWifiID", NetworkUtils.getCurrentNetworkId(context)).commit();
                if (response.body() != null) {
                    MyPlanet p = response.body();
                    preferences.edit().putString("versionDetail", new Gson().toJson(response.body())).commit();
                    retrofitInterface.getApkVersion(Utilities.getApkVersionUrl(settings)).enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                            String responses = null;
                            try {
                                responses = new Gson().fromJson(response.body().string(), String.class);
                                if (responses.isEmpty()) {
                                    callback.onError("Planet up to date", false);
                                    return;
                                }
                                String vsn = responses.replaceAll("v", "");
                                vsn = vsn.replaceAll("\\.", "");
                                int apkVersion = Integer.parseInt(vsn.startsWith("0") ? vsn.replace("0", "") : vsn);
                                int currentVersion = VersionUtils.getVersionCode(context);
                                if (Constants.showBetaFeature(KEY_UPGRADE_MAX, context) && p.getLatestapkcode() > currentVersion) {
                                    callback.onUpdateAvailable(p, false);
                                    return;
                                }
                                if (apkVersion > currentVersion) {
                                    callback.onUpdateAvailable(p, currentVersion >= p.getMinapkcode());
                                    return;
                                }
                                if (currentVersion < p.getMinapkcode() && apkVersion < p.getMinapkcode()) {
                                    callback.onUpdateAvailable(p, true);
                                } else {
                                    callback.onError("Planet up to date", false);
                                }

                            } catch (Exception e) {
                                Log.e("Error", e.getLocalizedMessage());
                                callback.onError("New apk version required  but not found on server - Contact admin", false);
                            }
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call, Throwable t) {

                        }
                    });

                } else {
                    callback.onError("Version not found", true);
                }
            }

            @Override
            public void onFailure(Call<MyPlanet> call, Throwable t) {
                t.printStackTrace();
                callback.onError("Connection failed.", true);
            }
        });
    }



    public void isPlanetAvailable(PlanetAvailableListener callback) {
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        retrofitInterface.isPlanetAvailable(Utilities.getUpdateUrl(preferences)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (callback != null && response.code() == 200) {
                    callback.isAvailable();
                } else {
                    callback.notAvailable();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.notAvailable();
            }
        });
    }

    public void becomeMember(Realm realm, JsonObject obj, CreateUserCallback callback) {
        isPlanetAvailable(new PlanetAvailableListener() {
            public void isAvailable() {
                ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
                retrofitInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/_users/org.couchdb.user:" + obj.get("name").getAsString()).enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        if (response.body() != null && response.body().has("_id")) {
                            callback.onSuccess("Unable to create user, user already exists");
                        } else {
                            retrofitInterface.putDoc(null, "application/json", Utilities.getUrl() + "/_users/org.couchdb.user:" + obj.get("name").getAsString(), obj).enqueue(new Callback<JsonObject>() {
                                @Override
                                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                                    if (response.body() != null && response.body().has("id")) {
                                        retrofitInterface.putDoc(null, "application/json", Utilities.getUrl() + "/shelf/org.couchdb.user:" + obj.get("name").getAsString(), new JsonObject());
                                        saveUserToDb(realm, response.body().get("id").getAsString(), obj, callback);
//                                            callback.onSuccess("User created successfully");
                                    } else {
                                        callback.onSuccess("Unable to create user");
                                    }
                                }

                                @Override
                                public void onFailure(Call<JsonObject> call, Throwable t) {
                                    callback.onSuccess("Unable to create user");
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        callback.onSuccess("Unable to create user");
                    }
                });
            }

            public void notAvailable() {
                SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);
                if (RealmUserModel.isUserExists(realm, obj.get("name").getAsString())) {
                    callback.onSuccess("User already exists");
                    return;
                }
                if (!realm.isInTransaction())
                    realm.beginTransaction();
                RealmUserModel model = RealmUserModel.populateUsersTable(obj, realm, settings);
                String keyString = AndroidDecrypter.generateKey();
                String iv = AndroidDecrypter.generateIv();
                model.setKey(keyString);
                model.setIv(iv);
                realm.commitTransaction();
                if (model != null) {
                    Utilities.toast(MainApplication.context, "Not connected to planet , created user offline.");
                    callback.onSuccess("Not connected to planet , created user offline.");
                }
            }
        });
    }


    private void saveUserToDb(Realm realm, String id, JsonObject obj, CreateUserCallback callback) {
        SharedPreferences settings = MainApplication.context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE);

        realm.executeTransactionAsync(realm1 -> {
            ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
            try {
                Response<JsonObject> res = retrofitInterface.getJsonObject(Utilities.getHeader(), Utilities.getUrl() + "/_users/" + id).execute();
                if (res.body() != null) {
                    RealmUserModel model = RealmUserModel.populateUsersTable(res.body(), realm1, settings);
                    if (model != null)
                        new UploadToShelfService(MainApplication.context).saveKeyIv(retrofitInterface, model, obj);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, () -> callback.onSuccess("User created successfully"), error -> {
            error.printStackTrace();
            callback.onSuccess("Unable to save user please sync");
        });
    }


    public void syncPlanetServers(Realm realm, SuccessListener callback) {
        ApiInterface retrofitInterface = ApiClient.getClient().create(ApiInterface.class);
        retrofitInterface.getJsonObject("", "https://planet.earth.ole.org/db/communityregistrationrequests/_all_docs?include_docs=true").enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.body() != null) {
                    JsonArray arr = JsonUtils.getJsonArray("rows", response.body());
                    realm.executeTransactionAsync(realm1 -> {
                        realm1.delete(RealmCommunity.class);
                        for (JsonElement j : arr) {
                            JsonObject jsonDoc = j.getAsJsonObject();
                            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc);
                            String id = JsonUtils.getString("_id", jsonDoc);
                            RealmCommunity community = realm1.createObject(RealmCommunity.class, id);
                            if (JsonUtils.getString("name", jsonDoc).equals("vi")) {
                                community.setWeight(0);
                            }
                            community.setLocalDomain(JsonUtils.getString("localDomain", jsonDoc));
                            community.setName(JsonUtils.getString("name", jsonDoc));
                            community.setParentDomain(JsonUtils.getString("parentDomain", jsonDoc));
                            community.setRegistrationRequest(JsonUtils.getString("registrationRequest", jsonDoc));
                        }

                    });

                    callback.onSuccess("Server sync successfully");
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                callback.onSuccess("Unable to connect to planet earth");

            }
        });
    }

    public interface CheckVersionCallback {
        void onUpdateAvailable(MyPlanet info, boolean cancelable);

        void onCheckingVersion();

        void onError(String msg, boolean blockSync);
    }

    public interface CreateUserCallback {
        void onSuccess(String message);
    }

    public interface ChecksumCallback {
        void onMatch();

        void onFail();
    }

    public interface PlanetAvailableListener {
        void isAvailable();

        void notAvailable();
    }
}
