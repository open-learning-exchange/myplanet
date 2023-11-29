package org.ole.planet.myplanet.datamanager;

import com.google.gson.GsonBuilder;

import java.lang.reflect.Modifier;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    public static final String BASE_URL = "https://vi.media.mit.edu/";
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(1, TimeUnit.MINUTES).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build();

        if (retrofit == null) {
            retrofit = new Retrofit.Builder().baseUrl(BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC).serializeNulls().create())).build();
        }
        return retrofit;
    }

    public static WebSocket createWebSocket(String url, WebSocketListener listener) {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url(url).build();
        return client.newWebSocket(request, listener);
    }
}