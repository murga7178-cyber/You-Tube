package com.videoplayer.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
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
import android.util.Log;
import android.widget.Toast;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
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
import java.util.ArrayList;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerErrorInfo;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

import org.json.JSONObject;

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

    // YouTube की आवाज़-खोज (माइक) के लिए: फोन की अपनी Speech Recognition से जोड़ता है
    private static final String SPEECH_JS =
            "(function(){"
            + "if(window.__vpSpeech)return;window.__vpSpeech=true;"
            + "function SR(){this.lang='hi-IN';this.continuous=false;this.interimResults=false;this.maxAlternatives=1;this._l={};}"
            + "SR.prototype.start=function(){window.__vpSR=this;Speech.start();};"
            + "SR.prototype.stop=function(){Speech.stop();};"
            + "SR.prototype.abort=function(){Speech.cancel();};"
            + "SR.prototype.addEventListener=function(t,f){(this._l[t]=this._l[t]||[]).push(f);};"
            + "SR.prototype.removeEventListener=function(t,f){var a=this._l[t]||[];var i=a.indexOf(f);if(i>-1)a.splice(i,1);};"
            + "function fire(o,t,ev){ev=ev||{};ev.type=t;var h=o['on'+t];if(typeof h==='function'){try{h.call(o,ev);}catch(e){}}(o._l[t]||[]).forEach(function(f){try{f.call(o,ev);}catch(e){}});}"
            + "window.__vpSpeechEvent=function(type,text){var o=window.__vpSR;if(!o)return;"
            + "if(type==='result'){var alt={transcript:text,confidence:0.9};var res=[alt];res.isFinal=true;res.item=function(i){return res[i];};var list=[res];list.item=function(i){return list[i];};fire(o,'result',{results:list,resultIndex:0});}"
            + "else if(type==='error'){fire(o,'error',{error:text});}"
            + "else{fire(o,type);}};"
            + "window.SpeechRecognition=SR;window.webkitSpeechRecognition=SR;"
            + "})();";

    // पेज को ज़ूम / किनारे खाली होने से रोकता है
    private static final String VIEW_FIX =
            "try{var vm=document.querySelector('meta[name=viewport]');"
            + "if(!vm){vm=document.createElement('meta');vm.name='viewport';document.head.appendChild(vm);}"
            + "vm.setAttribute('content','width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no');"
            + "document.documentElement.style.overflowX='hidden';"
            + "document.body&&(document.body.style.overflowX='hidden');"
            + "if(window.scrollX!==0)window.scrollTo(0,window.scrollY);}catch(x){}";

    private static final int REQ_AUDIO = 77;

    // Unity Ads (सिर्फ़ वीडियो प्लेयर के नीचे बैनर)
    private static final String UNITY_GAME_ID = "800411568";
    private static final String BANNER_PLACEMENT = "BP_Banner_Android";
    // असली विज्ञापन चलाने के लिए इसे false कर दीजिए (पहले true रखकर जाँच लीजिए)
    private static final boolean UNITY_TEST_MODE = true;

    private int mode = MODE_NONE;
    private SpeechRecognizer recognizer;
    private FrameLayout bannerHolder;
    private BannerView banner;
    private boolean adsReady, bannerOk;
    private int bannerTries;
    private PermissionRequest pendingWebPerm;
    private boolean pendingStart;
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
        initAds();
        applyChrome();

        WebSettings s = ytWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setForceDark(WebSettings.FORCE_DARK_OFF);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(ytWeb, true);

        ytWeb.addJavascriptInterface(new Bridge(), "VideoBridge");
        ytWeb.addJavascriptInterface(new SpeechBridge(), "Speech");
        ytWeb.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean wantsAudio = false;
                        for (String r : request.getResources()) {
                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsAudio = true;
                        }
                        if (!wantsAudio) {
                            request.deny();
                            return;
                        }
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED) {
                            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                        } else {
                            pendingWebPerm = request;
                            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
                        }
                    }
                });
            }
        });
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
                inject(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                inject(view);
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
        fullBg.setClickable(true);   // खाली जगह पर टैप पीछे के YouTube पेज तक न पहुँचे
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
        miniPp.setText("❚❚");
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

        // सीक बार के नीचे बैनर विज्ञापन की जगह
        bannerHolder = new FrameLayout(this);
        bannerHolder.setVisibility(View.GONE);
        playerLayer.addView(bannerHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP));

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
        miniPp.setText("❚❚");
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
        relayoutYt();
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
        relayoutYt();
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

        // बैनर: सिर्फ़ बड़े प्लेयर में, पोर्ट्रेट में, सीक बार वाले कार्ड के ठीक नीचे
        boolean portrait = getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
        if (!fsOn && mode == MODE_FULL && portrait) {
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP);
            bl.topMargin = lp.topMargin + lp.height + dp(12);
            bannerHolder.setLayoutParams(bl);
            bannerHolder.setVisibility(View.VISIBLE);
            // बैनर तभी लोड हो जब उसकी जगह दिख रही हो (छिपी जगह में Unity बैनर लोड नहीं होता)
            if (adsReady) {
                if (banner == null) {
                    loadBanner();
                } else if (!bannerOk) {
                    try {
                        banner.load();
                    } catch (Throwable ignored) {
                    }
                }
            }
        } else {
            bannerHolder.setVisibility(View.GONE);
        }
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
        relayoutYt();
    }

    @Override
    protected void onResume() {
        super.onResume();
        relayoutYt();
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
                    miniPp.setText((s == 1 || s == 3) ? "❚❚" : "▶\uFE0E");
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

    // ------------------------------------------------------------------
    // Unity Ads बैनर
    // ------------------------------------------------------------------
    private void initAds() {
        try {
            UnityAds.initialize(getApplicationContext(), UNITY_GAME_ID, UNITY_TEST_MODE,
                    new IUnityAdsInitializationListener() {
                        @Override
                        public void onInitializationComplete() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    adsReady = true;
                                    if (bannerHolder != null
                                            && bannerHolder.getVisibility() == View.VISIBLE) {
                                        loadBanner();
                                    }
                                }
                            });
                        }

                        @Override
                        public void onInitializationFailed(
                                UnityAds.UnityAdsInitializationError error, String message) {
                            // विज्ञापन न आए तो भी ऐप चलता रहेगा
                            Log.e("UnityBanner", "init failed: " + message);
                            final String why = String.valueOf(message);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this,
                                            "Unity init fail: " + why, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private void loadBanner() {
        try {
            if (banner != null) return;
            banner = new BannerView(this, BANNER_PLACEMENT, new UnityBannerSize(320, 50));
            banner.setListener(new BannerView.IListener() {
                @Override
                public void onBannerLoaded(BannerView bannerAdView) {
                    bannerOk = true;
                }

                public void onBannerShown(BannerView bannerAdView) {
                }

                @Override
                public void onBannerClick(BannerView bannerAdView) {
                }

                @Override
                public void onBannerFailedToLoad(BannerView bannerAdView, final BannerErrorInfo errorInfo) {
                    bannerOk = false;
                    final String why = errorInfo == null ? "?" : String.valueOf(errorInfo.errorMessage);
                    Log.e("UnityBanner", "banner failed: " + why);
                    // जाँच के लिए: कारण स्क्रीन पर दिखेगा (काम करने लगे तो यह Toast हटा सकते हैं)
                    Toast.makeText(MainActivity.this, "Banner fail: " + why, Toast.LENGTH_LONG).show();
                    if (bannerTries < 5 && bannerHolder != null) {
                        bannerTries++;
                        bannerHolder.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (banner != null && !bannerOk) banner.load();
                                } catch (Throwable ignored) {
                                }
                            }
                        }, 20000);
                    }
                }

                @Override
                public void onBannerLeftApplication(BannerView bannerAdView) {
                }
            });
            bannerHolder.addView(banner, new FrameLayout.LayoutParams(
                    dp(320), dp(50), Gravity.CENTER));
            banner.load();
        } catch (Throwable ignored) {
        }
    }

    private void inject(WebView view) {
        view.evaluateJavascript(HOOK, null);
        view.evaluateJavascript(SPEECH_JS, null);
        view.evaluateJavascript(VIEW_FIX, null);
    }

    /** वीडियो देखकर / घुमाकर लौटने पर YouTube का पेज दोबारा पूरी चौड़ाई में, बाईं ओर से बिठाता है */
    private void healYt() {
        if (ytWeb == null) return;
        ytWeb.requestLayout();
        ytWeb.scrollTo(0, ytWeb.getScrollY());          // आड़ा खिसकाव हटाओ
        ytWeb.evaluateJavascript(VIEW_FIX, null);       // ज़ूम 1 पर, किनारे बराबर
        ytWeb.evaluateJavascript("window.dispatchEvent(new Event('resize'));", null);
    }

    private void relayoutYt() {
        if (ytWeb == null) return;
        healYt();
        int[] delays = {150, 400, 800, 1500};
        for (int i = 0; i < delays.length; i++) {
            ytWeb.postDelayed(new Runnable() {
                @Override
                public void run() {
                    healYt();
                }
            }, delays[i]);
        }
    }

    // ------------------------------------------------------------------
    // आवाज़ से खोज (माइक)
    // ------------------------------------------------------------------
    private class SpeechBridge {
        @JavascriptInterface
        public void start() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startListening();
                }
            });
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (recognizer != null) recognizer.stopListening();
                }
            });
        }

        @JavascriptInterface
        public void cancel() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (recognizer != null) recognizer.cancel();
                }
            });
        }
    }

    private void speechEvent(String type, String text) {
        if (ytWeb == null) return;
        ytWeb.evaluateJavascript("window.__vpSpeechEvent&&window.__vpSpeechEvent("
                + JSONObject.quote(type) + "," + JSONObject.quote(text) + ")", null);
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speechEvent("error", "service-not-allowed");
            speechEvent("end", "");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    speechEvent("start", "");
                    speechEvent("audiostart", "");
                }

                @Override
                public void onBeginningOfSpeech() {
                    speechEvent("speechstart", "");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    speechEvent("speechend", "");
                }

                @Override
                public void onError(int error) {
                    String code;
                    if (error == SpeechRecognizer.ERROR_NO_MATCH
                            || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        code = "no-speech";
                    } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        code = "not-allowed";
                    } else if (error == SpeechRecognizer.ERROR_AUDIO) {
                        code = "audio-capture";
                    } else {
                        code = "network";
                    }
                    speechEvent("error", code);
                    speechEvent("end", "");
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> r = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty()) speechEvent("result", r.get(0));
                    speechEvent("end", "");
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizer.startListening(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_AUDIO) return;
        boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (pendingStart) {
            pendingStart = false;
            if (ok) {
                startListening();
            } else {
                speechEvent("error", "not-allowed");
                speechEvent("end", "");
            }
        }
        if (pendingWebPerm != null) {
            if (ok) {
                pendingWebPerm.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                pendingWebPerm.deny();
            }
            pendingWebPerm = null;
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
        if (banner != null) {
            try {
                banner.destroy();
            } catch (Throwable ignored) {
            }
            banner = null;
        }
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
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
