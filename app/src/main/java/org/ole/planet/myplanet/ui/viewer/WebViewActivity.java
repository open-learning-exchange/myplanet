package org.ole.planet.myplanet.ui.viewer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;


import org.ole.planet.myplanet.R;

public class WebViewActivity extends AppCompatActivity {

    ProgressBar pBar;
    boolean fromDeepLink = false;
    String link;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);
        String title, source;
        String dataFromDeepLink = getIntent().getDataString();
        fromDeepLink = !TextUtils.isEmpty(dataFromDeepLink);

        title = getIntent().getStringExtra("title");
        link = getIntent().getStringExtra("link");
        final WebView wv = findViewById(R.id.wv);
        final TextView tv_title = (findViewById(R.id.news_title));
        final TextView tv_source = (findViewById(R.id.news_source));
        if (!TextUtils.isEmpty(title)) {
            tv_title.setText(title);
        }

        pBar = findViewById(R.id.pBar);
        pBar.setMax(100);
        pBar.setProgress(0);

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                (WebViewActivity.this).setProgress(newProgress);
                pBar.incrementProgressBy(newProgress);
                if (newProgress == 100 && pBar.isShown()) pBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                tv_title.setText(title);
                super.onReceivedTitle(view, title);
            }
        });

        wv.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Uri i = Uri.parse(url);
                tv_source.setText(i.getHost());
            }
        });


        wv.getSettings().setJavaScriptEnabled(true);
        wv.loadUrl(link);
        findViewById(R.id.finish).setOnClickListener(v -> finish());

    }


}
