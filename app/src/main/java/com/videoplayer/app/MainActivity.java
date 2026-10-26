package com.videoplayer.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube (m.youtube.com) खोलता है (वाइट / लाइट मोड में)। किसी भी वीडियो / थंबनेल / Shorts पर
 * क्लिक होते ही उसका लिंक पकड़कर PlayerActivity (वीडियो प्लेयर) में चला देता है।
 */
public class MainActivity extends Activity {

    private static final String HOME = "https://m.youtube.com";

    // YouTube पेज के अंदर चलने वाली स्क्रिप्ट: वीडियो लिंक पर क्लिक पकड़ती है
    private static final String HOOK =
            "(function(){"
            + "if(window.__vpHooked)return;window.__vpHooked=true;"
            + "window.addEventListener('click',function(e){"
            + "var a=e.target&&e.target.closest?e.target.closest('a[href]'):null;"
            + "if(!a)return;"
            + "var h=a.hostname,p=a.pathname;"
            + "var yt=(h==='www.youtube.com'||h==='m.youtube.com'||h==='youtube.com');"
            + "var ok=(yt&&(p==='/watch'||p.indexOf('/shorts/')===0||p.indexOf('/live/')===0||p==='/playlist'))||h==='youtu.be';"
            + "if(!ok)return;"
            + "e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();"
            + "try{var v=document.querySelector('video');if(v)v.pause();}catch(x){}"
            + "VideoBridge.play(a.href);"
            + "},true);"
            + "})();";

    private WebView web;

    /** फोन डार्क मोड में हो तब भी यह ऐप (और YouTube) हमेशा वाइट मोड में रहे */
    @Override
    protected void attachBaseContext(Context base) {
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        super.attachBaseContext(base.createConfigurationContext(c));
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setForceDark(WebSettings.FORCE_DARK_OFF);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "VideoBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (request.isForMainFrame() && VideoLink.parse(url) != null) {
                    openPlayer(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                view.evaluateJavascript(HOOK, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(HOOK, null);
            }
        });

        web.loadUrl(HOME);
        handleShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShare(intent);
    }

    /** YouTube ऐप / ब्राउज़र से "Share" करने पर लिंक सीधे प्लेयर में चलता है */
    private void handleShare(Intent in) {
        if (in == null || !Intent.ACTION_SEND.equals(in.getAction())) return;
        String text = in.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) return;
        Matcher m = Pattern.compile("https?://\\S+").matcher(text);
        if (m.find()) {
            String url = m.group();
            if (VideoLink.parse(url) != null) openPlayer(url);
        }
    }

    private void openPlayer(String url) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("url", url);
        startActivity(i);
    }

    private class Bridge {
        @JavascriptInterface
        public void play(final String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (VideoLink.parse(url) != null) openPlayer(url);
                }
            });
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
