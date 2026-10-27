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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewConfiguration;
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
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAdsShowOptions;
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
    private static final int MINI_W = 180;  // dp, तैरती छोटी विंडो की चौड़ाई
    private static final int MINI_CARD_H = 101; // dp, उसकी ऊँचाई (16:9)
    private static final int SHORTS_BANNER_H = 50; // dp, Shorts के सबसे नीचे वाला बैनर
    private static final int TOP_H = 56;    // dp, बड़े प्लेयर की ऊपर वाली पट्टी
    private static final int BAR_DP = 52;   // dp, player.html की नीचे की बार (1 पंक्ति)
    private static final int MAX_BANNERS = 4; // नीचे ज़्यादा से ज़्यादा इतने बैनर

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
            + "document.documentElement.scrollLeft=0;document.body&&(document.body.scrollLeft=0);"
            + "if(window.scrollX!==0)window.scrollTo(0,window.scrollY);}catch(x){}";

    private static final int REQ_AUDIO = 77;
    // false = फोन घुमाने से अपने-आप फुलस्क्रीन नहीं होगा; सिर्फ़ ⛶ बटन से
    private static final boolean AUTO_ROTATE_FULLSCREEN = false;
    // true = फुलस्क्रीन से लौटते ही YouTube पेज ताज़ा लोड होता है (प्लेयर के पीछे, दिखता नहीं)
    // ताकि वह ठीक वैसा खुले जैसा शुरू में खुलता है, बाईं तरफ़ खिसका हुआ न रहे
    private static final boolean RELOAD_YT_AFTER_FULLSCREEN = true;

    // Unity Ads (सिर्फ़ वीडियो प्लेयर के नीचे बैनर)
    private static final String UNITY_GAME_ID = "800411568";
    private static final String BANNER_PLACEMENT = "BP_Banner_Android";
    // फुलस्क्रीन करने पर आने वाला इंटरस्टिशियल (अपनी Unity डैशबोर्ड वाली Placement ID यहाँ डालें)
    private static final String INTERSTITIAL_PLACEMENT = "BP_Interstitial_Android";
    private static final long INTERSTITIAL_GAP_MS = 60000;   // दो विज्ञापनों के बीच कम से कम इतना अंतर
    // false = असली विज्ञापन (टेस्ट के लिए true)
    private static final boolean UNITY_TEST_MODE = false;

    private int mode = MODE_NONE;
    private SpeechRecognizer recognizer;
    private FrameLayout bannerHolder;
    private LinearLayout bannerBox;
    private boolean adsReady;
    private boolean interReady, interLoading, interShowing;
    private long lastInter;
    private final ArrayList<AdSlot> adSlots = new ArrayList<AdSlot>();

    // फोन आड़ा करने पर अपने-आप फुलस्क्रीन
    private OrientationEventListener orient;
    private boolean fsByRotation, lastLand, rotEnterPending, rotExitPending, rotSuppress;
    private final Runnable rotEnter = new Runnable() {
        @Override
        public void run() {
            rotEnterPending = false;
            if (lastLand && !rotSuppress && mode == MODE_FULL && !fsOn && customView == null) {
                fsByRotation = true;
                requestFullscreen();
            }
        }
    };
    private final Runnable rotExit = new Runnable() {
        @Override
        public void run() {
            rotExitPending = false;
            if (!lastLand && fsOn && fsByRotation) {
                fsByRotation = false;
                setFullscreen(false);
            }
        }
    };
    private PermissionRequest pendingWebPerm;
    private boolean pendingStart;
    private int corner;
    private boolean fsOn;
    private boolean ytLocked;
    private final Runnable unlockRun = new Runnable() {
        @Override
        public void run() {
            unlockYt();
        }
    };

    private FrameLayout root;
    private WebView ytWeb;

    private FrameLayout playerLayer;
    private FrameLayout fullBg;
    private FrameLayout card;
    private FrameLayout miniBox;      // तैरती छोटी विंडो का टच-क्षेत्र + बटन
    private TextView miniPp;
    private float miniX, miniY;       // छोटी विंडो की जगह (px, ऊपर-बाएँ कोना)
    private boolean miniPlaced;

    // Shorts पेज पर सबसे नीचे एक बैनर
    private FrameLayout shortsHolder;
    private AdSlot shortsSlot;
    private boolean onShorts;
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

        buildShortsBanner();
        buildPlayerLayer();
        setContentView(root);
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int oldL, int oldT, int oldR, int oldB) {
                if (mode == MODE_MINI && !fsOn
                        && (r - l != oldR - oldL || b - t != oldB - oldT)) {
                    placeMini();
                }
            }
        });
        startOrientationWatch();
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
                onYtUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                inject(view);
                onYtUrl(url);
            }

            /** YouTube (m.) पेज बदले बिना भी URL बदलता है (Shorts में ऊपर स्वाइप) -> यहीं पकड़ में आता है */
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                onYtUrl(url);
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

    // ------------------------------------------------------------------
    // फुलस्क्रीन पर इंटरस्टिशियल विज्ञापन
    // ------------------------------------------------------------------
    private void loadInterstitial() {
        if (!adsReady || interReady || interLoading) return;
        interLoading = true;
        try {
            UnityAds.load(INTERSTITIAL_PLACEMENT, new IUnityAdsLoadListener() {
                @Override
                public void onUnityAdsAdLoaded(String placementId) {
                    interLoading = false;
                    interReady = true;
                }

                @Override
                public void onUnityAdsFailedToLoad(String placementId,
                        UnityAds.UnityAdsLoadError error, String message) {
                    interLoading = false;
                    Log.e("UnityInter", "load failed: " + message);
                    // 30 सेकंड बाद फिर कोशिश
                    root.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            loadInterstitial();
                        }
                    }, 30000);
                }
            });
        } catch (Throwable t) {
            interLoading = false;
        }
    }

    /** फुलस्क्रीन माँगी गई: विज्ञापन तैयार हो तो पहले वह, फिर फुलस्क्रीन; वरना सीधे फुलस्क्रीन */
    private void requestFullscreen() {
        if (mode == MODE_NONE || fsOn || interShowing) return;
        long now = System.currentTimeMillis();
        if (!adsReady || !interReady || now - lastInter < INTERSTITIAL_GAP_MS) {
            setFullscreen(true);
            return;
        }
        interShowing = true;
        interReady = false;
        try {
            UnityAds.show(this, INTERSTITIAL_PLACEMENT, new UnityAdsShowOptions(),
                    new IUnityAdsShowListener() {
                        @Override
                        public void onUnityAdsShowFailure(String placementId,
                                UnityAds.UnityAdsShowError error, String message) {
                            Log.e("UnityInter", "show failed: " + message);
                            afterInterstitial(false);
                        }

                        @Override
                        public void onUnityAdsShowStart(String placementId) {
                        }

                        @Override
                        public void onUnityAdsShowClick(String placementId) {
                        }

                        @Override
                        public void onUnityAdsShowComplete(String placementId,
                                UnityAds.UnityAdsShowCompletionState state) {
                            afterInterstitial(true);
                        }
                    });
        } catch (Throwable t) {
            afterInterstitial(false);
        }
    }

    private void afterInterstitial(final boolean shown) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                interShowing = false;
                if (shown) lastInter = System.currentTimeMillis();
                loadInterstitial();                       // अगली बार के लिए
                if (mode != MODE_NONE && !fsOn) setFullscreen(true);
            }
        });
    }

    // ------------------------------------------------------------------
    // Shorts: सबसे नीचे एक बैनर
    // ------------------------------------------------------------------
    private void buildShortsBanner() {
        shortsHolder = new FrameLayout(this);
        shortsHolder.setVisibility(View.GONE);
        root.addView(shortsHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(SHORTS_BANNER_H), Gravity.BOTTOM));
    }

    private static boolean isShortsPage(String url) {
        if (url == null) return false;
        try {
            Uri u = Uri.parse(url);
            String h = u.getHost(), p = u.getPath();
            return h != null && h.endsWith("youtube.com") && p != null
                    && (p.equals("/shorts") || p.startsWith("/shorts/"));
        } catch (Exception e) {
            return false;
        }
    }

    private void onYtUrl(String url) {
        boolean s = isShortsPage(url);
        if (s == onShorts) return;
        onShorts = s;
        updateShortsBanner(true);
    }

    /** बैनर लोड हो चुका हो तभी YouTube पेज के नीचे जगह छोड़ते हैं (वरना खाली पट्टी दिखेगी) */
    private boolean shortsBannerShown() {
        return onShorts && adsReady && shortsSlot != null && shortsSlot.ok;
    }

    private int ytBottomMargin() {
        return shortsBannerShown() ? dp(SHORTS_BANNER_H) : 0;
    }

    private void applyYtMargin() {
        if (ytWeb == null || ytLocked) return;
        ViewGroup.LayoutParams lp = ytWeb.getLayoutParams();
        if (!(lp instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams yl = (FrameLayout.LayoutParams) lp;
        int b = ytBottomMargin();
        if (yl.bottomMargin == b) return;
        yl.bottomMargin = b;
        ytWeb.setLayoutParams(yl);
    }

    private void updateShortsBanner(boolean tryLoad) {
        if (shortsHolder == null) return;
        if (!onShorts || !adsReady) {
            shortsHolder.setVisibility(View.GONE);
        } else {
            if (shortsSlot == null) {
                shortsSlot = makeSlot();
                if (shortsSlot != null) {
                    shortsSlot.onChanged = new Runnable() {
                        @Override
                        public void run() {
                            updateShortsBanner(false);
                        }
                    };
                    shortsHolder.addView(shortsSlot.view, new FrameLayout.LayoutParams(
                            dp(320), dp(50), Gravity.CENTER));
                }
            }
            shortsHolder.setBackgroundColor(shortsBannerShown() ? Color.WHITE : Color.TRANSPARENT);
            shortsHolder.setVisibility(View.VISIBLE);
            if (tryLoad && shortsSlot != null) shortsSlot.load();
        }
        applyYtMargin();
        if (mode == MODE_MINI) placeMini();
    }

    // ------------------------------------------------------------------
    // छोटी तैरती विंडो: जहाँ छोड़ो वहीं रहती है
    // ------------------------------------------------------------------
    private void placeMini() {
        if (card == null || miniBox == null) return;
        int rw = root.getWidth() > 0 ? root.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        int rh = root.getHeight() > 0 ? root.getHeight()
                : getResources().getDisplayMetrics().heightPixels;
        int w = dp(MINI_W), h = dp(MINI_CARD_H);
        if (!miniPlaced) {                      // पहली बार: नीचे दाएँ कोने में
            miniX = rw - w - dp(12);
            miniY = rh - h - dp(24);
            miniPlaced = true;
        }
        int reserve = shortsBannerShown() ? dp(SHORTS_BANNER_H) : 0;   // बैनर के ऊपर न चढ़े
        miniX = Math.max(0f, Math.min(rw - w, miniX));
        miniY = Math.max(0f, Math.min(rh - h - reserve, miniY));
        card.setTranslationX(miniX);
        card.setTranslationY(miniY);
        miniBox.setTranslationX(miniX);
        miniBox.setTranslationY(miniY);
    }

    private GradientDrawable roundBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
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

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // दाएँ कोने में होम: एक कदम पीछे -> प्लेयर नीचे छोटा, YouTube का पेज खुल जाए
        HomeIcon home = new HomeIcon(this);
        GradientDrawable circle2 = new GradientDrawable();
        circle2.setShape(GradientDrawable.OVAL);
        circle2.setColor(0x33FFFFFF);
        home.setBackground(circle2);
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_MINI);
            }
        });
        top.addView(home, new LinearLayout.LayoutParams(dp(40), dp(40)));

        fullBg.addView(top, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TOP_H), Gravity.TOP));
        playerLayer.addView(fullBg, new FrameLayout.LayoutParams(
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
        bannerBox = new LinearLayout(this);
        bannerBox.setOrientation(LinearLayout.VERTICAL);
        bannerBox.setGravity(Gravity.CENTER_HORIZONTAL);
        bannerHolder.addView(bannerBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- छोटी तैरती विंडो: उँगली से कहीं भी खिसकाओ, टैप करो तो बड़ा ----
        miniBox = new FrameLayout(this);
        miniBox.setVisibility(View.GONE);
        miniBox.setElevation(dp(14));   // card (8dp) से ऊपर, ताकि टच पहले इसी को मिले
        miniBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_FULL);
            }
        });
        miniBox.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY, startX, startY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = miniX;
                        startY = miniY;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        int slop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
                        if (!moved && Math.hypot(dx, dy) > slop) moved = true;
                        if (moved) {
                            miniX = startX + dx;
                            miniY = startY + dy;
                            placeMini();
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) v.performClick();
                        return true;
                    default:
                        return true;
                }
            }
        });

        miniPp = new TextView(this);
        miniPp.setText("❚❚");
        miniPp.setTextColor(Color.WHITE);
        miniPp.setTextSize(13);
        miniPp.setGravity(Gravity.CENTER);
        miniPp.setBackground(roundBg(0x99000000));
        miniPp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pWeb.evaluateJavascript("toggle()", null);
            }
        });
        FrameLayout.LayoutParams ppLp = new FrameLayout.LayoutParams(
                dp(30), dp(30), Gravity.BOTTOM | Gravity.LEFT);
        ppLp.setMargins(dp(6), 0, 0, dp(6));
        miniBox.addView(miniPp, ppLp);

        TextView miniClose = new TextView(this);
        miniClose.setText("✕");
        miniClose.setTextColor(Color.WHITE);
        miniClose.setTextSize(14);
        miniClose.setGravity(Gravity.CENTER);
        miniClose.setBackground(roundBg(0x99000000));
        miniClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlayer();
            }
        });
        FrameLayout.LayoutParams clLp = new FrameLayout.LayoutParams(
                dp(30), dp(30), Gravity.TOP | Gravity.RIGHT);
        clLp.setMargins(0, dp(6), dp(6), 0);
        miniBox.addView(miniClose, clLp);

        playerLayer.addView(miniBox, new FrameLayout.LayoutParams(
                dp(MINI_W), dp(MINI_CARD_H), Gravity.TOP | Gravity.LEFT));

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
                root.removeCallbacks(unlockRun);
                lockYt();
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
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                root.postDelayed(unlockRun, 1500);
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
            // परत दोनों मोड में पूरी स्क्रीन की; छोटे मोड में खाली जगह का टच नीचे YouTube पेज को जाता है
            playerLayer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fullBg.setVisibility(m == MODE_FULL ? View.VISIBLE : View.GONE);
            miniBox.setVisibility(m == MODE_MINI ? View.VISIBLE : View.GONE);
            corner = (m == MODE_MINI) ? dp(12) : dp(16);
            card.invalidateOutline();
            applyCardLayout();
            pWeb.evaluateJavascript("setMini(" + (m == MODE_MINI) + ")", null);
        }
        // छोटी विंडो अब पेज के ऊपर तैरती है, नीचे जगह नहीं छोड़नी; सिर्फ़ Shorts बैनर की जगह
        applyYtMargin();
        if (!fsOn && customView == null) {
            setRequestedOrientation(m == MODE_FULL
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
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
        if (on) {
            root.removeCallbacks(unlockRun);
            lockYt();
        }
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
            if (lastLand) rotSuppress = true;
            a.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            getWindow().setAttributes(a);
            setRequestedOrientation(mode == MODE_FULL
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            root.postDelayed(unlockRun, 1500);   // पक्का: 1.5 सेकंड में साइज़ खोल दो
        }
        fullBg.setVisibility((!on && mode == MODE_FULL) ? View.VISIBLE : View.GONE);
        corner = on ? 0 : dp(16);
        card.invalidateOutline();
        applyCardLayout();
        applyChrome();
        pWeb.evaluateJavascript("setFs(" + on + ")", null);
        relayoutYt();
    }

    /** होम का चित्र (छत, दीवार, दरवाज़ा) */
    private static class HomeIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        HomeIcon(Context c) {
            super(c);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float u = getWidth() / 40f;
            paint.setStrokeWidth(2.2f * u);
            path.reset();
            path.moveTo(10 * u, 21 * u);
            path.lineTo(20 * u, 12 * u);
            path.lineTo(30 * u, 21 * u);
            path.moveTo(13 * u, 19 * u);
            path.lineTo(13 * u, 29 * u);
            path.lineTo(27 * u, 29 * u);
            path.lineTo(27 * u, 19 * u);
            path.moveTo(18 * u, 29 * u);
            path.lineTo(18 * u, 23 * u);
            path.lineTo(22 * u, 23 * u);
            path.lineTo(22 * u, 29 * u);
            canvas.drawPath(path, paint);
        }
    }

    // ------------------------------------------------------------------
    // वीडियो को उंगली से नीचे खींचना -> छोटा होकर नीचे चिपक जाए (एक कदम पीछे)
    // ------------------------------------------------------------------
    private boolean dragCand, dragging;
    private float dragX0, dragY0, dragDy;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (card != null && mode == MODE_FULL && !fsOn && customView == null
                && playerLayer.getVisibility() == View.VISIBLE && handleDrag(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean handleDrag(MotionEvent ev) {
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            dragging = false;
            int[] loc = new int[2];
            card.getLocationOnScreen(loc);
            float x = ev.getRawX(), y = ev.getRawY();
            int videoH = card.getHeight() - dp(BAR_DP);   // सिर्फ़ वीडियो वाला हिस्सा
            dragCand = x >= loc[0] && x <= loc[0] + card.getWidth()
                    && y >= loc[1] && y <= loc[1] + videoH;
            dragX0 = x;
            dragY0 = y;
            return false;
        }
        if (!dragCand && !dragging) return false;
        if (a == MotionEvent.ACTION_POINTER_DOWN && !dragging) {
            dragCand = false;
            return false;
        }
        if (a == MotionEvent.ACTION_MOVE) {
            float dy = ev.getRawY() - dragY0, dx = ev.getRawX() - dragX0;
            if (!dragging) {
                int slop = ViewConfiguration.get(this).getScaledTouchSlop();
                if (dy > slop * 2 && dy > Math.abs(dx) * 1.5f) {
                    dragging = true;
                    MotionEvent c = MotionEvent.obtain(ev);
                    c.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(c);   // वीडियो पेज का टच रद्द
                    c.recycle();
                    card.setPivotX(card.getWidth() / 2f);
                    card.setPivotY(0f);
                } else {
                    if (Math.abs(dx) > slop * 2 || dy < -slop * 2) dragCand = false;
                    return false;
                }
            }
            dragDy = Math.max(0f, dy);
            applyDrag(dragDy);
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            boolean was = dragging;
            dragging = false;
            dragCand = false;
            if (!was) return false;
            if (a == MotionEvent.ACTION_UP && dragDy > dp(110)) {
                // जहाँ उँगली छोड़ी, वहीं छोटी विंडो बने
                int rw = root.getWidth() > 0 ? root.getWidth()
                        : getResources().getDisplayMetrics().widthPixels;
                miniX = (rw - dp(MINI_W)) / 2f;
                miniY = dp(TOP_H) + dragDy * 0.9f;
                miniPlaced = true;
                resetDragViews();
                setMode(MODE_MINI);
            } else {
                card.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(180).start();
                fullBg.animate().alpha(1f).setDuration(180).start();
                bannerHolder.animate().alpha(1f).translationY(0f).setDuration(180).start();
            }
            return true;
        }
        return dragging;
    }

    private void applyDrag(float dy) {
        float p = Math.min(1f, dy / dp(260));
        float sc = 1f - 0.4f * p;
        card.setTranslationY(dy * 0.9f);
        card.setScaleX(sc);
        card.setScaleY(sc);
        fullBg.setAlpha(1f - p);
        bannerHolder.setTranslationY(dy);
        bannerHolder.setAlpha(Math.max(0f, 1f - p * 1.5f));
    }

    private void resetDragViews() {
        card.animate().cancel();
        fullBg.animate().cancel();
        bannerHolder.animate().cancel();
        card.setTranslationY(0f);
        card.setScaleX(1f);
        card.setScaleY(1f);
        fullBg.setAlpha(1f);
        bannerHolder.setAlpha(1f);
        bannerHolder.setTranslationY(0f);
    }

    private void applyCardLayout() {
        FrameLayout.LayoutParams lp;
        if (fsOn) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        } else if (mode == MODE_MINI) {
            lp = new FrameLayout.LayoutParams(dp(MINI_W), dp(MINI_CARD_H), Gravity.TOP | Gravity.LEFT);
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
        if (mode == MODE_MINI && !fsOn) {
            placeMini();
        } else if (!dragging) {
            card.setTranslationX(0f);
            card.setTranslationY(0f);
        }

        // बैनर: सिर्फ़ बड़े प्लेयर में, पोर्ट्रेट में, सीक बार वाले कार्ड के ठीक नीचे
        boolean portrait = getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
        if (!fsOn && mode == MODE_FULL && portrait) {
            int rootH = root.getHeight();
            if (rootH <= 0) rootH = getResources().getDisplayMetrics().heightPixels;
            int top = lp.topMargin + lp.height + dp(12);
            int slotH = dp(58);
            int n = (rootH - top - dp(12)) / slotH;
            n = Math.max(1, Math.min(MAX_BANNERS, n));
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, n * slotH, Gravity.TOP);
            bl.topMargin = top;
            bannerHolder.setLayoutParams(bl);
            bannerHolder.setVisibility(View.VISIBLE);
            // बैनर तभी लोड हों जब उनकी जगह दिख रही हो
            if (adsReady) {
                ensureSlots(n);
                loadSlots(n);
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
        if (ytLocked && !fsOn && customView == null
                && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            unlockYt();
        }
        relayoutYt();
    }

    @Override
    protected void onPause() {
        if (orient != null) orient.disable();
        super.onPause();
    }

    /** फोन आड़ा करो -> फुलस्क्रीन बटन दबाने जैसा; वापस सीधा करो -> बाहर */
    private void startOrientationWatch() {
        orient = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int deg) {
                if (deg == ORIENTATION_UNKNOWN || root == null) return;
                boolean land = (deg >= 60 && deg <= 120) || (deg >= 240 && deg <= 300);
                boolean port = deg <= 30 || deg >= 330;
                lastLand = land;
                if (!AUTO_ROTATE_FULLSCREEN) return;
                if (land) {
                    if (rotExitPending) {
                        root.removeCallbacks(rotExit);
                        rotExitPending = false;
                    }
                    if (mode == MODE_FULL && !fsOn && !rotSuppress && !rotEnterPending) {
                        rotEnterPending = true;
                        root.postDelayed(rotEnter, 500);
                    }
                } else if (port) {
                    rotSuppress = false;
                    if (rotEnterPending) {
                        root.removeCallbacks(rotEnter);
                        rotEnterPending = false;
                    }
                    if (fsOn && fsByRotation && !rotExitPending) {
                        rotExitPending = true;
                        root.postDelayed(rotExit, 500);
                    }
                } else {
                    if (rotEnterPending) {
                        root.removeCallbacks(rotEnter);
                        rotEnterPending = false;
                    }
                    if (rotExitPending) {
                        root.removeCallbacks(rotExit);
                        rotExitPending = false;
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (orient != null && orient.canDetectOrientation()) orient.enable();
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
                    fsByRotation = false;
                    if (on) requestFullscreen(); else setFullscreen(false);
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
    /** एक बैनर की जगह: उसका व्यू और उसकी अपनी लोड-स्थिति */
    private class AdSlot {
        BannerView view;
        boolean ok, loading;
        int tries;
        Runnable onChanged;   // लोड / फेल होने पर (UI थ्रेड पर) चलता है

        void load() {
            if (view == null || ok || loading || !adsReady) return;
            loading = true;
            try {
                view.load();
            } catch (Throwable t) {
                loading = false;
            }
        }
    }

    private AdSlot makeSlot() {
        final AdSlot slot = new AdSlot();
        try {
            slot.view = new BannerView(this, BANNER_PLACEMENT, new UnityBannerSize(320, 50));
        } catch (Throwable t) {
            return null;
        }
        slot.view.setListener(new BannerView.IListener() {
            @Override
            public void onBannerLoaded(BannerView bannerAdView) {
                slot.ok = true;
                slot.loading = false;
                if (slot.onChanged != null) runOnUiThread(slot.onChanged);
            }

            public void onBannerShown(BannerView bannerAdView) {
            }

            @Override
            public void onBannerClick(BannerView bannerAdView) {
            }

            @Override
            public void onBannerFailedToLoad(BannerView bannerAdView, BannerErrorInfo errorInfo) {
                slot.ok = false;
                slot.loading = false;
                if (slot.onChanged != null) runOnUiThread(slot.onChanged);
                Log.e("UnityBanner", "banner failed: "
                        + (errorInfo == null ? "?" : String.valueOf(errorInfo.errorMessage)));
                if (slot.tries < 8 && slot.view != null) {
                    slot.tries++;
                    slot.view.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            slot.load();
                        }
                    }, 30000);
                }
            }

            @Override
            public void onBannerLeftApplication(BannerView bannerAdView) {
            }
        });
        return slot;
    }

    /** ज़रूरत भर बैनर बनाता है और बाकी को छिपा देता है */
    private void ensureSlots(int n) {
        while (adSlots.size() < n) {
            AdSlot s = makeSlot();
            if (s == null) break;
            adSlots.add(s);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(320), dp(50));
            p.bottomMargin = dp(8);
            bannerBox.addView(s.view, p);
        }
        for (int i = 0; i < adSlots.size(); i++) {
            adSlots.get(i).view.setVisibility(i < n ? View.VISIBLE : View.GONE);
        }
    }

    /** एक साथ सब नहीं: हर बैनर थोड़े अंतर पर लोड होता है (एक साथ लोड से टाइमआउट आता है) */
    private void loadSlots(int n) {
        for (int i = 0; i < n && i < adSlots.size(); i++) {
            final AdSlot s = adSlots.get(i);
            bannerHolder.postDelayed(new Runnable() {
                @Override
                public void run() {
                    s.load();
                }
            }, i * 900L);
        }
    }

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
                                    if (card != null && mode != MODE_NONE) applyCardLayout();
                                    updateShortsBanner(true);
                                    loadInterstitial();
                                }
                            });
                        }

                        @Override
                        public void onInitializationFailed(
                                UnityAds.UnityAdsInitializationError error, String message) {
                            // विज्ञापन न आए तो भी ऐप चलता रहेगा
                            Log.e("UnityBanner", "init failed: " + message);
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private void inject(WebView view) {
        view.evaluateJavascript(HOOK, null);
        view.evaluateJavascript(SPEECH_JS, null);
        view.evaluateJavascript(VIEW_FIX, null);
    }

    /**
     * फुलस्क्रीन (आड़ा) के दौरान YouTube पेज को स्क्रीन से अलग (detach) रखते हैं, ताकि उसे घुमाव का
     * पता ही न चले। वापस सीधा होने पर वही पेज उसी साइज़ में फिर जोड़ देते हैं -> बाईं तरफ़ से कटना नहीं।
     */
    private void lockYt() {
        if (ytWeb == null || ytLocked) return;
        ytLocked = true;
        root.removeView(ytWeb);
    }

    private void unlockYt() {
        if (ytWeb == null || !ytLocked) return;
        ytLocked = false;
        FrameLayout.LayoutParams yl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        yl.bottomMargin = ytBottomMargin();
        root.addView(ytWeb, 0, yl);
        if (RELOAD_YT_AFTER_FULLSCREEN) {
            String u = ytWeb.getUrl();
            if (u != null && u.startsWith("http")) ytWeb.reload();
        }
        relayoutYt();
    }

    private void healYt() {
        if (ytWeb == null || ytLocked) return;
        ytWeb.requestLayout();
        ytWeb.scrollTo(0, ytWeb.getScrollY());          // आड़ा खिसकाव हटाओ
        ytWeb.evaluateJavascript(VIEW_FIX, null);       // ज़ूम 1 पर, किनारे बराबर
        ytWeb.evaluateJavascript("window.dispatchEvent(new Event('orientationchange'));"
                + "window.dispatchEvent(new Event('resize'));", null);
    }

    private void relayoutYt() {
        if (ytWeb == null || ytLocked) return;
        healYt();
        int[] delays = {150, 400, 800, 1500};
        for (int i = 0; i < delays.length; i++) {
            root.postDelayed(new Runnable() {
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
        for (AdSlot a : adSlots) {
            try {
                if (a.view != null) a.view.destroy();
            } catch (Throwable ignored) {
            }
        }
        adSlots.clear();
        try {
            if (shortsSlot != null && shortsSlot.view != null) shortsSlot.view.destroy();
        } catch (Throwable ignored) {
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
