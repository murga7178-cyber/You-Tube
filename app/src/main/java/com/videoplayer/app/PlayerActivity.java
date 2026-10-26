package com.videoplayer.app;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** वीडियो प्लेयर: साफ़ वीडियो + नीचे अपनी सीक बार। असली पेज assets/player.html में है। */
public class PlayerActivity extends Activity {

    private static final int BAR_DP = 52; // player.html की नीचे वाली बार की ऊँचाई

    private FrameLayout root;
    private FrameLayout holder;
    private FrameLayout card;
    private WebView web;
    private ProgressBar spinner;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private VideoLink link;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        link = VideoLink.parse(getIntent().getStringExtra("url"));
        if (link == null) {
            finish();
            return;
        }

        String page;
        try {
            page = readAsset("player.html")
                    .replace("__ID__", link.id == null ? "" : link.id)
                    .replace("__LIST__", link.list == null ? "" : link.list)
                    .replace("__START__", String.valueOf(link.start));
        } catch (Exception e) {
            finish();
            return;
        }

        getWindow().setStatusBarColor(0xFF222222);
        getWindow().setNavigationBarColor(0xFF000000);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF222222, 0xFF000000}));

        // ---- ऊपर की पट्टी: ✕ बटन + नाम ----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(18);
        close.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x33FFFFFF);
        close.setBackground(circle);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = new TextView(this);
        title.setText("वीडियो प्लेयर");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = dp(12);
        top.addView(title, tp);

        column.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- गोल कोनों वाला कार्ड (वीडियो + नीचे सीक बार) ----
        holder = new FrameLayout(this);
        column.addView(holder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        card = new FrameLayout(this);
        card.setBackgroundColor(Color.BLACK);
        card.setElevation(dp(8));
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });
        card.setClipToOutline(true);
        holder.addView(card);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        card.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(0xFFFF0000));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(40), dp(40));
        sp.gravity = Gravity.CENTER;
        card.addView(spinner, sp);
        spinner.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (spinner != null) spinner.setVisibility(View.GONE);
            }
        }, 8000);

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        applyLayout();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.addJavascriptInterface(new NativeBridge(), "Native");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                if (customCallback != null) customCallback.onCustomViewHidden();
                customCallback = null;
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        });

        web.loadDataWithBaseURL("https://videoplayer.app/", page, "text/html", "utf-8", null);
    }

    private class NativeBridge {
        @JavascriptInterface
        public void ready() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (spinner != null) spinner.setVisibility(View.GONE);
                }
            });
        }
    }

    private String readAsset(String name) throws Exception {
        InputStream in = getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    /** पोर्ट्रेट में 16:9 वीडियो + नीचे बार, लैंडस्केप में पूरी जगह */
    private void applyLayout() {
        boolean land = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        FrameLayout.LayoutParams lp;
        if (land) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins(dp(8), 0, dp(8), dp(8));
        } else {
            int w = getResources().getDisplayMetrics().widthPixels - dp(24);
            lp = new FrameLayout.LayoutParams(w, w * 9 / 16 + dp(BAR_DP));
            lp.setMargins(dp(12), dp(4), dp(12), 0);
            lp.gravity = Gravity.TOP;
        }
        card.setLayoutParams(lp);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (card != null) applyLayout();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (customView != null) {
            web.getWebChromeClient().onHideCustomView();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
