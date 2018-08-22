package org.ole.planet.takeout;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.HashMap;
import java.util.Map;

import im.delight.android.webview.AdvancedWebView;

public class WebFileViewing extends AppCompatActivity implements AdvancedWebView.Listener{

    AdvancedWebView webView;
    String url, auth, pref = "";
    SharedPreferences settings;
    Map<String, String> headers = new HashMap<String,String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_file_viewing);

        webView = findViewById(R.id.webView);
        Intent intent = getIntent();
        url = intent.getStringExtra("url");
        auth = intent.getStringExtra("auth");
        settings = WebFileViewing.this.getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);

        headers.put("Cookie",auth);
        headers.put("Accept","application/json");

        webView.setCookiesEnabled(true);
        webView.setThirdPartyCookiesEnabled(true);
        webView.setListener(this,this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().getAllowFileAccess();
//        webView.getSettings().set;
        webView.addHttpHeader("Cookie",auth);
        webView.addHttpHeader("Accept","application/json");

        pref = settings.getString("serverURL", "");
        pref += "/_session";
//        webView.setWebViewClient(new WebClientClass());
        Log.e("FIRST","FIRST");
        webView.loadUrl(url,headers);
//        webView.loadUrl(url,headers);
    }

    @Override
    public void onPageStarted(String urll, Bitmap favicon) {
//        if(urll.equals(pref)){
//            webView.addHttpHeader("Cookie",auth);
//            webView.addHttpHeader("Accept","application/json");
//            Log.e("URL #1",urll+" ;; URL #2"+url);
//            webView.loadUrl(url,headers);
//        }
    }
    int i = 0;
    @Override
    public void onPageFinished(String urll){
        if(urll.equals(pref)){
//            webView.addHttpHeader("Cookie",auth);
//            webView.addHttpHeader("Accept","application/json");
            Log.e("URL #1",urll+" ;; URL #2"+url);
//            webView.removeHttpHeader("Cookie");
//            webView.removeHttpHeader("Accept");
            webView.loadUrl(url,headers);
        }
        String doc="<iframe src='http://docs.google.com/viewer?url="+url+"&embedded=true' width='100%' height='100%' style='border: none;'></iframe>";
        if(urll.equals(url) && i<1){
            Log.e("SECOND TIME URL #1",urll+" ;; URL #2"+url);
//            webView.removeHttpHeader("Cookie");
//            webView.removeHttpHeader("Accept");
            webView.loadUrl("http://docs.google.com/gview?embedded=true&url=" +url, headers);
            i++;
        }
    }

    @Override
    public void onPageError(int errorCode, String description, String failingUrl) {
        Log.e("WEBVIEW ERROR",errorCode+" ;; "+description+" ;; "+failingUrl);
    }

    @Override
    public void onDownloadRequested(String url, String suggestedFilename, String mimeType, long contentLength, String contentDisposition, String userAgent) {

    }

    @Override
    public void onExternalPageRequest(String url) {

    }
    private class WebClientClass extends WebViewClient {
        @Override
        public void onLoadResource(WebView view, String urll) {
            view.loadUrl(url, headers);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            view.loadUrl(url, headers);
            return true;
        }
    }
}
