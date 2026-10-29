package com.videoplayer.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

/**
 * म्यूज़िक मोड का फ़ोरग्राउंड सर्विस + मीडिया नोटिफ़िकेशन (म्यूज़िक प्लेयर जैसा).
 * - नोटिफ़िकेशन में गाने का नाम, थंबनेल, चलाएँ/रोकें, अगला, बंद
 * - लॉक स्क्रीन / क्विक पैनल / ब्लूटूथ-हेडसेट के बटन भी यहीं से (MediaSession)
 * असली प्लेयर MainActivity के वेबव्यू में चलता है; यह सर्विस सिर्फ़ ऐप को ज़िंदा रखती है
 * और बटनों को Controls के ज़रिए MainActivity तक पहुँचाती है।
 */
public class MusicService extends Service {

    public interface Controls {
        void onPlayPause();

        void onNext();

        void onClose();

        void onSeek(long ms);
    }

    // MainActivity इन्हें भरती है, फिर refresh() बुलाती है
    static volatile Controls controls;
    static volatile String title = "";
    static volatile boolean playing;
    static volatile Bitmap art;
    static volatile long posMs, durMs;

    private static MusicService instance;

    private static final String ACT_PP = "com.videoplayer.app.MUSIC_PP";
    private static final String ACT_NEXT = "com.videoplayer.app.MUSIC_NEXT";
    private static final String ACT_CLOSE = "com.videoplayer.app.MUSIC_CLOSE";
    private static final String CHANNEL = "vp_music";
    private static final int NID = 4711;

    private MediaSession session;

    /** नाम / चल-रुक / थंबनेल बदला: चल रही सर्विस का नोटिफ़िकेशन ताज़ा करो */
    static void refresh() {
        MusicService s = instance;
        if (s != null) s.update();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "म्यूज़िक प्लेयर",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        session = new MediaSession(this, "VideoPlayerMusic");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                Controls c = controls;
                if (c != null && !playing) c.onPlayPause();
            }

            @Override
            public void onPause() {
                Controls c = controls;
                if (c != null && playing) c.onPlayPause();
            }

            @Override
            public void onSkipToNext() {
                Controls c = controls;
                if (c != null) c.onNext();
            }

            @Override
            public void onStop() {
                Controls c = controls;
                if (c != null) c.onClose();
            }

            @Override
            public void onCustomAction(String action, Bundle extras) {
                if ("close".equals(action)) {
                    Controls c = controls;
                    if (c != null) c.onClose();
                }
            }

            @Override
            public void onSeekTo(long pos) {
                Controls c = controls;
                if (c != null) c.onSeek(pos);
            }
        });
        session.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        Controls c = controls;
        if (ACT_PP.equals(a)) {
            if (c != null) c.onPlayPause();
        } else if (ACT_NEXT.equals(a)) {
            if (c != null) c.onNext();
        } else if (ACT_CLOSE.equals(a)) {
            if (c != null) {
                c.onClose();
            } else {
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        update();
        return START_NOT_STICKY;
    }

    private PendingIntent act(String action, int code) {
        Intent i = new Intent(this, MusicService.class).setAction(action);
        return PendingIntent.getService(this, code, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification.Action action(int icon, String label, PendingIntent pi) {
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build();
    }

    private void update() {
        final boolean pl = playing;
        final String t = (title == null || title.length() == 0) ? "गाना लोड हो रहा है…" : title;
        final Bitmap a = art;

        MediaMetadata.Builder md = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "YouTube");
        if (a != null) md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, a);
        if (durMs > 0) md.putLong(MediaMetadata.METADATA_KEY_DURATION, durMs);
        session.setMetadata(md.build());
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO)
                .setState(pl ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        posMs, pl ? 1f : 0f)
                // नए Android की मीडिया-कंट्रोल में नोटिफ़िकेशन-बटन की जगह यही बटन दिखते हैं: ✕ बंद
                .addCustomAction(new PlaybackState.CustomAction.Builder("close", "बंद करें",
                        android.R.drawable.ic_menu_close_clear_cancel).build())
                .build());

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(t)
                .setContentText("YouTube")
                .setLargeIcon(a)
                .setContentIntent(openPi)
                .setVisibility(Notification.VISIBILITY_PUBLIC)   // लॉक स्क्रीन पर भी दिखे
                .setShowWhen(false)
                .setOngoing(true)
                .addAction(action(pl ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        pl ? "रोकें" : "चलाएँ", act(ACT_PP, 1)))
                .addAction(action(android.R.drawable.ic_media_next, "अगला", act(ACT_NEXT, 2)))
                .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "बंद करें", act(ACT_CLOSE, 3)))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NID, n);
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        try {
            if (session != null) {
                session.setActive(false);
                session.release();
            }
        } catch (Throwable ignored) {
        }
        session = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
