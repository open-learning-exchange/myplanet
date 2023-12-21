package org.ole.planet.myplanet.utilities;

import android.content.Context;
import android.content.Intent;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;

import org.ole.planet.myplanet.ui.viewer.WebViewActivity;

public class CustomClickableSpan extends ClickableSpan {
    private final String url;
    private final String title;
    private final Context context;

    public CustomClickableSpan(String url, String title, Context context) {
        this.url = url;
        this.title = title;
        this.context = context;
    }
    @Override
    public void onClick(@NonNull View widget) {
        openLinkInWebView(url, title);
    }

    private void openLinkInWebView(String link, String title) {
        if (link != null && !link.isEmpty()) {
            Intent webViewIntent = new Intent(context, WebViewActivity.class);
            webViewIntent.putExtra("title", title);
            webViewIntent.putExtra("link", link);
            context.startActivity(webViewIntent);
        } else {
            Utilities.toast(context, "Invalid link");
        }
    }
}
