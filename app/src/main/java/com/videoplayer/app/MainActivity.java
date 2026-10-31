package com.videoplayer.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.animation.LinearInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.WebResourceError;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.graphics.drawable.Icon;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.InputDevice;
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
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAdsShowOptions;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerErrorInfo;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.HttpURLConnection;
import java.net.URL;
import android.graphics.Rect;
import android.widget.ScrollView;

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
    // ऐप खुलने के बाद इतने गाने मुफ़्त; उसके बाद म्यूज़िक स्विच बंद, रिवॉर्डेड पूरा देखने पर ही फिर चालू
    private static final int FREE_SONGS = 10;
    private static final int MUSIC_BAR_H = 64;   // dp, म्यूज़िक मोड में नीचे वाले ऑडियो प्लेयर बार की ऊँचाई
    // true = म्यूज़िक मोड ऑन हो तो YouTube पेज से गाना चलाने पर सीधे नीचे ऑडियो बार में चलेगा
    // (false = पहले बड़ा प्लेयर खुलेगा, बैक करने पर नीचे बार बनेगा)
    private static final boolean MUSIC_OPEN_AS_BAR = true;
    private static final int MAX_BANNERS = 4; // नीचे ज़्यादा से ज़्यादा इतने बैनर

    // YouTube पेज के अंदर चलने वाली स्क्रिप्ट: वीडियो लिंक पर क्लिक पकड़ती है
    private static final String HOOK =
            "(function(){"
            + "if(window.__vpHooked)return;window.__vpHooked=true;"
            + "var vpClk=0;document.addEventListener('click',function(){vpClk=Date.now();},true);"
            + "var vpNoAuto=function(){return location.pathname.indexOf('/shorts')!==0;};"
            + "document.addEventListener('play',function(e){var v=e.target;if(!v||v.tagName!=='VIDEO'||!vpNoAuto())return;if(Date.now()-vpClk<1500)return;try{v.pause();}catch(x){}},true);"
            + "var vpSweep=function(){if(!vpNoAuto())return;var vs=document.querySelectorAll('video');for(var i=0;i<vs.length;i++){if(!vs[i].paused&&Date.now()-vpClk>1500){try{vs[i].pause();}catch(x){}}}};"
            + "setInterval(vpSweep,700);"
            + "try{var mm=window.matchMedia;window.matchMedia=function(q){if(/prefers-color-scheme/.test(q)){return {matches:/light/.test(q),media:q,onchange:null,addListener:function(){},removeListener:function(){},addEventListener:function(){},removeEventListener:function(){},dispatchEvent:function(){return false}};}return mm.call(window,q);};}catch(x){}"
            + "try{var de=document.documentElement;var fix=function(){if(de.hasAttribute('dark'))de.removeAttribute('dark');};fix();new MutationObserver(fix).observe(de,{attributes:true,attributeFilter:['dark']});}catch(x){}"
            + "var vpFind=function(a){var el=a,i=0;while(el&&i<7){var c=el.querySelectorAll('img,[class*=thumbnail],[class*=Thumbnail]'),best=null,bw=0;for(var k=0;k<c.length;k++){var r=c[k].getBoundingClientRect(),ar=r.width/(r.height||1);if(r.width>120&&ar>1.55&&ar<2.05&&r.width>bw){best=c[k];bw=r.width;}}if(best)return best;el=el.parentElement;i++;}return null;};"
            + "var vpRaf=0;var vpRep=function(){vpRaf=0;var el=window.__vpPinEl;if(!el)return;if(!el.isConnected){window.__vpPinEl=null;try{VideoBridge.pinLost();}catch(x){}return;}var r=el.getBoundingClientRect();try{VideoBridge.pin(r.left,r.top,r.width,r.height);}catch(x){}};"
            + "var vpSch=function(){if(window.__vpPinEl&&!vpRaf)vpRaf=requestAnimationFrame(vpRep);};"
            + "window.addEventListener('scroll',vpSch,true);window.addEventListener('resize',vpSch);"
            + "window.__vpPinStop=function(){window.__vpPinEl=null;};"
            + "window.addEventListener('click',function(e){"
            + "var a=e.target&&e.target.closest?e.target.closest('a[href]'):null;"
            + "if(!a)return;"
            + "var h=a.hostname,p=a.pathname;"
            + "var yt=(h==='www.youtube.com'||h==='m.youtube.com'||h==='youtube.com');"
            + "if(yt&&(p==='/shorts'||p.indexOf('/shorts/')===0)){try{VideoBridge.shorts();if(VideoBridge.openShorts(a.href)){e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();}}catch(x){}return;}"
            + "var ok=(yt&&(p==='/watch'||p.indexOf('/live/')===0||p==='/playlist'))||h==='youtu.be';"
            + "if(!ok)return;"
            + "e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();"
            + "try{var v=document.querySelector('video');if(v)v.pause();}catch(x){}"
            + "var te=null;try{te=vpFind(a);}catch(x){}"
            + "if(te){var rr=te.getBoundingClientRect();window.__vpPinEl=te;VideoBridge.playInline(a.href,rr.left,rr.top,rr.width,rr.height);}"
            + "else{VideoBridge.play(a.href);}"
            + "},true);"
            + "})();";

    // YouTube के नीचे वाले बटन (होम / Shorts / सब्सक्रिप्शन / आप) हटाने की स्टाइल; उनकी जगह VIP बार है
    private static final String HIDE_NAV =
            "(function(){try{var id='vp-nonav';if(document.getElementById(id))return;"
            + "var st=document.createElement('style');st.id=id;"
            + "st.textContent='ytm-pivot-bar-renderer,.pivot-bar,.pivot-bar-wrapper{display:none!important}';"
            + "(document.head||document.documentElement).appendChild(st);}catch(x){}})();";

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
    // true = Shorts का बैनर फेल हो तो वजह स्क्रीन पर छोटे संदेश में दिखे (जाँच के बाद false कर दें)
    private static final boolean SHORTS_DEBUG_TOAST = false;
    // true = फुलस्क्रीन से लौटते ही YouTube पेज ताज़ा लोड होता है (प्लेयर के पीछे, दिखता नहीं)
    // ताकि वह ठीक वैसा खुले जैसा शुरू में खुलता है, बाईं तरफ़ खिसका हुआ न रहे
    private static final boolean RELOAD_YT_AFTER_FULLSCREEN = true;

    // Unity Ads (सिर्फ़ वीडियो प्लेयर के नीचे बैनर)
    private static final String UNITY_GAME_ID = "800411568";
    private static final String BANNER_PLACEMENT = "BP_Banner_Android";
    // रिवॉर्डेड वीडियो की Placement ID (Unity डैशबोर्ड में जो नाम हो वही यहाँ डालें)
    private static final String REWARDED_PLACEMENT = "BP_Rewarded_Android";
    // false = असली विज्ञापन (टेस्ट के लिए true)
    private static final boolean UNITY_TEST_MODE = false;

    private int mode = MODE_NONE;
    private SpeechRecognizer recognizer;
    private FrameLayout bannerHolder;
    private LinearLayout bannerBox;
    private boolean adsReady;
    private boolean rewReady, rewLoading, rewShowing;
    // ऐप खोलने पर एक बार रिवॉर्डेड पूरा देख लिया तो true (ऐप बंद करके खोलने पर फिर false)
    private boolean rewardedDone;      // VIP 1 या उससे ऊपर
    private int vipLevel;               // 0..VIP_MAX
    private static final int VIP_MAX = 6;
    private static final boolean VIP_PERSIST = true;   // true करने पर VIP ऐप बंद होने के बाद भी याद रहे
    private static final int VIP_CARD_H = 46;           // dp, नीचे का VIP कार्ड
    // ऐप खुलने के बाद कितनी बार फुलस्क्रीन हुआ; FREE_FULLSCREENS बार मुफ़्त, उसके बाद रिवॉर्डेड ज़रूरी
    private static final int FREE_FULLSCREENS = 5;
    private int fsCount;
    private TextView rewBtn;
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
    private WebView ytWeb;       // अभी जो YouTube वेबव्यू सामने है (होम या Shorts)
    private WebView homeWeb;     // होम पेज वाला वेबव्यू: Shorts के दौरान नीचे जैसा का तैसा रहता है

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

        sensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        proxSensor = sensorMgr == null ? null : sensorMgr.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        loadVip();   // VIP, चुने फीचर, 24 घंटे की जाँच (पहले), फिर स्विच की हालत
        waveOn = proxSensor != null && vipHas(F_WAVE)
                && getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_WAVE, false);
        musicOn = vipHas(F_MUSIC)
                && getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_MUSIC, false);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        ytWeb = new WebView(this);
        ytWeb.setBackgroundColor(Color.WHITE);
        root.addView(ytWeb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildLoadingView();
        loadView.setVisibility(View.GONE);   // ऐप खुलते समय लोडिंग नहीं
        buildShortsBanner();
        buildPlayerLayer();
        bindMusicControls();
        registerPipReceiver();
        setContentView(root);
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int oldL, int oldT, int oldR, int oldB) {
                if (mode == MODE_MINI && !fsOn && !inPip
                        && (r - l != oldR - oldL || b - t != oldB - oldT)) {
                    placeMini();
                }
            }
        });
        startOrientationWatch();
        initAds();
        applyChrome();
        // प्लेयर का पेज पहले से तैयार रखो (बैकग्राउंड में), ताकि वीडियो टैप पर तुरंत खुले
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    playerHtml();
                } catch (Throwable ignored) {
                }
            }
        }).start();

        homeWeb = ytWeb;
        configureYt(ytWeb);
        CookieManager cm = CookieManager.getInstance();

        forceLightCookie(cm);
        ytWeb.loadUrl(HOME);
        root.postDelayed(urlPoll, 1000);
        handleShare(getIntent());
    }

    // ------------------------------------------------------------------
    // लोडिंग / नेटवर्क स्क्रीन: पेज ठीक से आने तक सुंदर स्क्रीन, काली/खाली स्क्रीन नहीं
    // ------------------------------------------------------------------
    private FrameLayout loadView;
    private TextView loadTitle, loadSub, loadRetry, loadLogo;
    private ProgressBar loadBar;
    private ObjectAnimator loadPulse;
    private boolean ytLoadFailed;
    private String failedUrl;
    // ऐप पहली बार खुलते समय लोडिंग स्क्रीन नहीं दिखानी (सीधे ऐप दिखे)
    private boolean firstPageLoad = true;
    // बैक दबाने पर लोडिंग कम-से-कम इतनी देर रहे, ताकि काली स्क्रीन की झलक न दिखे
    private static final int BACK_MIN_MS = 500;
    private static final int BACK_MAX_MS = 1200;
    private long holdLoadUntil;
    private boolean backWait;   // बैक के बाद सिर्फ़ इतिहास बदला (पूरा पेज नहीं) -> जल्दी हटाओ

    private final Runnable backHide = new Runnable() {
        @Override
        public void run() {
            holdLoadUntil = 0;
            backWait = false;
            if (!ytLoadFailed) hideLoading();
        }
    };

    /** बैक दबते ही लोडिंग दिखाओ; पेज तैयार होते ही (या अधिकतम BACK_MAX_MS बाद) हट जाएगी */
    private void showBackLoading() {
        showLoading();
        holdLoadUntil = SystemClock.uptimeMillis() + BACK_MIN_MS;
        backWait = true;
        root.removeCallbacks(backHide);
        root.postDelayed(backHide, BACK_MAX_MS);
    }

    /** पेज तैयार होने पर लोडिंग हटाओ (बैक वाली लोडिंग हो तो कम-से-कम समय पूरा होने के बाद) */
    private void hideLoadingSmart() {
        firstPageLoad = false;
        long wait = holdLoadUntil - SystemClock.uptimeMillis();
        if (holdLoadUntil != 0 && wait > 0) {
            root.removeCallbacks(backHide);
            root.postDelayed(backHide, wait);
            return;
        }
        holdLoadUntil = 0;
        root.removeCallbacks(backHide);
        hideLoading();
    }

    private final Runnable loadSlow = new Runnable() {
        @Override
        public void run() {
            if (loadView != null && loadView.getVisibility() == View.VISIBLE && !ytLoadFailed) {
                loadSub.setText("नेटवर्क थोड़ा धीमा लग रहा है… कृपया रुकिए");
                loadRetry.setVisibility(View.VISIBLE);
            }
        }
    };

    private final Runnable autoRetry = new Runnable() {
        @Override
        public void run() {
            if (ytLoadFailed && loadView != null && loadView.getVisibility() == View.VISIBLE) {
                retryLoad();
            }
        }
    };

    private void buildLoadingView() {
        loadView = new FrameLayout(this);
        loadView.setClickable(true);   // लोडिंग के समय पीछे के पेज पर टच न जाए
        loadView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFFFEDEA}));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        loadLogo = new TextView(this);
        loadLogo.setText("▶");
        loadLogo.setTextColor(Color.WHITE);
        loadLogo.setTextSize(30);
        loadLogo.setGravity(Gravity.CENTER);
        loadLogo.setElevation(dp(6));
        GradientDrawable lg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF1A1A, 0xFFD50000});
        lg.setCornerRadius(dp(22));
        loadLogo.setBackground(lg);
        col.addView(loadLogo, new LinearLayout.LayoutParams(dp(84), dp(84)));

        loadTitle = new TextView(this);
        loadTitle.setTextColor(0xFF212121);
        loadTitle.setTextSize(19);
        loadTitle.setTypeface(Typeface.DEFAULT_BOLD);
        loadTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(26);
        col.addView(loadTitle, tl);

        loadSub = new TextView(this);
        loadSub.setTextColor(0xFF757575);
        loadSub.setTextSize(14);
        loadSub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(8);
        col.addView(loadSub, sl);

        loadBar = new ProgressBar(this);
        loadBar.setIndeterminateTintList(ColorStateList.valueOf(0xFFFF0000));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(34), dp(34));
        bl.topMargin = dp(22);
        col.addView(loadBar, bl);

        loadRetry = new TextView(this);
        loadRetry.setText("🔄  फिर कोशिश करें");
        loadRetry.setTextColor(Color.WHITE);
        loadRetry.setTextSize(15);
        loadRetry.setTypeface(Typeface.DEFAULT_BOLD);
        loadRetry.setGravity(Gravity.CENTER);
        loadRetry.setPadding(dp(26), dp(12), dp(26), dp(12));
        GradientDrawable rg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFF3D00, 0xFFFF9100});
        rg.setCornerRadius(dp(24));
        loadRetry.setBackground(rg);
        loadRetry.setElevation(dp(4));
        loadRetry.setVisibility(View.GONE);
        loadRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retryLoad();
            }
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = dp(22);
        col.addView(loadRetry, rl);

        loadView.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(loadView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loadPulse = ObjectAnimator.ofPropertyValuesHolder(loadLogo,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.14f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.14f));
        loadPulse.setDuration(700);
        loadPulse.setRepeatCount(ObjectAnimator.INFINITE);
        loadPulse.setRepeatMode(ObjectAnimator.REVERSE);
        loadPulse.setInterpolator(new LinearInterpolator());
    }

    private void showLoading() {
        if (loadView == null) return;
        loadTitle.setText("लोड हो रहा है…");
        loadSub.setText("नेटवर्क जाँच रहे हैं, आपका पेज तैयार हो रहा है");
        loadBar.setVisibility(View.VISIBLE);
        loadRetry.setVisibility(View.GONE);
        loadView.setVisibility(View.VISIBLE);
        if (!loadPulse.isStarted()) loadPulse.start();
        root.removeCallbacks(autoRetry);
        root.removeCallbacks(loadSlow);
        root.postDelayed(loadSlow, 10000);
    }

    private void hideLoading() {
        if (loadView == null || loadView.getVisibility() != View.VISIBLE) return;
        root.removeCallbacks(loadSlow);
        root.removeCallbacks(autoRetry);
        loadPulse.cancel();
        loadView.setVisibility(View.GONE);
    }

    private void showLoadError(String url) {
        if (loadView == null) return;
        ytLoadFailed = true;
        failedUrl = url;
        loadTitle.setText("इंटरनेट नहीं चल रहा 📡");
        loadSub.setText("कनेक्शन जाँचिए, हम अपने-आप फिर कोशिश कर रहे हैं");
        loadBar.setVisibility(View.GONE);
        loadRetry.setVisibility(View.VISIBLE);
        loadView.setVisibility(View.VISIBLE);
        if (!loadPulse.isStarted()) loadPulse.start();
        root.removeCallbacks(loadSlow);
        root.removeCallbacks(autoRetry);
        root.postDelayed(autoRetry, 5000);
    }

    private void retryLoad() {
        if (ytWeb == null) return;   // स्क्रीन बंद हो चुकी हो तो यह देरी वाला टास्क कुछ न करे
        ytLoadFailed = false;
        showLoading();
        ytWeb.loadUrl(failedUrl != null ? failedUrl : HOME);
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
    // रिवॉर्डेड वीडियो (ऐप खोलने पर सिर्फ़ एक बार) -> उसके बाद फुलस्क्रीन खुलता है
    // ------------------------------------------------------------------
    private void loadRewarded() {
        if (!adsReady || rewReady || rewLoading || vipLevel >= VIP_MAX) return;
        rewLoading = true;
        try {
            UnityAds.load(REWARDED_PLACEMENT, new IUnityAdsLoadListener() {
                @Override
                public void onUnityAdsAdLoaded(String placementId) {
                    rewLoading = false;
                    rewReady = true;
                }

                @Override
                public void onUnityAdsFailedToLoad(String placementId,
                        UnityAds.UnityAdsLoadError error, String message) {
                    rewLoading = false;
                    Log.e("UnityRewarded", "load failed: " + message);
                    // 30 सेकंड बाद फिर कोशिश
                    root.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            loadRewarded();
                        }
                    }, 30000);
                }
            });
        } catch (Throwable t) {
            rewLoading = false;
        }
    }

    /** फुलस्क्रीन (वर्गाकार) बटन: रिवॉर्डेड देख चुके हों तो सीधे फुलस्क्रीन, वरना नोटिस (वीडियो चलता रहता है) */
    private void requestFullscreen() {
        if (mode == MODE_NONE || fsOn || rewShowing) return;
        if (vipHas(F_FULL)) {
            setFullscreen(true);
            return;
        }
        showFeatureGate(F_FULL);      // कोई फ़्री बार नहीं: VIP में चुना हो तभी फुलस्क्रीन
    }

    /** thenFull = true: पूरा देखने के बाद अपने-आप फुलस्क्रीन; false: सिर्फ़ ऊपर वाले बटन से देखा */
    private void startRewarded(final boolean thenFull) {
        if (rewShowing) return;
        if (vipLevel >= VIP_MAX) {
            Toast.makeText(this, "👑 आप सबसे ऊँचे VIP " + VIP_MAX + " पर हैं", Toast.LENGTH_SHORT).show();
            if (thenFull && mode != MODE_NONE && !fsOn) setFullscreen(true);
            return;
        }
        if (!adsReady || !rewReady) {
            Toast.makeText(this, "विज्ञापन तैयार हो रहा है, कुछ सेकंड बाद फिर दबाएँ", Toast.LENGTH_SHORT).show();
            loadRewarded();
            return;
        }
        rewShowing = true;
        rewReady = false;
        try {
            UnityAds.show(this, REWARDED_PLACEMENT, new UnityAdsShowOptions(),
                    new IUnityAdsShowListener() {
                        @Override
                        public void onUnityAdsShowFailure(String placementId,
                                UnityAds.UnityAdsShowError error, String message) {
                            Log.e("UnityRewarded", "show failed: " + message);
                            afterRewarded(false, true, thenFull);   // विज्ञापन न चला तो रोकें नहीं
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
                            // पूरा देखा -> रिवॉर्ड मिला; बीच में छोड़ा -> कुछ नहीं, फिर देखना होगा
                            boolean done = state == UnityAds.UnityAdsShowCompletionState.COMPLETED;
                            afterRewarded(done, false, thenFull);
                        }
                    });
        } catch (Throwable t) {
            afterRewarded(false, true, thenFull);
        }
    }

    private void afterRewarded(final boolean done, final boolean failed, final boolean thenFull) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rewShowing = false;
                boolean wasWave = waveAfterReward;
                waveAfterReward = false;
                boolean wasMusic = musicAfterReward;
                musicAfterReward = false;
                if (done) {
                    vipExpireIfNeeded();
                    if (vipLevel == 0) vipStart = System.currentTimeMillis();   // 24 घंटे यहीं से गिनें
                    vipLevel = Math.min(VIP_MAX, vipLevel + 1);
                    rewardedDone = vipLevel >= 1;
                    saveVip();
                    updateRewBtn();
                    updateVipCard();
                    refreshVipPage();
                    Toast.makeText(MainActivity.this, "🎉 VIP " + vipLevel + " मिल गया!\nअब VIP पेज पर कोई एक फीचर चुनें",
                            Toast.LENGTH_LONG).show();
                    loadRewarded();                       // अगले VIP के लिए
                } else {
                    loadRewarded();                       // अगली बार के लिए
                }
                if ((done || failed) && thenFull && mode != MODE_NONE && !fsOn) setFullscreen(true);
            }
        });
    }

    private void updateRewBtn() {
        if (rewBtn == null) return;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                vipLevel >= 1 ? new int[]{0xFF1B8F3A, 0xFF2ECC71} : new int[]{0xFFB8860B, 0xFFFFD54F});
        g.setCornerRadius(dp(20));
        rewBtn.setBackground(g);
        rewBtn.setTextColor(vipLevel >= 1 ? Color.WHITE : 0xFF3A2A00);
        rewBtn.setText(vipLevel <= 0 ? "👑  VIP पाएँ" : "👑  VIP " + vipLevel + (vipLevel >= VIP_MAX ? " ✅" : ""));
    }

    // ------------------------------------------------------------------
    // VIP: हर रिवॉर्डेड वीडियो पूरा देखने पर अगला VIP (1 से 5), हर VIP पर नया फीचर
    // ------------------------------------------------------------------
    private LinearLayout vipCard;
    private TextView vipCardTxt, vipCardGo;
    private android.app.Dialog vipDialog;
    private android.app.AlertDialog featureDialog;
    // VIP पेज अब स्वाइप करने वाले अलग-अलग पन्नों में: हर VIP का अपना पन्ना, टाइटल/विवरण/स्विच वहीं
    private androidx.viewpager2.widget.ViewPager2 vipPager;
    private VipPagerAdapter vipPagerAdapter;
    private TextView vipPageHint;
    private LinearLayout vipDots;
    private TextView[] vipDotViews;
    private TextView vipRewardBtn;   // "विज्ञापन देखकर अगला VIP पाएँ" — पन्नों के नीचे, सबके लिए साझा

    // फीचर नंबर (VIP पेज में इसी क्रम में दिखते हैं)
    private static final int F_WAVE = 1, F_MUSIC = 2, F_INLINE = 3, F_MINIMIZE = 4, F_FULL = 5, F_BROWSER = 6;
    private static final long VIP_LIFE_MS = 24L * 60L * 60L * 1000L;   // 24 घंटे में सारे VIP ख़त्म
    private int vipPicks;      // चुने हुए फीचर (bit f = फीचर f)
    private long vipStart;     // पहला VIP मिलने का समय (ms)

    private static final String[] VIP_TITLE = {
            "✋ हाथ से अगला Shorts",
            "🎵 म्यूज़िक मोड",
            "▶️ पेज पर ही वीडियो देखें",
            "🪟 मिनिमाइज करके चलाएँ",
            "⛶ फुल स्क्रीन",
            "🌐 वेब ब्राउज़र"
    };
    private static final String[] VIP_DESC = {
            "हाथ लहराकर (फ़ोन के सेंसर के ऊपर) अगला Shorts चलाएँ, उँगली से स्वाइप की ज़रूरत नहीं।",
            "म्यूज़िक स्विच चालू करें: वीडियो की जगह थंबनेल दिखेगा और सिर्फ़ गाना बजेगा। स्क्रीन बंद या ऐप नीचे होने पर भी चलता रहेगा।",
            "YouTube पेज पर जिस वीडियो पर क्लिक करें, वह वहीं उसी थंबनेल पर चलेगा। वहीं पर नीचे की पट्टी से वीडियो आगे-पीछे (स्क्रॉल) भी कर सकते हैं।",
            "ऐप को मिनिमाइज करके वीडियो अपनी होम स्क्रीन और दूसरी ऐप्स के ऊपर छोटी विंडो में चलाएँ। उसे खिसकाएँ, बड़ा-छोटा करें, रोकें या बंद करें।",
            "वीडियो को पूरी स्क्रीन में आड़ा करके (फुल स्क्रीन) देखें।",
            "ऐप के अंदर ही वेब ब्राउज़र खोलें — कोई भी वेबसाइट खोजें या URL डालकर देखें, वीडियो देखते हुए बाहर जाने की ज़रूरत नहीं।"
    };

    private String vipShort(int lv) {
        return (lv >= 1 && lv <= VIP_MAX) ? VIP_TITLE[lv - 1] : "";
    }

    /** यह फीचर इस VIP में चुना हुआ है? (24 घंटे पूरे हो गए हों तो पहले सब ख़त्म) */
    private boolean vipHas(int f) {
        vipExpireIfNeeded();
        return vipLevel > 0 && (vipPicks & (1 << f)) != 0;
    }

    private int vipPicksLeft() {
        return Math.max(0, vipLevel - Integer.bitCount(vipPicks));
    }

    private void saveVip() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("vip_level", vipLevel)
                .putInt("vip_picks", vipPicks)
                .putLong("vip_start", vipStart)
                .apply();
    }

    private void loadVip() {
        android.content.SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        vipLevel = Math.max(0, Math.min(VIP_MAX, sp.getInt("vip_level", 0)));
        vipPicks = sp.getInt("vip_picks", 0);
        vipStart = sp.getLong("vip_start", 0L);
        rewardedDone = vipLevel >= 1;
        vipExpireIfNeeded();
    }

    /** 24 घंटे पूरे (या फ़ोन की घड़ी पीछे की गई) -> सारे VIP और चुने फीचर ख़त्म. true = अभी ख़त्म हुए */
    private boolean vipExpireIfNeeded() {
        if (vipLevel <= 0 && vipPicks == 0) return false;
        long age = System.currentTimeMillis() - vipStart;
        if (vipStart > 0 && age >= 0 && age < VIP_LIFE_MS) return false;
        vipLevel = 0;
        vipPicks = 0;
        vipStart = 0;
        rewardedDone = false;
        saveVip();
        onVipEnded();
        return true;
    }

    private void onVipEnded() {
        if (root == null) return;       // ऐप अभी बन ही रही है
        if (waveSwitch != null && waveSwitch.isChecked()) waveSwitch.setChecked(false);
        if (musicSwitch != null && musicSwitch.isChecked()) musicSwitch.setChecked(false);
        updateRewBtn();
        updateVipCard();
        refreshVipPage();
        Toast.makeText(this, "⏰ 24 घंटे पूरे हुए, सारे VIP ख़त्म हो गए", Toast.LENGTH_LONG).show();
    }

    private final Runnable vipTick = new Runnable() {
        @Override
        public void run() {
            vipExpireIfNeeded();
            if (root != null) root.postDelayed(this, 30000);
        }
    };

    /** फीचर इस्तेमाल करने की कोशिश पर, अगर VIP में चुना नहीं है */
    private void showFeatureGate(final int f) {
        if (isFinishing() || (featureDialog != null && featureDialog.isShowing())) return;
        String why;
        if (vipLevel <= 0) {
            why = "अभी आप VIP नहीं हैं। एक रिवॉर्डेड वीडियो पूरा देखकर VIP 1 पाएँ, फिर यह फीचर चुनें।";
        } else if (vipPicksLeft() > 0) {
            why = "आपके पास " + vipPicksLeft() + " चुनाव बचा है। VIP पेज पर जाकर इसे चुनें।";
        } else {
            why = "आपके VIP " + vipLevel + " के सारे चुनाव इस्तेमाल हो चुके हैं। यह फीचर चाहिए तो VIP बढ़ाएँ।";
        }
        featureDialog = new android.app.AlertDialog.Builder(this)
                .setMessage(VIP_TITLE[f - 1] + " चलाने के लिए इसे VIP में चुनना होगा।\n\n" + why)
                .setPositiveButton("👑 VIP देखें", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        showVipPage(f);   // सीधे इसी फीचर के VIP पन्ने पर
                    }
                })
                .setNegativeButton("अभी नहीं", null)
                .show();
    }

    /** नीचे का VIP बार (YouTube के नीचे वाले बटनों की जगह) */
    private boolean vipCardShown() {
        return vipCard != null && mode == MODE_NONE && ytWeb != null && ytWeb == homeWeb
                && !onShorts && !ytLocked && !fsOn;
    }

    private void updateVipCard() {
        if (vipCard == null) return;
        vipCard.setVisibility(vipCardShown() ? View.VISIBLE : View.GONE);
        if (vipCardTxt != null) {
            vipCardTxt.setText(vipLevel <= 0 ? "👑  VIP पाएँ" : "👑  VIP " + vipLevel);
            vipCardTxt.setTextColor(vipLevel <= 0 ? 0xFF222222 : 0xFF1B8F3A);
        }
        if (vipCardGo != null) {
            vipCardGo.setText(vipLevel > 0 && vipPicksLeft() > 0 ? "फीचर चुनें  ›" : "VIP बढ़ाएँ  ›");
        }
        applyYtMargin();
    }

    private void buildVipCard() {
        vipCard = new LinearLayout(this);
        vipCard.setOrientation(LinearLayout.HORIZONTAL);
        vipCard.setGravity(Gravity.CENTER_VERTICAL);
        vipCard.setPadding(dp(16), 0, dp(12), 0);
        GradientDrawable line = new GradientDrawable();
        line.setColor(0xFFDDDDDD);
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{line, white});
        ld.setLayerInset(1, 0, dp(1), 0, 0);
        vipCard.setBackground(ld);
        vipCard.setVisibility(View.GONE);
        vipCardTxt = new TextView(this);
        vipCardTxt.setTextColor(0xFF222222);
        vipCardTxt.setTextSize(16);
        vipCardTxt.setTypeface(Typeface.DEFAULT_BOLD);
        vipCard.addView(vipCardTxt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        vipCardGo = new TextView(this);
        vipCardGo.setTextColor(Color.WHITE);
        vipCardGo.setTextSize(13);
        vipCardGo.setTypeface(Typeface.DEFAULT_BOLD);
        vipCardGo.setGravity(Gravity.CENTER);
        vipCardGo.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF1B8F3A);
        gb.setCornerRadius(dp(18));
        vipCardGo.setBackground(gb);
        vipCard.addView(vipCardGo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        vipCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVipPage();
            }
        });
        root.addView(vipCard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(VIP_CARD_H), Gravity.BOTTOM));
        updateVipCard();
    }

    private void showVipPage() {
        showVipPage(0);
    }

    /** jumpToLevel: 1..VIP_MAX हो तो सीधे उसी VIP के पन्ने पर खुले/जाए; 0 हो तो मौजूदा VIP के पन्ने पर */
    private void showVipPage(int jumpToLevel) {
        if (isFinishing()) return;
        vipExpireIfNeeded();
        if (vipDialog != null && vipDialog.isShowing()) {
            if (jumpToLevel >= 1 && jumpToLevel <= VIP_MAX && vipPager != null) {
                vipPager.setCurrentItem(jumpToLevel - 1, true);
            }
            return;
        }
        vipDialog = new android.app.Dialog(this, android.R.style.Theme_Light_NoTitleBar);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.WHITE);
        outer.setPadding(dp(16), dp(20), dp(16), dp(16));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("👑  VIP मेंबर");
        title.setTextColor(0xFF111111);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextColor(0xFF111111);
        x.setTextSize(22);
        x.setGravity(Gravity.CENTER);
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (vipDialog != null) vipDialog.dismiss();
            }
        });
        head.addView(x, new LinearLayout.LayoutParams(dp(44), dp(44)));
        outer.addView(head);

        vipPageHint = new TextView(this);
        vipPageHint.setTextColor(0xFF333333);
        vipPageHint.setTextSize(15);
        vipPageHint.setPadding(0, dp(10), 0, dp(10));
        outer.addView(vipPageHint);

        vipPagerAdapter = new VipPagerAdapter();
        vipPager = new androidx.viewpager2.widget.ViewPager2(this);
        vipPager.setAdapter(vipPagerAdapter);
        outer.addView(vipPager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));

        vipDots = new LinearLayout(this);
        vipDots.setOrientation(LinearLayout.HORIZONTAL);
        vipDots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsLp.topMargin = dp(8);
        outer.addView(vipDots, dotsLp);
        vipDotViews = new TextView[VIP_MAX];
        for (int i = 0; i < VIP_MAX; i++) {
            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextSize(12);
            dot.setPadding(dp(3), 0, dp(3), 0);
            vipDotViews[i] = dot;
            vipDots.addView(dot);
        }

        TextView swipeHint = new TextView(this);
        swipeHint.setText("‹   स्वाइप करके अगला VIP देखें   ›");
        swipeHint.setTextColor(0xFF999999);
        swipeHint.setTextSize(11);
        swipeHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shLp.topMargin = dp(4);
        outer.addView(swipeHint, shLp);

        vipRewardBtn = new TextView(this);
        vipRewardBtn.setGravity(Gravity.CENTER);
        vipRewardBtn.setTextSize(16);
        vipRewardBtn.setTypeface(Typeface.DEFAULT_BOLD);
        vipRewardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (vipLevel < VIP_MAX) startRewarded(false);
            }
        });
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        rbLp.topMargin = dp(14);
        outer.addView(vipRewardBtn, rbLp);
        updateVipRewardBtn();

        vipPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateVipDots(position);
            }
        });

        vipDialog.setContentView(outer);
        updateVipPageHint();

        int start;
        if (jumpToLevel >= 1 && jumpToLevel <= VIP_MAX) start = jumpToLevel - 1;
        else start = vipLevel <= 0 ? 0 : Math.min(vipLevel, VIP_MAX) - 1;
        updateVipDots(start);
        vipDialog.show();
        vipPager.setCurrentItem(start, false);
    }

    private void updateVipPageHint() {
        if (vipPageHint == null) return;
        String note = "\nएक बार चुना हुआ फीचर बदला नहीं जा सकता। 24 घंटे बाद सारे VIP ख़त्म हो जाएँगे।";
        if (vipLevel <= 0) {
            vipPageHint.setText("अभी आप VIP नहीं हैं। एक रिवॉर्डेड वीडियो पूरा देखने पर VIP 1 मिलेगा, जिसमें नीचे के फीचर में से कोई 1 चुन सकते हैं। VIP 2 पर 2, ऐसे ही VIP "
                    + VIP_MAX + " तक।" + note);
        } else {
            long left = Math.max(0L, VIP_LIFE_MS - (System.currentTimeMillis() - vipStart));
            long hh = left / 3600000L, mm = (left / 60000L) % 60L;
            vipPageHint.setText("आप VIP " + vipLevel + " हैं 🎉   ·   बचे चुनाव: " + vipPicksLeft()
                    + "   ·   ⏳ " + hh + "घं " + mm + "मि बाद सारे VIP ख़त्म" + note);
        }
    }

    private void updateVipDots(int active) {
        if (vipDotViews == null) return;
        for (int i = 0; i < vipDotViews.length; i++) {
            boolean unlocked = vipLevel >= (i + 1);
            vipDotViews[i].setTextColor(i == active ? 0xFF1B8F3A : (unlocked ? 0xFFBBBBBB : 0xFFDDDDDD));
        }
    }

    private void refreshVipPage() {
        if (vipDialog == null || !vipDialog.isShowing()) return;
        updateVipPageHint();
        updateVipRewardBtn();
        if (vipPagerAdapter != null) vipPagerAdapter.notifyDataSetChanged();
        if (vipPager != null) updateVipDots(vipPager.getCurrentItem());
    }

    private void updateVipRewardBtn() {
        if (vipRewardBtn == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(26));
        if (vipLevel >= VIP_MAX) {
            vipRewardBtn.setText("👑 आप सबसे ऊँचे VIP " + VIP_MAX + " पर हैं");
            vipRewardBtn.setTextColor(0xFF666666);
            bg.setColor(0xFFE0E0E0);
        } else {
            vipRewardBtn.setText("🎁  विज्ञापन देखकर VIP " + (vipLevel + 1) + " पाएँ");
            vipRewardBtn.setTextColor(Color.WHITE);
            bg.setColor(0xFF1B8F3A);
        }
        vipRewardBtn.setBackground(bg);
    }

    /** हर VIP level का अपना स्वाइप करने वाला पन्ना (ViewPager2 का adapter) */
    private class VipPagerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<VipPagerAdapter.VH> {
        class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            FrameLayout box;
            VH(FrameLayout v) {
                super(v);
                box = v;
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout box = new FrameLayout(MainActivity.this);
            box.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(
                    androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT,
                    androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT));
            return new VH(box);
        }

        @Override
        public void onBindViewHolder(VH h, int position) {
            h.box.removeAllViews();
            h.box.addView(buildVipPageView(position + 1));
        }

        @Override
        public int getItemCount() {
            return VIP_MAX;
        }
    }

    /** एक VIP level का पूरा पन्ना: title + description + चालू/बंद स्विच (ब्राउज़र के लिए स्विच = खोलें) */
    private View buildVipPageView(final int f) {
        final int green = 0xFF1B8F3A;
        boolean picked = vipHas(f);
        boolean canPick = !picked && vipPicksLeft() > 0;   // कोई भी क्रम चल सकता है, vipLevel सिर्फ़ कुल चुनाव तय करता है
        boolean unlocked = picked || canPick;   // सिर्फ़ तब 🔒 जब चुनाव बचे ही न हों

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(picked ? 0xFFE6F6EC : (canPick ? Color.WHITE : 0xFFF2F2F2));
        cg.setCornerRadius(dp(14));
        cg.setStroke(dp(picked ? 2 : 1), picked ? green : (canPick ? 0xFFBBBBBB : 0xFFDDDDDD));
        card.setBackground(cg);

        TextView h = new TextView(this);
        h.setText("VIP " + f + "   " + (picked ? "✅ " : (unlocked ? "" : "🔒 ")) + VIP_TITLE[f - 1]);
        h.setTextColor(picked ? green : (canPick ? 0xFF111111 : 0xFF888888));
        h.setTextSize(18);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(h);

        TextView d = new TextView(this);
        d.setText(VIP_DESC[f - 1]);
        d.setTextColor(picked ? 0xFF2E6B45 : (canPick ? 0xFF555555 : 0xFF999999));
        d.setTextSize(14);
        d.setPadding(0, dp(8), 0, dp(10));
        card.addView(d);

        TextView info = new TextView(this);
        info.setTextSize(12);
        info.setPadding(0, 0, 0, dp(14));
        if (picked) {
            info.setTextColor(green);
            info.setText(f == F_BROWSER ? "चालू है — नीचे का स्विच ऑन करके ब्राउज़र खोलें" : "चालू ✔");
        } else if (canPick) {
            info.setTextColor(0xFF555555);
            info.setText("नीचे का स्विच ऑन करके इसे चुनें   ·   " + vipPicksLeft() + " चुनाव बचे (किसी भी क्रम में चुन सकते हैं)");
        } else {
            info.setTextColor(0xFF999999);
            info.setText("इस VIP के सारे चुनाव हो चुके, VIP बढ़ाएँ");
        }
        card.addView(info);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView swLabel = new TextView(this);
        swLabel.setText(f == F_BROWSER ? "🌐  ब्राउज़र खोलें" : "✔  चालू रखें");
        swLabel.setTextColor(0xFF333333);
        swLabel.setTextSize(14);
        swLabel.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(swLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(this);
        int[][] sst = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
        sw.setThumbTintList(new ColorStateList(sst, new int[]{0xFFFFFFFF, 0xFFDDDDDD}));
        sw.setTrackTintList(new ColorStateList(sst, new int[]{green, 0x66CCCCCC}));
        sw.setScaleX(0.85f);
        sw.setScaleY(0.85f);
        boolean swOn;
        if (f == F_WAVE) swOn = picked && waveOn;
        else if (f == F_MUSIC) swOn = picked && musicOn;
        else if (f == F_BROWSER) swOn = false;   // यह स्विच सिर्फ़ ब्राउज़र खोलने के लिए है, स्थायी हालत नहीं
        else swOn = picked;
        sw.setChecked(swOn);
        sw.setEnabled(canPick || picked);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton bv, boolean isOn) {
                if (!bv.isPressed()) return;   // सिर्फ़ असली टैप पर चले, दोबारा-बनने पर नहीं
                if (!vipHas(f)) {
                    bv.setChecked(false);
                    vipPickConfirm(f);   // vipPickConfirm खुद जाँच लेता है कि चुनाव अभी भी बचा है या नहीं
                    return;
                }
                if (f == F_WAVE) {
                    setWave(isOn);
                    refreshVipPage();
                } else if (f == F_MUSIC) {
                    setMusic(isOn);
                    refreshVipPage();
                } else if (f == F_BROWSER) {
                    if (isOn) {
                        bv.setChecked(false);
                        openBrowserScreen();
                    }
                } else {
                    bv.setChecked(true);   // एक बार चुनने पर यह फीचर हमेशा चालू रहता है
                }
            }
        });
        row.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(row);

        ScrollView sv = new ScrollView(this);
        sv.addView(card, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout wrap = new FrameLayout(this);
        FrameLayout.LayoutParams svLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        svLp.leftMargin = dp(2);
        svLp.rightMargin = dp(2);
        svLp.topMargin = dp(2);
        svLp.bottomMargin = dp(2);
        wrap.addView(sv, svLp);
        return wrap;
    }

    // ------------------------------------------------------------------
    // ⭐ पसंदीदा (Favorite) गाने/वीडियो — जोड़ें, लिस्ट देखें, चलाएँ, हटाएँ
    // ------------------------------------------------------------------
    private org.json.JSONArray loadFavorites() {
        try {
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_FAVORITES, "[]");
            return new org.json.JSONArray(raw);
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    private void saveFavorites(org.json.JSONArray arr) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_FAVORITES, arr.toString()).apply();
    }

    private boolean isFavorite(String id) {
        org.json.JSONArray arr = loadFavorites();
        for (int i = 0; i < arr.length(); i++) {
            if (arr.optJSONObject(i) != null && id.equals(arr.optJSONObject(i).optString("id"))) return true;
        }
        return false;
    }

    private void addCurrentToFavorites() {
        if (curId == null || curId.length() == 0) {
            Toast.makeText(this, "अभी कोई गाना/वीडियो नहीं चल रहा", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isFavorite(curId)) {
            Toast.makeText(this, "यह पहले से पसंदीदा में है", Toast.LENGTH_SHORT).show();
            return;
        }
        org.json.JSONArray arr = loadFavorites();
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("id", curId);
            o.put("title", curTitle != null && curTitle.length() > 0 ? curTitle : curId);
            arr.put(o);
            saveFavorites(arr);
            Toast.makeText(this, "⭐ पसंदीदा में जोड़ा गया", Toast.LENGTH_SHORT).show();
            refreshFavoritesList();
        } catch (Exception ignored) {
        }
    }

    private void removeFavorite(String id) {
        org.json.JSONArray arr = loadFavorites();
        org.json.JSONArray out = new org.json.JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject o = arr.optJSONObject(i);
            if (o != null && !id.equals(o.optString("id"))) out.put(o);
        }
        saveFavorites(out);
        refreshFavoritesList();
    }

    private void showFavoritesDialog() {
        if (isFinishing()) return;
        if (favDialog == null) {
            favDialog = new android.app.Dialog(this, android.R.style.Theme_Light_NoTitleBar);
            LinearLayout outer = new LinearLayout(this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setBackgroundColor(Color.WHITE);
            outer.setPadding(dp(16), dp(20), dp(16), dp(16));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = new TextView(this);
            title.setText("⭐ मेरे पसंदीदा");
            title.setTextColor(0xFF111111);
            title.setTextSize(22);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView x = new TextView(this);
            x.setText("✕");
            x.setTextColor(0xFF111111);
            x.setTextSize(20);
            x.setGravity(Gravity.CENTER);
            x.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (favDialog != null) favDialog.dismiss();
                }
            });
            head.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
            outer.addView(head);

            TextView addBtn = new TextView(this);
            addBtn.setText("➕  अभी चल रहा गाना/वीडियो पसंदीदा में जोड़ें");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(14);
            addBtn.setGravity(Gravity.CENTER);
            addBtn.setPadding(dp(10), dp(12), dp(10), dp(12));
            GradientDrawable abg = new GradientDrawable();
            abg.setCornerRadius(dp(10));
            abg.setColor(0xFF1B8F3A);
            addBtn.setBackground(abg);
            addBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addCurrentToFavorites();
                }
            });
            LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            addLp.topMargin = dp(12);
            addLp.bottomMargin = dp(10);
            outer.addView(addBtn, addLp);

            ScrollView sv = new ScrollView(this);
            favListBox = new LinearLayout(this);
            favListBox.setOrientation(LinearLayout.VERTICAL);
            sv.addView(favListBox, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            outer.addView(sv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));

            favDialog.setContentView(outer);
        }
        refreshFavoritesList();
        favDialog.show();
    }

    private void refreshFavoritesList() {
        if (favListBox == null) return;
        favListBox.removeAllViews();
        org.json.JSONArray arr = loadFavorites();
        if (arr.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("अभी कोई पसंदीदा गाना/वीडियो नहीं जोड़ा गया है।");
            empty.setTextColor(0xFF999999);
            empty.setTextSize(13);
            empty.setPadding(dp(4), dp(16), dp(4), dp(16));
            favListBox.addView(empty);
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            final org.json.JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            final String id = o.optString("id");
            String ftitle = o.optString("title");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(10), dp(8), dp(10));
            GradientDrawable rb = new GradientDrawable();
            rb.setColor(0xFFF5F5F5);
            rb.setCornerRadius(dp(10));
            row.setBackground(rb);

            TextView t = new TextView(this);
            t.setText(ftitle);
            t.setTextColor(0xFF222222);
            t.setTextSize(13);
            t.setMaxLines(2);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView play = new TextView(this);
            play.setText("▶");
            play.setTextColor(0xFF1B8F3A);
            play.setTextSize(18);
            play.setPadding(dp(10), 0, dp(10), 0);
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (favDialog != null) favDialog.dismiss();
                    openPlayer("https://www.youtube.com/watch?v=" + id);
                }
            });
            row.addView(play);

            TextView del = new TextView(this);
            del.setText("🗑");
            del.setTextSize(16);
            del.setPadding(dp(6), 0, dp(4), 0);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeFavorite(id);
                }
            });
            row.addView(del);

            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.bottomMargin = dp(8);
            favListBox.addView(row, rLp);
        }
    }

    // ------------------------------------------------------------------
    // 🌐 VIP 6 — ऐप के अंदर वेब ब्राउज़र (Party Room वाले browser से लिया गया डिज़ाइन, यहाँ native WebView से)
    // ------------------------------------------------------------------
    private static final String BROWSER_HOME = "https://www.google.com/webhp?igu=1";
    private android.app.Dialog browserDialog;
    private WebView browserWeb;
    private android.widget.EditText browserUrlInput;
    private TextView browserBackBtn, browserFwdBtn;

    private void openBrowserScreen() {
        if (!vipHas(F_BROWSER)) {
            showFeatureGate(F_BROWSER);
            return;
        }
        if (vipDialog != null && vipDialog.isShowing()) vipDialog.dismiss();
        buildBrowserDialogIfNeeded();
        browserDialog.show();
    }

    private String browserProcessInput(String input) {
        if (input == null) return BROWSER_HOME;
        String t = input.trim();
        if (t.isEmpty()) return BROWSER_HOME;
        if (t.matches("^[a-zA-Z]+://.*")) return t;
        if (!t.contains(" ") && t.matches("^(www\\.)?[\\w-]+\\.[a-zA-Z]{2,}(/.*)?$")) return "https://" + t;
        try {
            return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(t, "UTF-8");
        } catch (Exception e) {
            return "https://www.google.com/search?q=" + t;
        }
    }

    private void browserGo() {
        if (browserWeb == null || browserUrlInput == null) return;
        browserWeb.loadUrl(browserProcessInput(browserUrlInput.getText().toString()));
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(browserUrlInput.getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    private void updateBrowserNav() {
        if (browserWeb == null) return;
        if (browserBackBtn != null) browserBackBtn.setAlpha(browserWeb.canGoBack() ? 1f : 0.4f);
        if (browserFwdBtn != null) browserFwdBtn.setAlpha(browserWeb.canGoForward() ? 1f : 0.4f);
    }

    private TextView addBrowserNavBtn(LinearLayout parent, String label, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(18);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF172033);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        parent.addView(b, lp);
        return b;
    }

    private void buildBrowserDialogIfNeeded() {
        if (browserDialog != null) return;
        browserDialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xFF050816);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView closeBtn = new TextView(this);
        closeBtn.setText("‹");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(26);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (browserDialog != null) browserDialog.dismiss();
            }
        });
        top.addView(closeBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        browserUrlInput = new android.widget.EditText(this);
        browserUrlInput.setHint("Search या website URL…");
        browserUrlInput.setHintTextColor(0x8AFFFFFF);
        browserUrlInput.setTextColor(Color.WHITE);
        browserUrlInput.setSingleLine(true);
        browserUrlInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        GradientDrawable ubg = new GradientDrawable();
        ubg.setColor(0x1AFFFFFF);
        ubg.setCornerRadius(dp(12));
        browserUrlInput.setBackground(ubg);
        browserUrlInput.setPadding(dp(12), 0, dp(12), 0);
        browserUrlInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                browserGo();
                return true;
            }
        });
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        top.addView(browserUrlInput, ulp);

        TextView goBtn = new TextView(this);
        goBtn.setText("Go");
        goBtn.setTextColor(Color.WHITE);
        goBtn.setTypeface(Typeface.DEFAULT_BOLD);
        goBtn.setGravity(Gravity.CENTER);
        GradientDrawable gbg = new GradientDrawable();
        gbg.setColor(0xFF16A34A);
        gbg.setCornerRadius(dp(12));
        goBtn.setBackground(gbg);
        goBtn.setPadding(dp(14), 0, dp(14), 0);
        goBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                browserGo();
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        glp.leftMargin = dp(6);
        top.addView(goBtn, glp);
        box.addView(top);

        browserWeb = new WebView(this);
        WebSettings bs = browserWeb.getSettings();
        bs.setJavaScriptEnabled(true);
        bs.setDomStorageEnabled(true);
        bs.setLoadWithOverviewMode(true);
        bs.setUseWideViewPort(true);
        bs.setSupportZoom(true);
        bs.setBuiltInZoomControls(true);
        bs.setDisplayZoomControls(false);
        browserWeb.setWebChromeClient(new WebChromeClient());
        browserWeb.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                if (browserUrlInput != null) browserUrlInput.setText(url);
                updateBrowserNav();
            }
        });
        box.addView(browserWeb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottom.setBackgroundColor(0xFF0F172A);
        browserBackBtn = addBrowserNavBtn(bottom, "◀", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (browserWeb != null && browserWeb.canGoBack()) browserWeb.goBack();
            }
        });
        browserFwdBtn = addBrowserNavBtn(bottom, "▶", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (browserWeb != null && browserWeb.canGoForward()) browserWeb.goForward();
            }
        });
        addBrowserNavBtn(bottom, "⟳", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (browserWeb != null) browserWeb.reload();
            }
        });
        addBrowserNavBtn(bottom, "🏠", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (browserWeb != null) browserWeb.loadUrl(BROWSER_HOME);
            }
        });
        box.addView(bottom);

        browserDialog.setContentView(box);
        browserDialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(android.content.DialogInterface d, int keyCode, android.view.KeyEvent event) {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    if (browserWeb != null && browserWeb.canGoBack()) {
                        browserWeb.goBack();
                        return true;
                    }
                }
                return false;
            }
        });
        browserWeb.loadUrl(BROWSER_HOME);
    }

    /** चुनने से पहले पक्का करना: एक बार चुना तो बदल नहीं सकते */
    private void vipPickConfirm(final int f) {
        if (vipHas(f)) return;
        if (vipPicksLeft() <= 0) {
            Toast.makeText(this, "VIP " + vipLevel + " के सारे चुनाव हो चुके, VIP बढ़ाएँ", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setMessage("\"" + VIP_TITLE[f - 1] + "\" चुनें?\n\nएक बार चुनने के बाद इसे बदल नहीं सकेंगे। "
                        + "24 घंटे बाद सारे VIP ख़त्म हो जाएँगे।")
                .setPositiveButton("✔ ठीक है", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (vipExpireIfNeeded() || vipPicksLeft() <= 0 || vipHas(f)) return;
                        vipPicks |= (1 << f);
                        saveVip();
                        updateVipCard();
                        refreshVipPage();
                        Toast.makeText(MainActivity.this, "✅ " + VIP_TITLE[f - 1] + " चालू हो गया",
                                Toast.LENGTH_SHORT).show();
                        if (f == F_WAVE && waveSwitch != null) waveSwitch.setChecked(true);
                        if (f == F_MUSIC && musicSwitch != null) musicSwitch.setChecked(true);
                    }
                })
                .setNegativeButton("रद्द", null)
                .show();
    }


    // ---- फुलस्क्रीन से पहले का नोटिस (वीडियो के ठीक नीचे) ----
    private LinearLayout fsNotice;

    private TextView noticeBtn(String text, int bg, int fg) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), dp(11), dp(8), dp(11));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(bg);
        b.setBackground(g);
        return b;
    }

    private void buildFsNotice() {
        fsNotice = new LinearLayout(this);
        fsNotice.setOrientation(LinearLayout.VERTICAL);
        fsNotice.setPadding(dp(16), dp(14), dp(16), dp(14));
        fsNotice.setClickable(true);
        fsNotice.setElevation(dp(10));
        fsNotice.setVisibility(View.GONE);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xFF1A1A1A);
        bg.setStroke(dp(1), 0x33FFFFFF);
        fsNotice.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("🎁  पहले रिवॉर्डेड वीडियो पूरा करें");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        fsNotice.addView(title);

        TextView msg = new TextView(this);
        msg.setText("आपके मुफ़्त 5 फुल स्क्रीन पूरे हो गए। अब एक रिवॉर्डेड वीडियो पूरा देखें। "
                + "यह ऐप खुले रहने तक सिर्फ़ एक बार देखना है, उसके बाद जितने चाहें वीडियो फुल स्क्रीन में देखें। "
                + "तब तक आपका वीडियो चलता रहेगा।");
        msg.setTextColor(0xFFAAAAAA);
        msg.setTextSize(13);
        msg.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(6);
        fsNotice.addView(msg, mlp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView stay = noticeBtn("अभी नहीं", 0xFF333333, Color.WHITE);
        stay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFsNotice();
            }
        });
        TextView go = noticeBtn("🎁 वीडियो देखें ▶ फुल स्क्रीन", 0xFFFF0000, Color.WHITE);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFsNotice();
                startRewarded(true);
            }
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = dp(8);
        row.addView(stay, l1);
        row.addView(go, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(12);
        fsNotice.addView(row, rlp);

        playerLayer.addView(fsNotice, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
    }

    private void showFsNotice() {
        if (fsNotice == null || fsNotice.getVisibility() == View.VISIBLE) return;
        FrameLayout.LayoutParams cl = (FrameLayout.LayoutParams) card.getLayoutParams();
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        nl.leftMargin = dp(12);
        nl.rightMargin = dp(12);
        nl.topMargin = cl.topMargin + cl.height + dp(12);
        fsNotice.setLayoutParams(nl);
        fsNotice.setVisibility(View.VISIBLE);
    }

    private void hideFsNotice() {
        if (fsNotice != null) fsNotice.setVisibility(View.GONE);
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

    /** पक्का करने के लिए: हर ~1 सेकंड में YouTube का मौजूदा URL देखकर Shorts पहचानता है */
    private final Runnable urlPoll = new Runnable() {
        @Override
        public void run() {
            if (ytWeb != null && !ytLocked) onYtUrl(ytWeb.getUrl());
            if (root != null) root.postDelayed(this, 1000);
        }
    };

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
        int b = shortsBannerShown() ? dp(SHORTS_BANNER_H) : 0;
        // नीचे ऑडियो बार हो तो YouTube पेज उसके ऊपर तक ही रहे (बार पेज को ढके नहीं)
        if (mode == MODE_MINI && musicOn && !inPip) b = Math.max(b, dp(MUSIC_BAR_H));
        if (vipCardShown()) b = Math.max(b, dp(VIP_CARD_H));      // नीचे VIP कार्ड के लिए जगह
        return b;
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
        updateVipCard();
        if (shortsHolder == null) return;
        if (!onShorts || !adsReady) {
            shortsHolder.setVisibility(View.GONE);
        } else {
            if (shortsSlot == null) {
                shortsSlot = makeSlot();
                if (shortsSlot != null) {
                    shortsSlot.toastErr = SHORTS_DEBUG_TOAST;
                    shortsSlot.retryMs = 10000;
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
            if (tryLoad && shortsSlot != null) {
                if (!shortsSlot.ok && !shortsSlot.loading && shortsSlot.tries >= 8) shortsSlot.tries = 0;
                shortsSlot.load();
            }
        }
        applyYtMargin();
        if (mode == MODE_MINI) placeMini();
    }

    // ------------------------------------------------------------------
    // छोटी तैरती विंडो: जहाँ छोड़ो वहीं रहती है
    // ------------------------------------------------------------------
    private void placeMini() {
        if (card == null || miniBox == null || musicOn || ovShown) return;
        if (inlineOn) {
            placeInline();
            return;
        }
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

        // ✕ और होम के बीच: रिवॉर्डेड वीडियो बटन
        rewBtn = new TextView(this);
        rewBtn.setTextColor(Color.WHITE);
        rewBtn.setTextSize(13);
        rewBtn.setEllipsize(TextUtils.TruncateAt.END);
        rewBtn.setTypeface(Typeface.DEFAULT_BOLD);
        rewBtn.setGravity(Gravity.CENTER);
        rewBtn.setSingleLine(true);
        rewBtn.setPadding(dp(10), 0, dp(10), 0);
        rewBtn.setElevation(dp(4));
        updateRewBtn();
        rewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVipPage();
            }
        });
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        rbLp.leftMargin = dp(12);
        rbLp.rightMargin = dp(6);
        top.addView(rewBtn, rbLp);

        // ⭐ पसंदीदा: हाथ-स्विच यहाँ से हटा दिया (अब वह सिर्फ़ VIP पेज से ऑन/ऑफ होता है)
        TextView favBtn = new TextView(this);
        favBtn.setText("⭐");
        favBtn.setTextSize(17);
        favBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        favBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFavoritesDialog();
            }
        });
        LinearLayout.LayoutParams wbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wbLp.rightMargin = dp(2);
        top.addView(favBtn, wbLp);

        // 🎵 म्यूज़िक मोड: वीडियो की जगह थंबनेल, सिर्फ़ गाना बजे (स्क्रीन बंद / ऐप नीचे होने पर भी)
        LinearLayout musicBox = new LinearLayout(this);
        musicBox.setOrientation(LinearLayout.HORIZONTAL);
        musicBox.setGravity(Gravity.CENTER_VERTICAL);
        TextView musicIcon = new TextView(this);
        musicIcon.setText("🎵");
        musicIcon.setTextSize(15);
        musicBox.addView(musicIcon);
        musicSwitch = new Switch(this);
        musicSwitch.setChecked(musicOn);
        int[][] mst = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
        musicSwitch.setThumbTintList(new ColorStateList(mst, new int[]{0xFFFFFFFF, 0xFFDDDDDD}));
        musicSwitch.setTrackTintList(new ColorStateList(mst, new int[]{0xFF2ECC71, 0x66FFFFFF}));
        musicSwitch.setScaleX(0.8f);
        musicSwitch.setScaleY(0.8f);
        musicSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                setMusic(on);
            }
        });
        musicBox.addView(musicSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams mbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mbLp.rightMargin = dp(2);
        top.addView(musicBox, mbLp);

        // दाएँ कोने में मिनिमाइज़: प्लेयर नीचे छोटा, YouTube का पेज खुल जाए
        MinimizeIcon home = new MinimizeIcon(this);
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

        pWeb = new BgWebView(this);
        if (Build.VERSION.SDK_INT >= 26) {
            // पीछे जाने पर भी प्लेयर का रेंडरर ऊँची प्राथमिकता पर रहे (आवाज़ न कटे)
            pWeb.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }
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
                if (inlineOn) return inlineTouch(v, e);
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
        buildFsNotice();
        buildMusicBar();
        // कमेंट पैनल हटाया (buildComments() अब नहीं बनता)

        buildVipCard();
        root.postDelayed(vipTick, 30000);   // हर 30 सेकंड में 24 घंटे की जाँच
        root.addView(playerLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings ps = pWeb.getSettings();
        ps.setJavaScriptEnabled(true);
        ps.setDomStorageEnabled(true);
        ps.setMediaPlaybackRequiresUserGesture(false);
        pWeb.addJavascriptInterface(new NativeBridge(), "Native");
        pWeb.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // नया वीडियो लोड होने पर छोटा/बड़ा मोड फिर से लागू (छोटी विंडो में बार न दिखे)
                view.evaluateJavascript("window.setMini&&setMini(" + (mode == MODE_MINI) + ")", null);
                view.evaluateJavascript("window.setMusic&&setMusic(" + musicOn + ")", null);
                view.evaluateJavascript("window.setInline&&setInline(" + (inlineOn && mode == MODE_MINI) + ")", null);
            }
        });
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
            page = playerHtml()
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
        // म्यूज़िक बार / अगला-गाना की तैयारी
        errStreak = 0;
        curTitle = "";
        lastPos = 0;
        lastDur = 0;
        if (link.id != null && link.id.length() > 0) {
            curId = link.id;
            markPlayed(link.id);
            loadThumb(link.id);
        }
        if (mbTitle != null) mbTitle.setText("लोड हो रहा है…");
        if (mbProg != null) mbProg.setScaleX(0f);
        harvestPool(null);   // इस पेज के बाकी वीडियो/गाने "अगला" के लिए जमा करो
        pWeb.loadDataWithBaseURL("https://videoplayer.app/", page, "text/html", "utf-8", null);
        // छोटी विंडो खुली हो तो नया वीडियो उसी में चले; वरना बड़ा प्लेयर
        if (mode == MODE_NONE) {
            // थंबनेल पर क्लिक: उसी जगह छोटी विंडो; म्यूज़िक मोड ऑन: नीचे ऑडियो बार; वरना बड़ा प्लेयर
            setMode((inlineOn || (musicOn && MUSIC_OPEN_AS_BAR)) ? MODE_MINI : MODE_FULL);
        } else if (mode != MODE_MINI) {
            setMode(MODE_FULL);
        } else if (inlineOn) {
            applyCardLayout();      // पहले से छोटी विंडो खुली थी: अब इस थंबनेल की जगह ले आओ
        }
    }

    private void setMode(int m) {
        root.post(new Runnable() {
            @Override
            public void run() {
                updateVipCard();
            }
        });
        if (m != MODE_FULL) closeComments();
        if (m != MODE_MINI) inlineStop();
        mode = m;
        hideFsNotice();
        if (m == MODE_NONE) {
            playerLayer.setVisibility(View.GONE);
        } else {
            playerLayer.setVisibility(View.VISIBLE);
            // परत दोनों मोड में पूरी स्क्रीन की; छोटे मोड में खाली जगह का टच नीचे YouTube पेज को जाता है
            playerLayer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fullBg.setVisibility(m == MODE_FULL ? View.VISIBLE : View.GONE);
            // म्यूज़िक मोड + मिनिमाइज़ = छोटी वीडियो विंडो नहीं, नीचे ऑडियो प्लेयर बार
            boolean asBar = (m == MODE_MINI && musicOn);
            miniBox.setVisibility((m == MODE_MINI && !asBar) ? View.VISIBLE : View.GONE);
            musicBar.setVisibility(asBar ? View.VISIBLE : View.GONE);
            card.setAlpha(asBar ? 0.01f : 1f);   // वीडियो अदृश्य, पर चलता रहे (आवाज़ के लिए)
            corner = asBar ? 0 : ((m == MODE_MINI) ? dp(12) : dp(16));
            card.invalidateOutline();
            applyCardLayout();
            pWeb.evaluateJavascript("setMini(" + (m == MODE_MINI) + ")", null);
        }
        updateMusicService();
        // छोटी विंडो अब पेज के ऊपर तैरती है, नीचे जगह नहीं छोड़नी; सिर्फ़ Shorts बैनर की जगह
        applyYtMargin();
        if (!fsOn && customView == null
                && getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        applyChrome();
    }

    private void stopPlayer() {
        vidPlaying = false;
        closeComments();
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
        closeComments();
        hideFsNotice();
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

    /** मिनिमाइज़ का चित्र (नीचे की ओर तीर + नीचे पट्टी) */
    private static class MinimizeIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        MinimizeIcon(Context c) {
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
            path.moveTo(11 * u, 14 * u);
            path.lineTo(20 * u, 23 * u);
            path.lineTo(29 * u, 14 * u);
            path.moveTo(12 * u, 28 * u);
            path.lineTo(28 * u, 28 * u);
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
        if (cmOpen && cmPanel != null) {
            // कमेंट खुले हों तो पैनल के बाहर कहीं भी टैप -> कमेंट बंद (वह टैप और कुछ नहीं करेगा)
            int act = ev.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                Rect r = new Rect();
                cmPanel.getGlobalVisibleRect(r);
                cmSwallow = !r.contains((int) ev.getRawX(), (int) ev.getRawY());
                if (cmSwallow) {
                    closeComments();
                    return true;
                }
            } else if (cmSwallow) {
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) cmSwallow = false;
                return true;
            }
        }
        if (card != null && mode == MODE_FULL && !fsOn && customView == null && !cmOpen
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
            // सिर्फ़ बीच के 20% हिस्से से नीचे खींचने पर मिनिमाइज़; बाएँ = आवाज़, दाएँ = ब्राइटनेस
            dragCand = x >= loc[0] + card.getWidth() * 0.4f && x <= loc[0] + card.getWidth() * 0.6f
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
        if (ovShown) return;      // कार्ड अभी बाहर की तैरती विंडो में है
        FrameLayout.LayoutParams lp;
        if (fsOn || inPip) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        } else if (mode == MODE_MINI && musicOn) {
            // ऑडियो बार के ठीक पीछे (अदृश्य), ताकि वेबव्यू चलता रहे पर टच न ले
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(MUSIC_BAR_H),
                    Gravity.BOTTOM | Gravity.LEFT);
        } else if (mode == MODE_MINI && inlineOn) {
            lp = new FrameLayout.LayoutParams(inW, inH, Gravity.TOP | Gravity.LEFT);
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
        if (mode == MODE_MINI && !musicOn && !fsOn && !inPip) {
            placeMini();
        } else if (!dragging) {
            card.setTranslationX(0f);
            card.setTranslationY(0f);
        }

        // बैनर: सिर्फ़ बड़े प्लेयर में, पोर्ट्रेट में, सीक बार वाले कार्ड के ठीक नीचे
        boolean portrait = getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
        if (!fsOn && !inPip && !cmOpen && mode == MODE_FULL && portrait) {
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
        if (cmOpen) positionComments();
        if (ytLocked && !fsOn && customView == null
                && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            unlockYt();
        }
        relayoutYt();
    }

    @Override
    protected void onPause() {
        resumedNow = false;
        stopProx();
        if (root != null) root.removeCallbacks(urlPoll);
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
        resumedNow = true;
        vipExpireIfNeeded();
        restoreFromOverlay();     // बाहर की विंडो से वापस ऐप में
        if (orient != null && orient.canDetectOrientation()) orient.enable();
        updateWaveSensor();
        if (root != null) {
            root.removeCallbacks(urlPoll);
            root.postDelayed(urlPoll, 1000);
        }
        relayoutYt();
    }

    // ------------------------------------------------------------------
    // Share, bridges, बैक
    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    // सेंसर के आगे हाथ लहराओ -> Shorts में अगला वीडियो (उँगली से ऊपर स्वाइप जैसा)
    // ------------------------------------------------------------------
    private static final String PREFS = "vp_prefs", PREF_WAVE = "wave_next";
    private static final String PREF_FAVORITES = "favorites";   // JSON array [{id,title}]
    private android.app.Dialog favDialog;
    private LinearLayout favListBox;
    private static final int WAVE_GAP_MS = 1000;   // दो बार लगातार न लगे
    private SensorManager sensorMgr;
    private Sensor proxSensor;
    private Switch waveSwitch;
    private boolean waveOn, proxNear, proxRegistered;
    private long lastWaveMs;
    // ऐप खुलने के बाद शुरू के FREE_WAVES बार हाथ लहराना मुफ़्त; उसके बाद स्विच बंद हो जाता है
    // और रिवॉर्डेड वीडियो पूरा (हरा) होने पर ही फिर चालू होगा। ऐप दोबारा खुलने पर फिर 5 मुफ़्त।
    private static final int FREE_WAVES = 5;
    private int waveCount;

    private final SensorEventListener proxListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            if (proxSensor == null || e.values == null || e.values.length == 0) return;
            boolean near = e.values[0] < Math.min(5f, proxSensor.getMaximumRange());
            if (near && !proxNear) {
                proxNear = true;
                onWave();
            } else if (!near) {
                proxNear = false;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private void setWave(boolean on) {
        if (on == waveOn) return;
        if (on && proxSensor == null) {
            Toast.makeText(this, "इस फ़ोन में प्रॉक्सिमिटी सेंसर नहीं मिला", Toast.LENGTH_SHORT).show();
            if (waveSwitch != null) waveSwitch.setChecked(false);
            return;
        }
        if (on && !vipHas(F_WAVE)) {
            if (waveSwitch != null) waveSwitch.setChecked(false);
            showWaveGate();
            return;
        }
        waveOn = on;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_WAVE, on).apply();
        updateWaveSensor();
        Toast.makeText(this, on ? "✋ हाथ लहराकर अगला Shorts: चालू"
                : "✋ हाथ लहराकर अगला Shorts: बंद", Toast.LENGTH_SHORT).show();
    }

    private boolean waveAfterReward;
    private android.app.AlertDialog waveDialog;

    private void showWaveGate() {
        showFeatureGate(F_WAVE);
    }

    private void updateWaveSensor() {
        boolean want = waveOn && proxSensor != null && sensorMgr != null;
        if (want && !proxRegistered) {
            proxNear = true;     // शुरू में सेंसर ढका हो तो अपने-आप न लगे
            proxRegistered = sensorMgr.registerListener(proxListener, proxSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        } else if (!want) {
            stopProx();
        }
    }

    private void stopProx() {
        if (proxRegistered && sensorMgr != null) {
            try {
                sensorMgr.unregisterListener(proxListener);
            } catch (Throwable ignored) {
            }
        }
        proxRegistered = false;
    }

    private void onWave() {
        long now = SystemClock.uptimeMillis();
        if (now - lastWaveMs < WAVE_GAP_MS) return;
        // सिर्फ़ Shorts देखते समय (प्लेयर बंद, विज्ञापन/लोडिंग न चल रही हो)
        if (!onShorts || mode != MODE_NONE || ytWeb == null || ytLocked || customView != null
                || rewShowing || (loadView != null && loadView.getVisibility() == View.VISIBLE)) return;
        lastWaveMs = now;
        swipeNextShort();
    }

    /** उँगली जैसा ऊपर स्वाइप: नीचे से ऊपर की ओर तेज़ खिसकाव -> अगला Shorts */
    private void swipeNextShort() {
        int w = ytWeb.getWidth(), h = ytWeb.getHeight();
        if (w <= 0 || h <= 0) return;
        final float x = w / 2f, y0 = h * 0.72f, y1 = h * 0.22f;
        final long down = SystemClock.uptimeMillis();
        final int steps = 12;
        sendTouch(down, down, MotionEvent.ACTION_DOWN, x, y0);
        for (int i = 1; i <= steps; i++) {
            final int k = i;
            root.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendTouch(down, down + k * 16L,
                            k == steps ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE,
                            x, y0 + (y1 - y0) * k / steps);
                }
            }, k * 16L);
        }
    }

    private void sendTouch(long downTime, long time, int action, float x, float y) {
        if (ytWeb == null || ytLocked) return;
        MotionEvent ev = MotionEvent.obtain(downTime, time, action, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ytWeb.dispatchTouchEvent(ev);
        ev.recycle();
    }

    /** YouTube वेबव्यू की सारी सेटिंग (होम और Shorts दोनों वेबव्यू के लिए एक जैसी) */
    @SuppressWarnings("deprecation")
    private void configureYt(final WebView web) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // होम/सर्च वाले YouTube पेज पर वीडियो अपने-आप न चलें (Shorts वाला वेबव्यू छोड़कर)
        s.setMediaPlaybackRequiresUserGesture(web == homeWeb);
        s.setSupportMultipleWindows(false);
        s.setForceDark(WebSettings.FORCE_DARK_OFF);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "VideoBridge");
        web.addJavascriptInterface(new SpeechBridge(), "Speech");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view != ytWeb) return;   // हटाए/पीछे वाले वेबव्यू की खबर अनदेखी
                // पेज काफ़ी लोड हो गया तो लोडिंग स्क्रीन हटाकर तुरंत पेज दिखाओ
                if (newProgress >= 80 && !ytLoadFailed) hideLoadingSmart();
            }

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
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (request.isForMainFrame() && view == homeWeb && isShortsPage(url)) {
                    openShortsOverlay(url);   // होम पेज वैसा ही रहे, Shorts अलग वेबव्यू में
                    return true;
                }
                if (request.isForMainFrame() && !isShorts(url) && VideoLink.parse(url) != null) {
                    openPlayer(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (view != ytWeb) return;
                ytLoadFailed = false;
                backWait = false;            // पूरा पेज लोड हो रहा है -> तैयार होने तक रुको
                if (!firstPageLoad && view == homeWeb) showLoading();   // Shorts वेबव्यू पर गोल घूमना नहीं
                inject(view);
                onYtUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (view != ytWeb) return;
                if (!ytLoadFailed) hideLoadingSmart();
                inject(view);
                onYtUrl(url);
                if (view == homeWeb && !spareScheduled) {
                    spareScheduled = true;
                    root.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            prepareShortsSpare();
                        }
                    }, 4000);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (view != ytWeb) return;
                if (request != null && request.isForMainFrame()) {
                    showLoadError(request.getUrl().toString());
                }
            }

            /** YouTube (m.) पेज बदले बिना भी URL बदलता है (Shorts में ऊपर स्वाइप) -> यहीं पकड़ में आता है */
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                if (view != ytWeb) return;
                onYtUrl(url);
                if (backWait) {            // YouTube ने पेज बदले बिना पीछे लौटा दिया -> जल्दी दिखाओ
                    backWait = false;
                    hideLoadingSmart();
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // कमेंट: वीडियो के नीचे पैनल (स्क्रॉल करके पढ़ो); पैनल के बाहर कहीं भी टैप करने पर बंद
    // ------------------------------------------------------------------
    // YouTube Data API v3 की key यहाँ डालें (Google Cloud Console -> YouTube Data API v3 -> API key)
    private static final String YT_API_KEY = "";

    private LinearLayout cmPanel, cmList;
    private ScrollView cmScroll;
    private TextView cmStatus;
    private String cmVideoId, cmNext;
    private boolean cmOpen, cmLoading, cmEnd, cmSwallow;

    private void buildComments() {
        cmPanel = new LinearLayout(this);
        cmPanel.setOrientation(LinearLayout.VERTICAL);
        cmPanel.setVisibility(View.GONE);
        cmPanel.setElevation(dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF181818);
        bg.setCornerRadii(new float[]{dp(14), dp(14), dp(14), dp(14), 0, 0, 0, 0});
        cmPanel.setBackground(bg);
        cmPanel.setClickable(true);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(8), dp(6), dp(8));
        TextView title = new TextView(this);
        title.setText("💬 कमेंट");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextColor(Color.WHITE);
        x.setTextSize(18);
        x.setGravity(Gravity.CENTER);
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeComments();
            }
        });
        head.addView(x, new LinearLayout.LayoutParams(dp(40), dp(36)));
        cmPanel.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cmScroll = new ScrollView(this);
        cmList = new LinearLayout(this);
        cmList.setOrientation(LinearLayout.VERTICAL);
        cmList.setPadding(dp(14), 0, dp(14), dp(16));
        cmScroll.addView(cmList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cmScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int sx, int sy, int ox, int oy) {
                View c = cmScroll.getChildAt(0);
                if (c != null && !cmEnd
                        && c.getBottom() - (cmScroll.getHeight() + sy) < dp(240)) {
                    loadComments();
                }
            }
        });
        cmPanel.addView(cmScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        cmStatus = new TextView(this);
        cmStatus.setTextColor(0xFFAAAAAA);
        cmStatus.setTextSize(13);
        cmStatus.setPadding(0, dp(10), 0, dp(10));

        playerLayer.addView(cmPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300), Gravity.TOP));
    }

    private void positionComments() {
        if (cmPanel == null) return;
        int rootH = root.getHeight() > 0 ? root.getHeight()
                : getResources().getDisplayMetrics().heightPixels;
        boolean portrait = getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
        int top;
        if (portrait) {
            int w = getResources().getDisplayMetrics().widthPixels - dp(24);
            top = dp(TOP_H) + w * 9 / 16 + dp(BAR_DP) + dp(8);   // वीडियो + बार के ठीक नीचे
        } else {
            top = rootH * 40 / 100;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(dp(160), rootH - top), Gravity.TOP);
        lp.topMargin = top;
        cmPanel.setLayoutParams(lp);
    }

    private void openComments(String videoId) {
        if (cmPanel == null || mode != MODE_FULL || fsOn || videoId == null || videoId.length() == 0) return;
        if (!videoId.equals(cmVideoId)) {
            cmVideoId = videoId;
            cmNext = null;
            cmEnd = false;
            cmLoading = false;
            cmList.removeAllViews();
        }
        cmOpen = true;
        bannerHolder.setVisibility(View.GONE);
        positionComments();
        cmPanel.setVisibility(View.VISIBLE);
        cmScroll.scrollTo(0, 0);
        if (cmList.getChildCount() == 0) loadComments();
    }

    private void closeComments() {
        if (!cmOpen) return;
        cmOpen = false;
        cmSwallow = false;
        if (cmPanel != null) cmPanel.setVisibility(View.GONE);
        if (card != null && mode != MODE_NONE) applyCardLayout();   // बैनर वापस
    }

    private void setCmStatus(String s) {
        if (cmStatus.getParent() != null) ((ViewGroup) cmStatus.getParent()).removeView(cmStatus);
        if (s == null) return;
        cmStatus.setText(s);
        cmList.addView(cmStatus);
    }

    private void loadComments() {
        if (cmLoading || cmEnd || cmVideoId == null) return;
        if (YT_API_KEY.length() == 0) {
            cmEnd = true;
            setCmStatus("कमेंट पढ़ने के लिए MainActivity.java में YT_API_KEY (YouTube Data API v3) डालनी होगी");
            return;
        }
        cmLoading = true;
        setCmStatus("लोड हो रहा है…");
        final String vid = cmVideoId, tok = cmNext;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                JSONArray items = null;
                String next = null;
                HttpURLConnection c = null;
                try {
                    String u = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet"
                            + "&maxResults=30&order=relevance&textFormat=plainText&videoId="
                            + Uri.encode(vid) + "&key=" + Uri.encode(YT_API_KEY)
                            + (tok != null ? "&pageToken=" + Uri.encode(tok) : "");
                    c = (HttpURLConnection) new URL(u).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while (in != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
                    String body = out.toString("UTF-8");
                    if (code >= 400) {
                        err = body.contains("commentsDisabled") ? "इस वीडियो में कमेंट बंद हैं"
                                : body.contains("quotaExceeded") ? "आज की API सीमा पूरी हो गई"
                                : "कमेंट नहीं मिल पाए (" + code + ")";
                    } else {
                        JSONObject o = new JSONObject(body);
                        items = o.optJSONArray("items");
                        next = o.optString("nextPageToken", "");
                    }
                } catch (Throwable t) {
                    err = "इंटरनेट जाँचिए, कमेंट नहीं आ पाए";
                } finally {
                    if (c != null) c.disconnect();
                }
                final String fErr = err, fNext = next;
                final JSONArray fItems = items;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!vid.equals(cmVideoId)) return;     // बीच में दूसरा वीडियो खुल गया
                        cmLoading = false;
                        setCmStatus(null);
                        if (fErr != null) {
                            cmEnd = true;
                            setCmStatus(fErr);
                            return;
                        }
                        try {
                            for (int i = 0; fItems != null && i < fItems.length(); i++) {
                                JSONObject s = fItems.getJSONObject(i).getJSONObject("snippet")
                                        .getJSONObject("topLevelComment").getJSONObject("snippet");
                                addCommentView(s.optString("authorDisplayName"),
                                        s.optString("textDisplay"), s.optInt("likeCount", 0));
                            }
                        } catch (Throwable ignored) {
                        }
                        cmNext = (fNext == null || fNext.length() == 0) ? null : fNext;
                        cmEnd = cmNext == null;
                        if (cmList.getChildCount() == 0) setCmStatus("अभी कोई कमेंट नहीं है");
                    }
                });
            }
        }).start();
    }

    private void addCommentView(String author, String text, int likes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView a = new TextView(this);
        a.setText(author);
        a.setTextColor(0xFFAAAAAA);
        a.setTextSize(12);
        a.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(a);
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        row.addView(b);
        if (likes > 0) {
            TextView l = new TextView(this);
            l.setText("👍 " + likes);
            l.setTextColor(0xFF888888);
            l.setTextSize(12);
            row.addView(l);
        }
        cmList.addView(row);
    }

    // ------------------------------------------------------------------
    // 🎵 म्यूज़िक मोड: थंबनेल + सिर्फ़ गाना. ऐप नीचे करने / स्क्रीन बंद होने पर भी चलता रहे
    // (इसके लिए MusicService नोटिफ़िकेशन के साथ ऐप को ज़िंदा रखती है)
    // ------------------------------------------------------------------
    private static final String PREF_MUSIC = "music_mode";
    private static final int REQ_NOTIF = 78;
    private Switch musicSwitch;
    private boolean musicOn;

    private void setMusic(boolean on) {
        if (on == musicOn) return;
        if (on && !vipHas(F_MUSIC)) {   // म्यूज़िक मोड VIP में चुना नहीं
            if (musicSwitch != null) musicSwitch.setChecked(false);
            showMusicGate();
            return;
        }
        musicOn = on;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_MUSIC, on).apply();
        if (pWeb != null) pWeb.evaluateJavascript("window.setMusic&&setMusic(" + on + ")", null);
        if (on && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
        }
        updateMusicService();
        if (!on && mode == MODE_MINI) setMode(MODE_MINI);   // नीचे का ऑडियो बार -> छोटी वीडियो विंडो
        Toast.makeText(this, on ? "🎵 म्यूज़िक मोड चालू: थंबनेल दिखेगा, गाना बजेगा"
                : "🎵 म्यूज़िक मोड बंद", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------
    // 🎵 म्यूज़िक मोड: नीचे ऑडियो प्लेयर बार, "अगला गाना", नोटिफ़िकेशन / लॉक-स्क्रीन के बटन
    // ------------------------------------------------------------------
    private FrameLayout musicBar;
    private ImageView mbThumb;
    private TextView mbTitle, mbPp;
    private View mbProg;
    private String curTitle = "", curId = "", thumbFor = "";
    private double lastPos, lastDur;
    private int errStreak;
    private final ArrayList<String> pool = new ArrayList<String>();     // पेज से जमा किए वीडियो-id
    private final ArrayList<String> played = new ArrayList<String>();   // चल चुके
    private final Random rnd = new Random();

    private TextView barBtn(String t, int sp, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(t);
        b.setTextColor(Color.WHITE);
        b.setTextSize(sp);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(l);
        return b;
    }

    private void buildMusicBar() {
        musicBar = new FrameLayout(this);
        musicBar.setVisibility(View.GONE);
        musicBar.setClickable(true);       // बार पर टच पीछे के पेज तक न जाए
        musicBar.setElevation(dp(14));     // card (8dp) से ऊपर
        musicBar.setBackgroundColor(0xFF1E1E1E);
        musicBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_FULL);        // बार पर टैप -> बड़ा प्लेयर
            }
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(4), 0);

        mbThumb = new ImageView(this);
        mbThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mbThumb.setBackgroundColor(0xFF333333);
        mbThumb.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
            }
        });
        mbThumb.setClipToOutline(true);
        row.addView(mbThumb, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        mbTitle = new TextView(this);
        mbTitle.setTextColor(Color.WHITE);
        mbTitle.setTextSize(14);
        mbTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mbTitle.setSingleLine(true);
        mbTitle.setEllipsize(TextUtils.TruncateAt.END);
        mbTitle.setText("लोड हो रहा है…");
        col.addView(mbTitle);
        TextView sub = new TextView(this);
        sub.setTextColor(0xFFAAAAAA);
        sub.setTextSize(12);
        sub.setSingleLine(true);
        sub.setText("🎵 म्यूज़िक मोड");
        col.addView(sub);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.leftMargin = dp(10);
        cl.rightMargin = dp(4);
        row.addView(col, cl);

        mbPp = barBtn("▶\uFE0E", 22, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pWeb != null) pWeb.evaluateJavascript("toggle()", null);
            }
        });
        row.addView(mbPp, new LinearLayout.LayoutParams(dp(46), dp(48)));
        row.addView(barBtn("⏭\uFE0E", 22, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        }), new LinearLayout.LayoutParams(dp(46), dp(48)));
        row.addView(barBtn("✕", 18, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlayer();
            }
        }), new LinearLayout.LayoutParams(dp(42), dp(48)));

        musicBar.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mbProg = new View(this);                 // ऊपर पतली लाल प्रोग्रेस रेखा
        mbProg.setBackgroundColor(0xFFFF0000);
        mbProg.setPivotX(0f);
        mbProg.setScaleX(0f);
        musicBar.addView(mbProg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.TOP));

        playerLayer.addView(musicBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(MUSIC_BAR_H), Gravity.BOTTOM));
    }

    /** नोटिफ़िकेशन / लॉक-स्क्रीन / हेडसेट के बटन -> प्लेयर */
    private void bindMusicControls() {
        MusicService.controls = new MusicService.Controls() {
            @Override
            public void onPlayPause() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pWeb != null) pWeb.evaluateJavascript("toggle()", null);
                    }
                });
            }

            @Override
            public void onNext() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        playNext();
                    }
                });
            }

            @Override
            public void onClose() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stopPlayer();
                    }
                });
            }

            @Override
            public void onSeek(final long ms) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pWeb != null) {
                            pWeb.evaluateJavascript("try{p.seekTo(" + (ms / 1000.0) + ",true)}catch(e){}", null);
                        }
                    }
                });
            }
        };
    }

    private void fillMusicStatics() {
        MusicService.title = curTitle.length() > 0 ? curTitle : "गाना लोड हो रहा है…";
        MusicService.playing = vidPlaying;
        MusicService.posMs = (long) (lastPos * 1000);
        MusicService.durMs = (long) (lastDur * 1000);
    }

    /** नाम / चल-रुक / थंबनेल बदलने पर नोटिफ़िकेशन ताज़ा करो */
    private void pushMusic() {
        if (!musicOn || mode == MODE_NONE) return;
        fillMusicStatics();
        MusicService.refresh();
    }

    private void markPlayed(String id) {
        played.remove(id);
        played.add(id);
        if (played.size() > 100) played.remove(0);
    }

    private void addPool(String id) {
        if (id == null || id.length() != 11 || pool.contains(id)) return;
        pool.add(id);
        if (pool.size() > 300) pool.remove(0);
    }

    private void loadThumb(final String id) {
        if (id == null || id.length() == 0 || id.equals(thumbFor)) return;
        thumbFor = id;
        MusicService.art = null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bm = null;
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL("https://i.ytimg.com/vi/" + id + "/mqdefault.jpg")
                            .openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    InputStream in = c.getInputStream();
                    bm = BitmapFactory.decodeStream(in);
                    in.close();
                } catch (Throwable ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
                final Bitmap fb = bm;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (fb == null || !id.equals(thumbFor)) return;
                        if (mbThumb != null) mbThumb.setImageBitmap(fb);
                        MusicService.art = fb;
                        pushMusic();
                    }
                });
            }
        }).start();
    }

    // YouTube पेज पर दिख रहे सारे वीडियो-लिंक (होम / सर्च) -> "अगला" के लिए उम्मीदवार
    private static final String HARVEST_JS =
            "(function(){var r=[],s={};try{document.querySelectorAll('a[href]').forEach(function(a){"
            + "try{var u=new URL(a.href);if(/youtube\\.com$/.test(u.hostname)&&u.pathname==='/watch'){"
            + "var v=u.searchParams.get('v');if(v&&v.length===11&&!s[v]){s[v]=1;r.push(v);}}}catch(e){}});}"
            + "catch(e){}return JSON.stringify(r);})()";

    private void harvestPool(final Runnable after) {
        final WebView w = ytWeb;
        if (w == null || ytLocked) {
            if (after != null) after.run();
            return;
        }
        try {
            w.evaluateJavascript(HARVEST_JS, new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String v) {
                    try {
                        Object o = new JSONTokener(v).nextValue();
                        if (o instanceof String) {
                            JSONArray a = new JSONArray((String) o);
                            for (int i = 0; i < a.length(); i++) addPool(a.optString(i));
                        }
                    } catch (Throwable ignored) {
                    }
                    if (after != null) after.run();
                }
            });
        } catch (Throwable t) {
            if (after != null) after.run();
        }
    }

    /** म्यूज़िक मोड: बिना चले हुए में से रैंडम; वीडियो मोड: पेज के क्रम में अगला */
    private String pickNext() {
        ArrayList<String> c = new ArrayList<String>();
        for (String id : pool) {
            if (!id.equals(curId) && !played.contains(id)) c.add(id);
        }
        if (c.isEmpty()) return null;
        return musicOn ? c.get(rnd.nextInt(c.size())) : c.get(0);
    }

    private void playId(String id) {
        curId = id;
        markPlayed(id);
        curTitle = "";
        lastPos = 0;
        lastDur = 0;
        if (mbTitle != null) mbTitle.setText("लोड हो रहा है…");
        if (mbProg != null) mbProg.setScaleX(0f);
        loadThumb(id);
        if (pWeb != null) pWeb.evaluateJavascript("loadId(" + JSONObject.quote(id) + ")", null);
        pushMusic();
    }

    private void playNext() {
        if (mode == MODE_NONE || pWeb == null) return;
        String id = pickNext();
        if (id != null) {
            playId(id);
            return;
        }
        harvestPool(new Runnable() {
            @Override
            public void run() {
                String n = pickNext();
                if (n != null) playId(n);
                else fetchRelatedAndPlay();
            }
        });
    }

    /** पेज पर नया कुछ न मिले तो: मौजूदा वीडियो के "आगे चलाएँ" वीडियो-id YouTube पेज से निकालो (सबसे अच्छा प्रयास) */
    private void fetchRelatedAndPlay() {
        final String from = curId;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<String> got = new ArrayList<String>();
                HttpURLConnection c = null;
                try {
                    if (from.length() == 11) {
                        c = (HttpURLConnection) new URL("https://m.youtube.com/watch?v=" + from).openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(10000);
                        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                        c.setRequestProperty("Accept-Language", "hi-IN,hi;q=0.9,en;q=0.8");
                        InputStream in = c.getInputStream();
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[16384];
                        int n, total = 0;
                        while ((n = in.read(buf)) > 0 && total < 3000000) {
                            out.write(buf, 0, n);
                            total += n;
                        }
                        in.close();
                        Matcher m = Pattern.compile("\"videoId\":\"([A-Za-z0-9_-]{11})\"").matcher(out.toString("UTF-8"));
                        while (m.find() && got.size() < 40) {
                            String id = m.group(1);
                            if (!got.contains(id)) got.add(id);
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mode == MODE_NONE) return;
                        for (String id : got) addPool(id);
                        String n = pickNext();
                        if (n == null) {                 // सब चल चुके -> फिर से शुरू
                            played.clear();
                            if (curId.length() > 0) played.add(curId);
                            n = pickNext();
                        }
                        if (n != null) {
                            playId(n);
                        } else {
                            Toast.makeText(MainActivity.this, "अगला गाना नहीं मिला", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private int songCount;
    private boolean musicAfterReward;
    private android.app.AlertDialog musicDialog;

    /** मुफ़्त गाने पूरे: म्यूज़िक स्विच अपने-आप बंद + रिवॉर्डेड वाला संदेश */
    private void musicGateTrip() {
        if (musicSwitch != null) musicSwitch.setChecked(false);
        showMusicGate();
    }

    private void showMusicGate() {
        showFeatureGate(F_MUSIC);
    }

    private void updateMusicService() {
        try {
            Intent si = new Intent(this, MusicService.class);
            if (musicOn && mode != MODE_NONE) {
                fillMusicStatics();
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(si); else startService(si);
            } else {
                stopService(si);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // म्यूज़िक मोड में ऐप नीचे / स्क्रीन बंद होने पर भी वेबव्यू चलता रहे
        if (musicOn && mode != MODE_NONE && pWeb != null) {
            pWeb.onResume();
            pWeb.resumeTimers();
        }
    }

    // ------------------------------------------------------------------
    // थंबनेल पर ही चलना: क्लिक किए वीडियो के थंबनेल के बराबर विंडो, उसी की जगह; स्क्रॉल से साथ चले
    // और थंबनेल निकल जाए तो वीडियो बंद। विंडो पर टैप -> बड़ा प्लेयर
    // ------------------------------------------------------------------
    private boolean inlineOn;
    private float inX, inY, inViewH;
    private int inW, inH, inlineSized;
    private float itDownX, itDownY;
    private boolean itMoved, itSeek;

    private void inlineSet(double l, double t, double w, double h) {
        float d = getResources().getDisplayMetrics().density;
        int[] a = new int[2], b = new int[2];
        if (ytWeb != null) ytWeb.getLocationInWindow(a);
        root.getLocationInWindow(b);
        inX = a[0] - b[0] + (float) (l * d);
        inY = a[1] - b[1] + (float) (t * d);
        inW = Math.round((float) (w * d));
        inH = Math.round((float) (h * d));
        inViewH = ytWeb == null ? 0 : ytWeb.getHeight();
    }

    private void placeInline() {
        if (!inlineOn || card == null || miniBox == null || ovShown) return;
        int key = inW * 100000 + inH;
        if (key != inlineSized) {                       // साइज़ बदला तभी लेआउट छुओ
            inlineSized = key;
            card.setLayoutParams(new FrameLayout.LayoutParams(inW, inH, Gravity.TOP | Gravity.LEFT));
            miniBox.setLayoutParams(new FrameLayout.LayoutParams(inW, inH, Gravity.TOP | Gravity.LEFT));
            setMiniPpBottom(dp(22));          // ❚❚ को सीक बार के ऊपर रखो
            if (pWeb != null) pWeb.evaluateJavascript("window.setInline&&setInline(true)", null);
        }
        card.setTranslationX(inX);
        card.setTranslationY(inY);
        miniBox.setTranslationX(inX);
        miniBox.setTranslationY(inY);
    }

    private void setMiniPpBottom(int px) {
        try {
            ViewGroup.LayoutParams lp = miniPp.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) lp).bottomMargin = px;
                miniPp.setLayoutParams(lp);
            }
        } catch (Throwable ignored) {
        }
    }

    private void inlineStop() {
        if (!inlineOn) return;
        inlineOn = false;
        inlineSized = 0;
        setMiniPpBottom(dp(6));
        if (pWeb != null) pWeb.evaluateJavascript("window.setInline&&setInline(false)", null);
        try {
            if (homeWeb != null) homeWeb.evaluateJavascript("window.__vpPinStop&&__vpPinStop()", null);
        } catch (Throwable ignored) {
        }
        if (miniBox != null) {
            miniBox.setLayoutParams(new FrameLayout.LayoutParams(
                    dp(MINI_W), dp(MINI_CARD_H), Gravity.TOP | Gravity.LEFT));
        }
    }

    /** थंबनेल वाली विंडो पर छुआई: खिसकाना नहीं; ऊपर-नीचे सरकाओ तो पेज स्क्रॉल हो, टैप तो बड़ा प्लेयर */
    private boolean inlineTouch(View v, MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            itSeek = e.getY() > v.getHeight() - dp(30);     // नीचे की पट्टी = सीक बार
        }
        if (itSeek) {
            boolean end = a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL;
            float f = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, v.getWidth())));
            if (pWeb != null) pWeb.evaluateJavascript("seekFrac(" + f + "," + end + ")", null);
            if (end) itSeek = false;
            return true;
        }
        if (a == MotionEvent.ACTION_DOWN) {
            itDownX = e.getRawX();
            itDownY = e.getRawY();
            itMoved = false;
        } else if (!itMoved && a == MotionEvent.ACTION_MOVE) {
            int slop = ViewConfiguration.get(this).getScaledTouchSlop();
            if (Math.hypot(e.getRawX() - itDownX, e.getRawY() - itDownY) > slop) itMoved = true;
        }
        if (ytWeb == null) {
            if (a == MotionEvent.ACTION_UP && !itMoved) v.performClick();
            return true;
        }
        int[] loc = new int[2];
        ytWeb.getLocationOnScreen(loc);
        MotionEvent c = MotionEvent.obtain(e);
        c.setLocation(e.getRawX() - loc[0], e.getRawY() - loc[1]);
        if (a == MotionEvent.ACTION_UP && !itMoved) {
            c.setAction(MotionEvent.ACTION_CANCEL);     // पेज पर थंबनेल का लिंक दोबारा न चले
            ytWeb.dispatchTouchEvent(c);
            c.recycle();
            v.performClick();
            return true;
        }
        ytWeb.dispatchTouchEvent(c);
        c.recycle();
        return true;
    }

    // ------------------------------------------------------------------
    // बाहर की अपनी तैरती विंडो (ऐप नीचे करने पर): सिर्फ़ वीडियो + ❚❚ + ✕ + ⤢ (बड़ा/छोटा)
    // वीडियो पर टैप -> ऐप खुल जाता है; उँगली से कहीं भी खिसका सकते हैं
    // ------------------------------------------------------------------
    private boolean ovShown, ovBig;
    private FrameLayout ovRoot;
    private TextView ovPp, ovZoom;
    private WindowManager.LayoutParams ovLp;
    private int ovIdx;

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT >= 26 && Settings.canDrawOverlays(this);
    }

    private int ovWidth() {
        int sw = getResources().getDisplayMetrics().widthPixels;
        return ovBig ? (int) (sw * 0.92f) : Math.min((int) (sw * 0.55f), dp(260));
    }

    private boolean showOverlay() {
        if (ovShown || musicOn || !vipHas(F_MINIMIZE) || mode == MODE_NONE || !vidPlaying || rewShowing
                || customView != null || card == null || !canOverlay()) return false;
        try {
            final WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
            final int sw = getResources().getDisplayMetrics().widthPixels;
            final int sh = getResources().getDisplayMetrics().heightPixels;
            ovBig = false;
            int w = ovWidth(), h = w * 9 / 16;

            ovRoot = new FrameLayout(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.BLACK);
            bg.setCornerRadius(dp(10));
            ovRoot.setBackground(bg);
            ovRoot.setClipToOutline(true);
            ovRoot.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline o) {
                    o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
                }
            });

            // कार्ड (वेबव्यू) को ऐप की स्क्रीन से निकालकर इस विंडो में लगाओ
            ovIdx = playerLayer.indexOfChild(card);
            playerLayer.removeView(card);
            card.setTranslationX(0f);
            card.setTranslationY(0f);
            ovShown = true;
            corner = dp(10);
            card.invalidateOutline();
            ovRoot.addView(card, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fullBg.setVisibility(View.GONE);
            miniBox.setVisibility(View.GONE);
            bannerHolder.setVisibility(View.GONE);
            if (shortsHolder != null) shortsHolder.setVisibility(View.GONE);

            // टच: खिसकाना और टैप (टैप -> ऐप खुले)
            View touch = new View(this);
            final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
            touch.setOnTouchListener(new View.OnTouchListener() {
                float dx, dy;
                int sx, sy;
                boolean moved;

                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            dx = e.getRawX();
                            dy = e.getRawY();
                            sx = ovLp.x;
                            sy = ovLp.y;
                            moved = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float mx = e.getRawX() - dx, my = e.getRawY() - dy;
                            if (!moved && Math.abs(mx) + Math.abs(my) > slop) moved = true;
                            if (moved) {
                                ovLp.x = Math.max(0, Math.min(sw - ovLp.width, (int) (sx + mx)));
                                ovLp.y = Math.max(0, Math.min(sh - ovLp.height, (int) (sy + my)));
                                try {
                                    ((WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE))
                                            .updateViewLayout(ovRoot, ovLp);
                                } catch (Throwable ignored) {
                                }
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (!moved) openAppFromOverlay();
                            return true;
                    }
                    return true;
                }
            });
            ovRoot.addView(touch, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // ✕ (ऊपर दाएँ)
            TextView x = barBtn("✕", 14, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    restoreFromOverlay();
                    stopPlayer();
                }
            });
            x.setBackground(roundBg(0x99000000));
            FrameLayout.LayoutParams xl = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP | Gravity.END);
            xl.setMargins(0, dp(6), dp(6), 0);
            ovRoot.addView(x, xl);

            // ⏯ (नीचे बाएँ)
            ovPp = barBtn("❚❚", 13, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (pWeb != null) pWeb.evaluateJavascript("toggle()", null);
                }
            });
            ovPp.setBackground(roundBg(0x99000000));
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.BOTTOM | Gravity.START);
            pl.setMargins(dp(6), 0, 0, dp(6));
            ovRoot.addView(ovPp, pl);

            // ⤢ बड़ा / छोटा (नीचे दाएँ)
            ovZoom = barBtn("⤢", 15, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ovBig = !ovBig;
                    int nw = ovWidth(), nh = nw * 9 / 16;
                    int sw2 = getResources().getDisplayMetrics().widthPixels;
                    int sh2 = getResources().getDisplayMetrics().heightPixels;
                    ovLp.width = nw;
                    ovLp.height = nh;
                    ovLp.x = Math.max(0, Math.min(sw2 - nw, ovLp.x));
                    ovLp.y = Math.max(0, Math.min(sh2 - nh, ovLp.y));
                    ovZoom.setText(ovBig ? "⤡" : "⤢");
                    try {
                        ((WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE))
                                .updateViewLayout(ovRoot, ovLp);
                    } catch (Throwable ignored) {
                    }
                    if (pWeb != null) pWeb.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (pWeb != null) pWeb.evaluateJavascript("fitShield()", null);
                        }
                    }, 120);
                }
            });
            ovZoom.setBackground(roundBg(0x99000000));
            FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.BOTTOM | Gravity.END);
            zl.setMargins(0, 0, dp(6), dp(6));
            ovRoot.addView(ovZoom, zl);
            ovPp.setText(vidPlaying ? "❚❚" : "▶\uFE0E");

            ovLp = new WindowManager.LayoutParams(w, h,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            ovLp.gravity = Gravity.TOP | Gravity.START;
            ovLp.x = sw - w - dp(12);
            ovLp.y = sh - h - dp(140);
            wm.addView(ovRoot, ovLp);

            if (pWeb != null) {
                pWeb.evaluateJavascript("setMini(true);setPip(true);keepAlive()", null);
                pWeb.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (pWeb != null) pWeb.evaluateJavascript("fitShield();keepAlive()", null);
                    }
                }, 300);
            }
            return true;
        } catch (Throwable t) {
            restoreFromOverlay();
            return false;
        }
    }

    /** विंडो हटाकर कार्ड को ऐप की स्क्रीन में वापस लगाता है */
    private void restoreFromOverlay() {
        if (!ovShown) return;
        ovShown = false;
        try {
            ((WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE)).removeViewImmediate(ovRoot);
        } catch (Throwable ignored) {
        }
        try {
            ovRoot.removeView(card);
        } catch (Throwable ignored) {
        }
        playerLayer.addView(card, Math.max(0, Math.min(ovIdx, playerLayer.getChildCount())),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        ovRoot = null;
        ovPp = null;
        ovZoom = null;
        corner = (mode == MODE_MINI) ? dp(12) : dp(16);
        card.invalidateOutline();
        fullBg.setVisibility(mode == MODE_FULL ? View.VISIBLE : View.GONE);
        miniBox.setVisibility(mode == MODE_MINI ? View.VISIBLE : View.GONE);
        applyCardLayout();
        if (pWeb != null) {
            pWeb.evaluateJavascript("setPip(false);setMini(" + (mode == MODE_MINI) + ")", null);
        }
        updateShortsBanner(false);
    }

    private void openAppFromOverlay() {
        restoreFromOverlay();
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {
        }
    }

    /** पहली बार वीडियो चलने पर एक बार: बाहर की तैरती विंडो की अनुमति माँगो */
    private boolean ovAsked;

    private void askOverlayPermissionOnce() {
        if (ovAsked || isFinishing() || Build.VERSION.SDK_INT < 26 || canOverlay()) return;
        ovAsked = true;
        android.content.SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (sp.getBoolean("ov_asked", false)) return;
        sp.edit().putBoolean("ov_asked", true).apply();
        new android.app.AlertDialog.Builder(this)
                .setMessage("ऐप नीचे करने पर वीडियो की छोटी तैरती विंडो बाकी ऐप्स के ऊपर दिखाने के लिए "
                        + "\"दूसरी ऐप्स के ऊपर दिखाएँ\" की अनुमति दें")
                .setPositiveButton("अनुमति दें", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Throwable ignored) {
                        }
                    }
                })
                .setNegativeButton("अभी नहीं", null)
                .show();
    }

    // ------------------------------------------------------------------
    // ऐप को नीचे (होम/जेस्चर) करने पर वीडियो छोटी विंडो (Picture-in-Picture) में सब ऐप के ऊपर चलता रहे
    // ------------------------------------------------------------------
    private boolean vidPlaying, inPip, resumedNow;

    private final Runnable pipCheck = new Runnable() {
        @Override
        public void run() {
            // छोटी विंडो को ✕ से बंद किया गया (ऐप सामने नहीं आया) -> वीडियो रोक दो
            if (!resumedNow && !inPip && mode != MODE_NONE && pWeb != null) {
                stopPlayer();
            }
        }
    };

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (showOverlay()) return;     // अपनी तैरती विंडो (अनुमति हो तो)
        enterPip();                    // वरना सिस्टम की छोटी विंडो
    }

    // ------------------------------------------------------------------
    // PiP (होम स्क्रीन पर तैरती विंडो): सिर्फ़ दो बटन -> चलाएँ/रोकें और बंद
    // ------------------------------------------------------------------
    private static final String ACT_PIP_PP = "com.videoplayer.app.PIP_PP";
    private static final String ACT_PIP_CLOSE = "com.videoplayer.app.PIP_CLOSE";
    private static final String ACT_PIP_NEXT = "com.videoplayer.app.PIP_NEXT";
    private BroadcastReceiver pipReceiver;

    private PictureInPictureParams pipParams() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9));
        if (Build.VERSION.SDK_INT >= 26) {
            ArrayList<RemoteAction> acts = new ArrayList<RemoteAction>();
            acts.add(new RemoteAction(
                    Icon.createWithResource(this, vidPlaying ? android.R.drawable.ic_media_pause
                            : android.R.drawable.ic_media_play),
                    vidPlaying ? "रोकें" : "चलाएँ", "play/pause", pipIntent(ACT_PIP_PP, 11)));
            b.setActions(acts);
        }
        return b.build();
    }

    private PendingIntent pipIntent(String action, int code) {
        Intent i = new Intent(action).setPackage(getPackageName());
        return PendingIntent.getBroadcast(this, code, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void registerPipReceiver() {
        pipReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent in) {
                String a = in == null ? null : in.getAction();
                if (ACT_PIP_PP.equals(a)) {
                    if (pWeb != null) pWeb.evaluateJavascript("toggle()", null);
                } else if (ACT_PIP_NEXT.equals(a)) {
                    playNext();
                } else if (ACT_PIP_CLOSE.equals(a)) {
                    stopPlayer();
                    if (inPip) moveTaskToBack(true);   // छोटी विंडो भी हट जाए
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(ACT_PIP_PP);
        f.addAction(ACT_PIP_CLOSE);
        f.addAction(ACT_PIP_NEXT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pipReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(pipReceiver, f);
    }

    /** म्यूज़िक मोड में ऐप नीचे / स्क्रीन बंद होने पर भी WebView को "दिखता हुआ" मानो, ताकि गाना और आवाज़ चलती रहे */
    private class BgWebView extends WebView {
        BgWebView(Context c) {
            super(c);
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            // ऐप नीचे/स्क्रीन बंद होने पर Android खिड़की को GONE बताता है; तब भी (INVISIBLE/GONE दोनों में)
            // वेबव्यू को "दिखता हुआ" मानो, वरना वीडियो का समय चलता है पर आवाज़ बंद हो जाती है
            if (musicOn && mode != MODE_NONE && !isFinishing()) visibility = View.VISIBLE;
            super.onWindowVisibilityChanged(visibility);
        }
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26 || musicOn || !vipHas(F_MINIMIZE) || inPip || mode == MODE_NONE || !vidPlaying
                || rewShowing || customView != null) return;
        try {
            enterPictureInPictureMode(pipParams());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPip, newConfig);
        inPip = isInPip;
        if (card == null || pWeb == null) return;
        if (isInPip) {
            hideFsNotice();
            fullBg.setVisibility(View.GONE);
            miniBox.setVisibility(View.GONE);
            bannerHolder.setVisibility(View.GONE);
            if (shortsHolder != null) shortsHolder.setVisibility(View.GONE);
            corner = 0;
            card.invalidateOutline();
            applyCardLayout();
            miniBox.setVisibility(View.GONE);      // अंदर के अपने बटन नहीं; सिर्फ़ सिस्टम का ▶/❚❚ और ✕
            pWeb.evaluateJavascript("setMini(true);setPip(true);keepAlive()", null);
            pWeb.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (inPip && pWeb != null) pWeb.evaluateJavascript("keepAlive()", null);
                }
            }, 700);
        } else {
            pWeb.evaluateJavascript("setPip(false)", null);
            corner = (mode == MODE_MINI) ? dp(12) : dp(16);
            card.invalidateOutline();
            fullBg.setVisibility(mode == MODE_FULL ? View.VISIBLE : View.GONE);
            miniBox.setVisibility(mode == MODE_MINI ? View.VISIBLE : View.GONE);
            applyCardLayout();
            pWeb.evaluateJavascript("setMini(" + (mode == MODE_MINI) + ")", null);
            updateShortsBanner(false);
            root.removeCallbacks(pipCheck);
            root.postDelayed(pipCheck, 500);
        }
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
            if (VideoLink.parse(url) != null) {
                if (mode == MODE_MINI) setMode(MODE_FULL);   // शेयर किया लिंक बड़े प्लेयर में
                openPlayer(url);
            }
        }
    }

    /** YouTube पेज से: वीडियो पर क्लिक */
    private class Bridge {
        /** होम पेज से Shorts पर टैप: अलग वेबव्यू में खोलो (true). Shorts के अंदर से हो तो false -> YouTube खुद संभाले */
        @JavascriptInterface
        public boolean openShorts(final String url) {
            if (ytWeb != homeWeb) return false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    openShortsOverlay(url);
                }
            });
            return true;
        }

        @JavascriptInterface
        public void shorts() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mode != MODE_NONE) stopPlayer();
                }
            });
        }

        /** थंबनेल पर क्लिक: वीडियो उसी थंबनेल की जगह, उसी के बराबर चले (CSS px में जगह) */
        @JavascriptInterface
        public void playInline(final String url, final double l, final double tp, final double w, final double h) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (VideoLink.parse(url) == null) return;
                    if (musicOn || !vipHas(F_INLINE) || ytWeb != homeWeb || w < 60 || h < 30) {
                        openPlayer(url);
                        return;
                    }
                    inlineSet(l, tp, w, h);
                    inlineOn = true;
                    inlineSized = 0;
                    openPlayer(url);
                }
            });
        }

        /** पेज स्क्रॉल हुआ: थंबनेल की नई जगह */
        @JavascriptInterface
        public void pin(final double l, final double tp, final double w, final double h) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!inlineOn || mode != MODE_MINI) return;
                    inlineSet(l, tp, w, h);
                    placeInline();
                    // थंबनेल ऊपर (या नीचे) स्क्रॉल होकर निकल गया -> वीडियो पूरा बंद
                    float d = getResources().getDisplayMetrics().density;
                    float topRel = (float) (tp * d);
                    if (topRel + inH * 0.6f < 0 || topRel > inViewH - inH * 0.4f) stopPlayer();
                }
            });
        }

        /** पेज बदल गया, थंबनेल रहा ही नहीं */
        @JavascriptInterface
        public void pinLost() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (inlineOn && mode == MODE_MINI) stopPlayer();
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
        /** नीचे की बार के 💬 बटन से: कमेंट खोलो / बंद करो */
        @JavascriptInterface
        public void comments(final String videoId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (cmOpen) closeComments(); else openComments(videoId);
                }
            });
        }

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

        /** गाना खत्म / नोटिफ़िकेशन-बार का अगला बटन: अगला गाना */
        @JavascriptInterface
        public void next() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    playNext();
                }
            });
        }

        /** वीडियो एम्बेड नहीं हो सकता (म्यूज़िक मोड) -> अपने-आप अगला, पर लगातार 3 बार से ज़्यादा नहीं */
        @JavascriptInterface
        public void skipError() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (++errStreak <= 3) {
                        playNext();
                    } else {
                        Toast.makeText(MainActivity.this, "गाने नहीं चल पा रहे, कोई और चुनें",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        /** गाने का नाम + id (नोटिफ़िकेशन और नीचे के बार में दिखाने के लिए) */
        @JavascriptInterface
        public void meta(final String title, final String id) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    curTitle = title == null ? "" : title;
                    if (id != null && id.length() > 0) {
                        curId = id;
                        markPlayed(id);
                    }
                    if (mbTitle != null) mbTitle.setText(curTitle);
                    loadThumb(curId);
                    pushMusic();
                }
            });
        }

        @JavascriptInterface
        public void progress(final double cur, final double dur) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    lastPos = cur;
                    lastDur = dur;
                    if (mbProg != null && dur > 0) {
                        mbProg.setScaleX((float) Math.max(0, Math.min(1, cur / dur)));
                    }
                }
            });
        }

        @JavascriptInterface
        public void state(final int s) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    vidPlaying = (s == 1 || s == 3);
                    miniPp.setText((s == 1 || s == 3) ? "❚❚" : "▶\uFE0E");
                    if (s == 1) errStreak = 0;
                    if (mbPp != null) mbPp.setText(vidPlaying ? "❚❚" : "▶\uFE0E");
                    if (ovPp != null) ovPp.setText(vidPlaying ? "❚❚" : "▶\uFE0E");
                    if (vidPlaying) askOverlayPermissionOnce();
                    pushMusic();
                    if (inPip && Build.VERSION.SDK_INT >= 26) {
                        try {
                            setPictureInPictureParams(pipParams());
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (closingShorts) {
            return;   // Shorts बंद होने का एनिमेशन चल रहा है
        }
        if (cmOpen) {
            closeComments();
        } else if (customView != null) {
            pWeb.getWebChromeClient().onHideCustomView();
        } else if (fsOn) {
            setFullscreen(false);
        } else if (mode == MODE_FULL) {
            setMode(MODE_MINI);          // बैक -> वीडियो नीचे छोटा होकर चलता रहेगा
        } else if (ytWeb != null && homeWeb != null && ytWeb != homeWeb) {
            // Shorts वेबव्यू से बैक: अलग वेबव्यू हटाओ, होम पेज बिना रीफ्रेश वैसा ही दिखेगा
            if (!onShorts && ytWeb.canGoBack()) ytWeb.goBack();
            else closeShortsOverlay();
        } else if (ytWeb != null && ytWeb.canGoBack()) {
            // Shorts से बैक: पुराना पेज पहले से खुला है, इसलिए बिना लोडिंग के तुरंत दिखे
            if (!onShorts) showBackLoading();   // बाकी पेजों पर काली स्क्रीन की जगह लोडिंग
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
        boolean toastErr;     // फेल होने की वजह स्क्रीन पर दिखाओ (जाँच के लिए)
        long retryMs = 30000;

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
                if (slot.toastErr) {
                    final String why = errorInfo == null ? "?" : String.valueOf(errorInfo.errorMessage);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Shorts बैनर नहीं आया: " + why,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
                Log.e("UnityBanner", "banner failed: "
                        + (errorInfo == null ? "?" : String.valueOf(errorInfo.errorMessage)));
                if (slot.tries < 8 && slot.view != null) {
                    slot.tries++;
                    slot.view.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            slot.load();
                        }
                    }, slot.retryMs);
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
                                    loadRewarded();
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

    /** Shorts को अलग वेबव्यू में खोलता है; नीचे वाला होम पेज छुआ नहीं जाता (रीलोड नहीं होता) */
    private void openShortsOverlay(String url) {
        if (homeWeb == null || ytWeb != homeWeb || ytLocked) return;
        if (mode != MODE_NONE) stopPlayer();
        try {
            homeWeb.evaluateJavascript(
                    "document.querySelectorAll('video').forEach(function(v){try{v.pause();}catch(e){}})", null);
            // (पहले यहाँ homeWeb.onPause() था: उसी से लौटते समय काली झलक आती थी, इसलिए हटाया)
        } catch (Throwable ignored) {
        }
        // पहले से तैयार रखा (गरम) Shorts वेबव्यू हो तो वही, ताकि जल्दी खुले
        final WebView sw;
        if (shortsSpare != null) {
            sw = shortsSpare;
            shortsSpare = null;
            sw.getSettings().setMediaPlaybackRequiresUserGesture(false);
        } else {
            sw = new WebView(this);
            sw.setBackgroundColor(Color.BLACK);
            configureYt(sw);
        }
        ytWeb = sw;
        updateVipCard();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(sw, root.indexOfChild(homeWeb) + 1, lp);
        // खुलते समय ज़ूम-इन: छोटा होकर बड़ा (बैक के ज़ूम-आउट का उल्टा)
        final int rad = dp(24);
        sw.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rad);
            }
        });
        sw.setClipToOutline(true);
        sw.setScaleX(0.6f);
        sw.setScaleY(0.6f);
        sw.setAlpha(0f);
        sw.animate().cancel();
        sw.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(240)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        sw.setClipToOutline(false);
                    }
                }).start();
        sw.loadUrl(url);
        onYtUrl(url);
    }

    // अगला Shorts जल्दी खुले, इसलिए एक Shorts वेबव्यू पहले से (बिना आवाज़ के) तैयार रखते हैं
    private WebView shortsSpare;
    private boolean spareScheduled;

    private void prepareShortsSpare() {
        if (shortsSpare != null || homeWeb == null || isFinishing()) return;
        try {
            WebView sw = new WebView(this);
            sw.setBackgroundColor(Color.BLACK);
            configureYt(sw);
            sw.getSettings().setMediaPlaybackRequiresUserGesture(true);   // तैयार रहते समय कुछ न चले
            shortsSpare = sw;
            sw.loadUrl("https://m.youtube.com/shorts");
        } catch (Throwable ignored) {
        }
    }

    /** Shorts वेबव्यू हटाकर वही होम पेज वापस सामने लाता है */
    private boolean closingShorts;

    private void closeShortsOverlay() {
        if (homeWeb == null || ytWeb == homeWeb || closingShorts) return;
        closingShorts = true;
        final WebView sw = ytWeb;
        // 1) पीछे वाला होम पेज पहले ही जगा दो, ताकि एनिमेशन के दौरान वही दिखे (काली स्क्रीन नहीं)
        try {
            homeWeb.setVisibility(View.VISIBLE);
            homeWeb.onResume();
            homeWeb.resumeTimers();
            homeWeb.invalidate();
        } catch (Throwable ignored) {
        }
        // Shorts की आवाज़ तुरंत बंद
        try {
            sw.evaluateJavascript(
                    "document.querySelectorAll('video').forEach(function(v){try{v.pause();}catch(e){}})", null);
        } catch (Throwable ignored) {
        }
        // 2) बाकी स्थिति अभी बदल दो (Shorts वेबव्यू की खबरें अब अनदेखी होंगी)
        ytWeb = homeWeb;
        holdLoadUntil = 0;
        backWait = false;
        ytLoadFailed = false;
        root.removeCallbacks(backHide);
        hideLoading();
        onShorts = false;
        updateShortsBanner(false);
        applyYtMargin();
        // 3) Shorts का पर्दा गोल कोनों के साथ सिकुड़ता हुआ गायब -> पीछे का पेज दिखता है
        final int rad = dp(24);
        sw.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rad);
            }
        });
        sw.setClipToOutline(true);
        sw.setPivotX(sw.getWidth() / 2f);
        sw.setPivotY(sw.getHeight() / 2f);
        final Runnable finish = new Runnable() {
            @Override
            public void run() {
                if (!closingShorts) return;
                closingShorts = false;
                try {
                    root.removeView(sw);
                    sw.destroy();
                } catch (Throwable ignored) {
                }
            }
        };
        sw.animate().cancel();
        sw.animate().scaleX(0.6f).scaleY(0.6f).alpha(0f).setDuration(240)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(finish).start();
        root.postDelayed(finish, 600);   // पक्का: एनिमेशन किसी वजह से न चले तो भी सफ़ाई हो
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                prepareShortsSpare();
            }
        }, 3000);
    }

    private void inject(WebView view) {
        view.evaluateJavascript(HOOK, null);
        view.evaluateJavascript(HIDE_NAV, null);
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
        int at = 0;
        if (homeWeb != null && homeWeb != ytWeb && root.indexOfChild(homeWeb) >= 0) {
            at = root.indexOfChild(homeWeb) + 1;
        }
        root.addView(ytWeb, at, yl);
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
        root.removeCallbacks(healRun);     // पुराने बचे हुए हटाओ, ढेर न लगे
        int[] delays = {150, 400, 800, 1500};
        for (int i = 0; i < delays.length; i++) root.postDelayed(healRun, delays[i]);
    }

    private final Runnable healRun = new Runnable() {
        @Override
        public void run() {
            healYt();
        }
    };

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
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    // player.html एक बार ही पढ़ते हैं (टैप पर डिस्क से पढ़ने में देर लगती थी)
    private volatile String playerHtmlCache;

    private String playerHtml() throws Exception {
        String h = playerHtmlCache;
        if (h == null) {
            h = readAsset("player.html");
            playerHtmlCache = h;
        }
        return h;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (root != null) {
            root.removeCallbacks(vipTick);
            root.removeCallbacks(autoRetry);   // WebView destroy होने के बाद यह देरी वाला retry न चले (NPE क्रैश से बचाव)
            root.removeCallbacks(loadSlow);
        }
        if (shortsSpare != null) {
            try {
                shortsSpare.destroy();
            } catch (Throwable ignored) {
            }
            shortsSpare = null;
        }
        if (ovShown) {
            try {
                ((WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE)).removeViewImmediate(ovRoot);
            } catch (Throwable ignored) {
            }
            ovShown = false;
        }
        try {
            if (pipReceiver != null) unregisterReceiver(pipReceiver);
        } catch (Throwable ignored) {
        }
        MusicService.controls = null;
        try {
            stopService(new Intent(this, MusicService.class));
        } catch (Throwable ignored) {
        }
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
        if (homeWeb != null && homeWeb != ytWeb) {
            homeWeb.destroy();
        }
        homeWeb = null;
        if (ytWeb != null) {
            ytWeb.destroy();
            ytWeb = null;
        }
        if (browserWeb != null) {
            try {
                browserWeb.loadUrl("about:blank");
                browserWeb.destroy();
            } catch (Throwable ignored) {
            }
            browserWeb = null;
        }
        super.onDestroy();
    }
}
