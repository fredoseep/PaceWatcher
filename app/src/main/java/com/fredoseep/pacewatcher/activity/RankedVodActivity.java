package com.fredoseep.pacewatcher.activity;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class RankedVodActivity extends AppCompatActivity {
    public static final String EXTRA_VOD_URL = "vod_url";
    public static final String EXTRA_MARKERS = "markers";

    private final TreeSet<Long> markersMs = new TreeSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private MatchMarkerBar markerBar;
    private TextView status;
    private LinearLayout overlay;
    private final Runnable hideControls = () -> {
        if (overlay != null) overlay.setVisibility(View.GONE);
    };
    private boolean playerReady;
    private long positionMs;
    private long durationMs;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (webView == null) return;
            webView.evaluateJavascript("(function(){if(!window.matchPlayer||!window.matchReady)return null;return JSON.stringify({p:window.matchPlayer.getCurrentTime(),d:window.matchPlayer.getDuration()});})()", value -> {
                try {
                    if (value != null && !"null".equals(value)) {
                        String json = new JSONArray("[" + value + "]").getString(0);
                        JSONObject progress = new JSONObject(json);
                        double p = progress.optDouble("p", 0);
                        double d = progress.optDouble("d", 0);
                        if (Double.isFinite(p) && Double.isFinite(d) && d > 0) {
                            playerReady = true;
                            positionMs = Math.max(0, (long) (p * 1000));
                            durationMs = (long) (d * 1000);
                            markerBar.update(positionMs, durationMs);
                            status.setText(format(positionMs) + " / " + format(durationMs));
                        }
                    }
                } catch (Exception ignored) { }
            });
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String videoId = parseTwitchVideoId(getIntent().getStringExtra(EXTRA_VOD_URL));
        if (videoId == null) {
            TextView error = new TextView(this);
            error.setText("此链接不是可识别的 Twitch 回放地址");
            setContentView(error);
            return;
        }
        try {
            JSONArray points = new JSONArray(getIntent().getStringExtra(EXTRA_MARKERS));
            for (int i = 0; i < points.length(); i++) {
                long pointMs = points.optLong(i, -1);
                if (pointMs >= 0) markersMs.add(pointMs);
            }
        } catch (Exception ignored) { }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        webView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) showControls();
            return false; // 触摸继续交给 Twitch 播放器
        });

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(12), dp(4), dp(12), dp(8));
        overlay.setBackgroundColor(Color.argb(110, 0, 0, 0));
        FrameLayout.LayoutParams floatParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        floatParams.topMargin = dp(8);
        root.addView(overlay, floatParams);
        markerBar = new MatchMarkerBar(this);
        overlay.addView(markerBar, new LinearLayout.LayoutParams(-1, dp(42)));
        markerBar.setMarkers(markersMs);
        markerBar.setOnSeekListener(position -> {
            seekTo(position);
            showControls();
        });
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setText("正在加载 Twitch 播放器…");
        overlay.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout controls = new LinearLayout(this);
        overlay.addView(controls);
        Button back = button(controls, "−10 秒");
        Button next = button(controls, "下一场");
        Button forward = button(controls, "+10 秒");
        back.setOnClickListener(v -> { seekTo(positionMs - 10_000); showControls(); });
        forward.setOnClickListener(v -> { seekTo(positionMs + 10_000); showControls(); });
        next.setOnClickListener(v -> {
            Long point = markersMs.higher(positionMs + 1000L);
            if (point != null) seekTo(point);
            showControls();
        });
        overlay.setVisibility(View.GONE);

        long start = markersMs.isEmpty() ? 0 : markersMs.first() / 1000;
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body,#player{margin:0;width:100%;height:100%;min-width:400px;min-height:300px;background:#000;overflow:hidden}</style>"
                + "<script src='https://player.twitch.tv/js/embed/v1.js'></script></head><body><div id='player'></div><script>"
                + "window.matchReady=false;window.matchPlayer=new Twitch.Player('player',{width:'100%',height:'100%',"
                + "video:'v" + videoId + "',parent:['localhost'],autoplay:false,time:'" + twitchTime(start) + "'});"
                + "window.matchPlayer.addEventListener(Twitch.Player.READY,function(){window.matchReady=true;});"
                + "</script></body></html>";
        // parent 必须与加载页面的域名一致。此 HTML 仅加载在本应用的 localhost 来源中。
        webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null);
        handler.post(poll);
    }

    private static String parseTwitchVideoId(String url) {
        if (url == null) return null;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !(host.equalsIgnoreCase("twitch.tv") || host.toLowerCase(Locale.ROOT).endsWith(".twitch.tv"))) return null;
        List<String> segments = uri.getPathSegments();
        for (int i = 0; i + 1 < segments.size(); i++) {
            if (segments.get(i).equals("videos") || segments.get(i).equals("v")) {
                String id = segments.get(i + 1);
                return id.matches("[0-9]+") ? id : null;
            }
        }
        return null;
    }

    private static String twitchTime(long seconds) {
        return seconds / 3600 + "h" + seconds % 3600 / 60 + "m" + seconds % 60 + "s";
    }

    private static String format(long milliseconds) {
        long seconds = milliseconds / 1000;
        return String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private void seekTo(long targetMs) {
        if (!playerReady || webView == null) return;
        long clamped = Math.max(0, Math.min(targetMs, durationMs));
        webView.evaluateJavascript("window.matchPlayer.seek(" + clamped / 1000.0 + ");", null);
    }

    private void showControls() {
        if (overlay == null) return;
        overlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls, 5_000);
    }

    private Button button(LinearLayout parent, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(165, 38, 56, 88));
        background.setCornerRadius(dp(24));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(6), 0, dp(6), 0);
        parent.addView(button, params);
        return button;
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        handler.removeCallbacks(poll);
        handler.removeCallbacks(hideControls);
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
