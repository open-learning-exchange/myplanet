package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;


import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.Utilities;

public class WebViewActivity extends AppCompatActivity {

    ProgressBar pBar;
    boolean fromDeepLink = false;
    String link;
    WebView wv;
    TextView tv_title, tv_source;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);
        String title, source;
        String dataFromDeepLink = getIntent().getDataString();
        fromDeepLink = !TextUtils.isEmpty(dataFromDeepLink);

        title = getIntent().getStringExtra("title");
        link = getIntent().getStringExtra("link");
        wv = findViewById(R.id.wv);

        clearCookie();
        tv_title = (findViewById(R.id.web_title));
        tv_source = (findViewById(R.id.web_source));
        if (!TextUtils.isEmpty(title)) {
            tv_title.setText(title);
        }

        pBar = findViewById(R.id.pBar);
        pBar.setMax(100);
        pBar.setProgress(0);
        setListeners();
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        wv.loadUrl(link);
        findViewById(R.id.finish).setOnClickListener(v -> finish());
        setWebClient();
    }

    private void setWebClient() {
//        wv.setWebChromeClient(new WebChromeClient());

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Utilities.log("Url " + url);
                if (url.endsWith("/eng/")) {
                    finish();
                }
                Uri i = Uri.parse(url);
                tv_source.setText(i.getHost());
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
            }
        });
    }


    private void clearCookie() {
        CookieSyncManager.createInstance(this);
        CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(aBoolean -> {
            });
        }else{
            cookieManager.removeAllCookie();
        }
    }

    private void setListeners() {
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                (WebViewActivity.this).setProgress(newProgress);
                Utilities.log("Url " + view.getUrl() );
                if (view.getUrl().endsWith("/eng/")) {
                    finish();
                }
                pBar.incrementProgressBy(newProgress);
                if (newProgress == 100 && pBar.isShown()) pBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                tv_title.setText(title);
                super.onReceivedTitle(view, title);
            }
        });


    }


}
