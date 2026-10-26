package com.videoplayer.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube (वाइट मोड) + उसके ऊपर वीडियो प्लेयर।
 * वीडियो पर क्लिक -> प्लेयर बड़ा खुलता है। बैक दबाने पर प्लेयर नीचे छोटा (मिनी) होकर चलता रहता है,
 * मिनी पर टैप करने पर फिर बड़ा हो जाता है।
 */
public class MainActivity extends Activity {

    private static final String HOME = "https://m.youtube.com";

    private static final int MODE_NONE = 0, MODE_FULL = 1, MODE_MINI = 2;
    private static final int MINI_W = 128;  // dp, छोटे वीडियो की चौड़ाई
    private static final int MINI_CARD_H = 72; // dp
    private static final int MINI_H = 88;   // dp, पूरी मिनी पट्टी
    private static final int TOP_H = 56;    // dp, बड़े प्लेयर की ऊपर वाली पट्टी
    private static final int BAR_DP = 96;   // dp, player.html की नीचे की बार (2 पंक्तियाँ)

    // YouTube पेज के अंदर चलने वाली स्क्रिप्ट: वीडियो लिंक पर क्लिक पकड़ती है
    private static final String HOOK =
            "(function(){"
            + "if(window.__vpHooked)return;window.__vpHooked=true;"
            + "try{var mm=window.matchMedia;window.matchMedia=function(q){if(/prefers-color-scheme/.test(q)){return {matches:/light/.test(q),media:q,onchange:null,addListener:function(){},removeListener:function(){},addEventListener:function(){},removeEventListener:function(){},dispatchEvent:function(){return false}};}return mm.call(window,q);};}catch(x){}"
            + "try{var de=document.documentElement;var fix=function(){if(de.hasAttribute('dark'))de.removeAttribute('dark');};fix();new MutationObserver(fix).observe(de,{attributes:true,attributeFilter:['dark']});}catch(x){}"
            + "window.addEventListener('click',function(e){"
            + "var a=e.target&&e.target.closest?e.target.closest('a[href]'):null;"
            + "if(!a)return;"
            + "var h=a.hostname,p=a.pathname;"
            + "var yt=(h==='www.youtube.com'||h==='m.youtube.com'||h==='youtube.com');"
            + "if(yt&&p.indexOf('/shorts/')===0){try{VideoBridge.shorts();}catch(x){}return;}"
            + "var ok=(yt&&(p==='/watch'||p.indexOf('/live/')===0||p==='/playlist'))||h==='youtu.be';"
            + "if(!ok)return;"
            + "e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();"
            + "try{var v=document.querySelector('video');if(v)v.pause();}catch(x){}"
            + "VideoBridge.play(a.href);"
            + "},true);"
            + "})();";

    private int mode = MODE_NONE;
    private int corner;
    private boolean fsOn;

    private FrameLayout root;
    private WebView ytWeb;

