package org.ole.planet.myplanet.ui.viewer;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.databinding.ActivityWebViewBinding;
import org.ole.planet.myplanet.utilities.Utilities;

public class WebViewActivity extends AppCompatActivity {
    private ActivityWebViewBinding activityWebViewBinding;
    boolean fromDeepLink = false;
    String link;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityWebViewBinding = ActivityWebViewBinding.inflate(getLayoutInflater());
        setContentView(activityWebViewBinding.getRoot());
        String title, source;
        String dataFromDeepLink = getIntent().getDataString();
        fromDeepLink = !TextUtils.isEmpty(dataFromDeepLink);

        title = getIntent().getStringExtra("title");
        link = getIntent().getStringExtra("link");

        clearCookie();
        if (!TextUtils.isEmpty(title)) {
            activityWebViewBinding.contentWebView.webTitle.setText(title);
        }
        
        activityWebViewBinding.contentWebView.pBar.setMax(100);
        activityWebViewBinding.contentWebView.pBar.setProgress(0);
        setListeners();
        activityWebViewBinding.contentWebView.wv.getSettings().setJavaScriptEnabled(true);
        activityWebViewBinding.contentWebView.wv.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        activityWebViewBinding.contentWebView.wv.loadUrl(link);
        activityWebViewBinding.contentWebView.finish.setOnClickListener(v -> finish());
        setWebClient();
    }

    private void setWebClient() {
        activityWebViewBinding.contentWebView.wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Utilities.log("Url " + url);
                if (url.endsWith("/eng/")) {
                    finish();
                }
                Uri i = Uri.parse(url);
                activityWebViewBinding.contentWebView.webSource.setText(i.getHost());
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
        } else {
            cookieManager.removeAllCookie();
        }
    }

    private void setListeners() {
        activityWebViewBinding.contentWebView.wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                (WebViewActivity.this).setProgress(newProgress);
                Utilities.log("Url " + view.getUrl());
                if (view.getUrl().endsWith("/eng/")) {
                    finish();
                }
                activityWebViewBinding.contentWebView.pBar.incrementProgressBy(newProgress);
                if (newProgress == 100 && activityWebViewBinding.contentWebView.pBar.isShown()) activityWebViewBinding.contentWebView.pBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                activityWebViewBinding.contentWebView.webTitle.setText(title);
                super.onReceivedTitle(view, title);
            }
        });
    }
}
