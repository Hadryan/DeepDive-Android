package com.nuvolect.deepdive.util;//

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.nuvolect.deepdive.license.AppSpecific;
import com.nuvolect.deepdive.license.LicensePersist;


/**
 * Dialog to show a WebView page. Links are all launched in a new browser tab.
 */
public class WebViewDialog {

    private static Activity m_act;

    public static boolean shouldDisplay(Context ctx){

        boolean nagOk = LicensePersist.timeToNagUser(ctx,
                AppSpecific.WHATS_NET_NAG_KEY, AppSpecific.WHATS_NEW_NAG_PERIOD);

        return nagOk && Util.checkInternetConnection(ctx);
    }

    public static void start(Activity act, String url) {

        m_act = act;

        AlertDialog.Builder builder = new AlertDialog.Builder(m_act);
        AlertDialog alert = builder.create();

        WebView webview = new WebView(m_act);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(new FlexibleWebViewClient());
        webview.loadUrl( url );

        alert.setView(webview.getRootView());
        alert.show();
    }

    /**
     * If the user has a browser, use it for external URLs.  If no browser than use
     * the webview as a browser.
     */
    static class FlexibleWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

            if( Util.isIntentAvailable(m_act, intent)){

                m_act.startActivity(intent);
                return true;
            }else{
                view.loadUrl(url); // Stay within this webview and load url
                return false;
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            super.onReceivedSslError(view, handler, error);

            handler.proceed();
        }
    }
}