    private FrameLayout playerLayer;
    private FrameLayout fullBg;
    private FrameLayout card;
    private LinearLayout miniRow;
    private View miniTap;
    private TextView miniPp;
    private WebView pWeb;
    private ProgressBar spinner;

    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    private final Runnable hideSpinner = new Runnable() {
        @Override
        public void run() {
            if (spinner != null) spinner.setVisibility(View.GONE);
        }
    };

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

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        ytWeb = new WebView(this);
        ytWeb.setBackgroundColor(Color.WHITE);
        root.addView(ytWeb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildPlayerLayer();
        setContentView(root);
        applyChrome();

        WebSettings s = ytWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setForceDark(WebSettings.FORCE_DARK_OFF);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(ytWeb, true);

        ytWeb.addJavascriptInterface(new Bridge(), "VideoBridge");
        ytWeb.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (request.isForMainFrame() && !isShorts(url) && VideoLink.parse(url) != null) {
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

        forceLightCookie(cm);
        ytWeb.loadUrl(HOME);
        handleShare(getIntent());
    }

    private static boolean isShorts(String url) {
        try {
            String path = Uri.parse(url).getPath();
            return path != null && path.startsWith("/shorts/");
        } catch (Exception e) {
            return false;
        }
    }

    /** YouTube की अपनी थीम-सेटिंग (PREF कुकी) को लाइट पर कर देता है */
    private void forceLightCookie(CookieManager cm) {
        try {
            String cur = cm.getCookie("https://m.youtube.com");
            String pref = "";
            if (cur != null) {
                for (String c : cur.split(";")) {
                    c = c.trim();
                    if (c.startsWith("PREF=")) pref = c.substring(5);
                }
            }
            StringBuilder nb = new StringBuilder("f6=80000");
            if (!pref.isEmpty()) {
                for (String kv : pref.split("&")) {
                    if (!kv.isEmpty() && !kv.startsWith("f6=")) nb.append('&').append(kv);
                }
            }
            cm.setCookie("https://m.youtube.com",
                    "PREF=" + nb + "; Domain=.youtube.com; Path=/; Secure; Max-Age=31536000");
            cm.flush();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // प्लेयर की स्क्रीन बनाना
    // ------------------------------------------------------------------
    private void buildPlayerLayer() {
        playerLayer = new FrameLayout(this);
        playerLayer.setVisibility(View.GONE);
        playerLayer.setElevation(dp(12));

        // ---- बड़ा मोड: ग्रेडिएंट बैकग्राउंड + ऊपर ✕ और नाम ----
        fullBg = new FrameLayout(this);
        fullBg.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF222222, 0xFF000000}));

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
                stopPlayer();
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

        fullBg.addView(top, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TOP_H), Gravity.TOP));
        playerLayer.addView(fullBg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- छोटा (मिनी) मोड: गहरी पट्टी, नाम, ⏸/▶ और ✕ ----
        miniRow = new LinearLayout(this);
        miniRow.setOrientation(LinearLayout.HORIZONTAL);
        miniRow.setGravity(Gravity.CENTER_VERTICAL);
        miniRow.setBackgroundColor(0xFF0F0F0F);
        miniRow.setPadding(dp(8) + dp(MINI_W) + dp(12), 0, dp(8), 0);
        miniRow.setVisibility(View.GONE);
        miniRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_FULL);
            }
        });

        TextView miniTitle = new TextView(this);
        miniTitle.setText("वीडियो चल रहा है");
        miniTitle.setTextColor(Color.WHITE);
        miniTitle.setTextSize(14);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniRow.addView(miniTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        miniPp = new TextView(this);
        miniPp.setText("⏸");
        miniPp.setTextColor(Color.WHITE);
        miniPp.setTextSize(22);
        miniPp.setGravity(Gravity.CENTER);
        miniPp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pWeb.evaluateJavascript("toggle()", null);
            }
        });
        miniRow.addView(miniPp, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView miniClose = new TextView(this);
        miniClose.setText("✕");
        miniClose.setTextColor(Color.WHITE);
        miniClose.setTextSize(18);
        miniClose.setGravity(Gravity.CENTER);
        miniClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlayer();
            }
        });
        miniRow.addView(miniClose, new LinearLayout.LayoutParams(dp(44), dp(44)));

        playerLayer.addView(miniRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- वीडियो का कार्ड (दोनों मोड में यही चलता है, सिर्फ़ साइज़ बदलता है) ----
        corner = dp(16);
        card = new FrameLayout(this);
        card.setBackgroundColor(Color.BLACK);
        card.setElevation(dp(8));
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), corner);
            }
        });
        card.setClipToOutline(true);

        pWeb = new WebView(this);
        pWeb.setBackgroundColor(Color.BLACK);
        card.addView(pWeb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(0xFFFF0000));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(32), dp(32));
        sp.gravity = Gravity.CENTER;
        card.addView(spinner, sp);

        playerLayer.addView(card);

        // मिनी में वीडियो पर टैप -> फिर बड़ा
        miniTap = new View(this);
        miniTap.setVisibility(View.GONE);
        miniTap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_FULL);
            }
        });
        playerLayer.addView(miniTap);

        root.addView(playerLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings ps = pWeb.getSettings();
        ps.setJavaScriptEnabled(true);
        ps.setDomStorageEnabled(true);
        ps.setMediaPlaybackRequiresUserGesture(false);
        pWeb.addJavascriptInterface(new NativeBridge(), "Native");
        pWeb.setWebViewClient(new WebViewClient());
        pWeb.setWebChromeClient(new WebChromeClient() {
            @Override
            @SuppressWarnings("deprecation")
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
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                applyChrome();
            }
        });
    }

    // ------------------------------------------------------------------
    // वीडियो खोलना / मोड बदलना / बंद करना
    // ------------------------------------------------------------------
    private void openPlayer(String url) {
        VideoLink link = VideoLink.parse(url);
        if (link == null) return;
        String page;
        try {
            page = readAsset("player.html")
                    .replace("__ID__", link.id == null ? "" : link.id)
                    .replace("__LIST__", link.list == null ? "" : link.list)
                    .replace("__START__", String.valueOf(link.start));
        } catch (Exception e) {
            return;
        }
        spinner.setVisibility(View.VISIBLE);
        spinner.removeCallbacks(hideSpinner);
        spinner.postDelayed(hideSpinner, 8000);
        miniPp.setText("⏸");
        pWeb.loadDataWithBaseURL("https://videoplayer.app/", page, "text/html", "utf-8", null);
        setMode(MODE_FULL);
    }

    private void setMode(int m) {
        mode = m;
        if (m == MODE_NONE) {
            playerLayer.setVisibility(View.GONE);
        } else {
            playerLayer.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams pl = (m == MODE_MINI)
                    ? new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(MINI_H), Gravity.BOTTOM)
                    : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            playerLayer.setLayoutParams(pl);
            fullBg.setVisibility(m == MODE_FULL ? View.VISIBLE : View.GONE);
            miniRow.setVisibility(m == MODE_MINI ? View.VISIBLE : View.GONE);
            miniTap.setVisibility(m == MODE_MINI ? View.VISIBLE : View.GONE);
            corner = (m == MODE_MINI) ? dp(8) : dp(16);
            card.invalidateOutline();
            applyCardLayout();
            pWeb.evaluateJavascript("setMini(" + (m == MODE_MINI) + ")", null);
        }
        // मिनी में नीचे की जगह छोड़ दो, ताकि YouTube का पेज पट्टी के पीछे न दबे
        FrameLayout.LayoutParams yl = (FrameLayout.LayoutParams) ytWeb.getLayoutParams();
        yl.bottomMargin = (m == MODE_MINI) ? dp(MINI_H) : 0;
        ytWeb.setLayoutParams(yl);
        applyChrome();
    }

    private void stopPlayer() {
        if (customView != null) pWeb.getWebChromeClient().onHideCustomView();
        if (fsOn) setFullscreen(false);
        spinner.removeCallbacks(hideSpinner);
        pWeb.loadUrl("about:blank");
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        setMode(MODE_NONE);
    }

    /** पूरा स्क्रीन: आड़ा (landscape), बिना बार, नॉच तक वीडियो */
    @SuppressWarnings("deprecation")
    private void setFullscreen(boolean on) {
        if (mode == MODE_NONE) return;
        fsOn = on;
        WindowManager.LayoutParams a = getWindow().getAttributes();
        if (on) {
            a.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(a);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else {
            a.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            getWindow().setAttributes(a);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        fullBg.setVisibility((!on && mode == MODE_FULL) ? View.VISIBLE : View.GONE);
        corner = on ? 0 : dp(16);
        card.invalidateOutline();
        applyCardLayout();
        applyChrome();
        pWeb.evaluateJavascript("setFs(" + on + ")", null);
    }

    private void applyCardLayout() {
        FrameLayout.LayoutParams lp;
        if (fsOn) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        } else if (mode == MODE_MINI) {
            lp = new FrameLayout.LayoutParams(dp(MINI_W), dp(MINI_CARD_H));
            lp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
            lp.leftMargin = dp(8);
        } else if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins(dp(8), dp(TOP_H), dp(8), dp(8));
        } else {
            int w = getResources().getDisplayMetrics().widthPixels - dp(24);
            lp = new FrameLayout.LayoutParams(w, w * 9 / 16 + dp(BAR_DP));
            lp.setMargins(dp(12), dp(TOP_H), dp(12), 0);
            lp.gravity = Gravity.TOP;
        }
        card.setLayoutParams(lp);
        miniTap.setLayoutParams(new FrameLayout.LayoutParams(lp));
    }

    /** स्टेटस बार / नेविगेशन बार के रंग: बड़े प्लेयर में गहरे, बाकी में वाइट */
    @SuppressWarnings("deprecation")
    private void applyChrome() {
        if (customView != null || fsOn) return;
        if (mode == MODE_FULL) {
            getWindow().setStatusBarColor(0xFF222222);
            getWindow().setNavigationBarColor(0xFF000000);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } else {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (card != null && mode != MODE_NONE) applyCardLayout();
    }

    // ------------------------------------------------------------------
    // Share, bridges, बैक
    // ------------------------------------------------------------------
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

    /** YouTube पेज से: वीडियो पर क्लिक */
    private class Bridge {
        @JavascriptInterface
        public void shorts() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mode != MODE_NONE) stopPlayer();
                }
            });
        }

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

    /** प्लेयर पेज से: तैयार है / चल रहा है या रुका */
    private class NativeBridge {
        @JavascriptInterface
        public void ready() {
            runOnUiThread(hideSpinner);
        }

        @JavascriptInterface
        public void fullscreen(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setFullscreen(on);
                }
            });
        }

        /** ब्राइटनेस स्लाइडर: 5 से 100 */
        @JavascriptInterface
        public void setBrightness(final int percent) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = Math.max(5, Math.min(100, percent)) / 100f;
                    getWindow().setAttributes(lp);
                }
            });
        }

        @JavascriptInterface
        public int getBrightness() {
            float b = getWindow().getAttributes().screenBrightness;
            if (b < 0) {
                try {
                    b = Settings.System.getInt(getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS) / 255f;
                } catch (Exception e) {
                    b = 0.5f;
                }
            }
            return Math.max(5, Math.min(100, Math.round(b * 100)));
        }

        @JavascriptInterface
        public void state(final int s) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    miniPp.setText((s == 1 || s == 3) ? "⏸" : "▶");
                }
            });
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (customView != null) {
            pWeb.getWebChromeClient().onHideCustomView();
        } else if (fsOn) {
            setFullscreen(false);
        } else if (mode == MODE_FULL) {
            setMode(MODE_MINI);          // बैक -> वीडियो नीचे छोटा होकर चलता रहेगा
        } else if (ytWeb != null && ytWeb.canGoBack()) {
            ytWeb.goBack();
        } else {
            super.onBackPressed();
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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (pWeb != null) {
            pWeb.loadUrl("about:blank");
            pWeb.destroy();
            pWeb = null;
        }
        if (ytWeb != null) {
            ytWeb.destroy();
            ytWeb = null;
        }
        super.onDestroy();
    }
}
