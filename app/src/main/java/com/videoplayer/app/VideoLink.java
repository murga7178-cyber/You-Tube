package com.videoplayer.app;

import android.net.Uri;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** YouTube link से video id / playlist / start time निकालता है। */
public class VideoLink {
    public String id;     // 11 अक्षर की video id (playlist-only में null)
    public String list;   // playlist id (हो सकता है null)
    public int start;     // सेकंड

    public static VideoLink parse(String url) {
        if (url == null) return null;
        Uri u;
        try {
            u = Uri.parse(url.trim());
        } catch (Exception e) {
            return null;
        }
        String host = u.getHost();
        if (host == null) return null;
        host = host.toLowerCase().replaceFirst("^(www\\.|m\\.|music\\.)", "");

        String id = null;
        String list = u.getQueryParameter("list");
        List<String> p = u.getPathSegments();

        if (host.equals("youtu.be")) {
            if (!p.isEmpty()) id = p.get(0);
        } else if (host.equals("youtube.com") || host.equals("youtube-nocookie.com")) {
            if (p.isEmpty()) return null;
            String first = p.get(0);
            if (first.equals("watch")) {
                id = u.getQueryParameter("v");
            } else if ((first.equals("shorts") || first.equals("live")
                    || first.equals("embed") || first.equals("v")) && p.size() > 1) {
                id = p.get(1);
            } else if (!first.equals("playlist")) {
                return null;
            }
        } else {
            return null;
        }

        if (id != null && !id.matches("[\\w-]{11}")) id = null;
        if (list != null && !list.matches("[\\w-]+")) list = null;
        if (id == null && list == null) return null;

        VideoLink v = new VideoLink();
        v.id = id;
        v.list = list;
        String t = u.getQueryParameter("t");
        if (t == null) t = u.getQueryParameter("start");
        v.start = parseTime(t);
        return v;
    }

    private static int parseTime(String t) {
        if (t == null || t.isEmpty()) return 0;
        try {
            if (t.matches("\\d+")) return Integer.parseInt(t);
            Matcher m = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matcher(t);
            if (m.matches()) {
                return num(m.group(1)) * 3600 + num(m.group(2)) * 60 + num(m.group(3));
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int num(String s) {
        return s == null ? 0 : Integer.parseInt(s);
    }

    /** प्लेयर में चलाने वाला embed link */
    public String embedUrl() {
        StringBuilder sb = new StringBuilder("https://www.youtube-nocookie.com/embed/");
        sb.append(id != null ? id : "videoseries");
        sb.append("?autoplay=1&playsinline=1&rel=0&modestbranding=1");
        if (start > 0) sb.append("&start=").append(start);
        if (list != null) sb.append("&list=").append(list);
        return sb.toString();
    }

    /** YouTube पर खोलने वाला असली link */
    public String watchUrl() {
        if (id != null) {
            return "https://www.youtube.com/watch?v=" + id + (list != null ? "&list=" + list : "");
        }
        return "https://www.youtube.com/playlist?list=" + list;
    }
}
