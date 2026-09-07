package com.fredoseep.pacewatcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView webView;
    private String currentStreamer = "";
    private boolean isRunning = true;

    // UI 组件
    private Button floatingBtn;
    private ScrollView terminalScroll;
    private TextView terminalText;
    private AudioManager audioManager;
    // 自动隐藏逻辑
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = () -> {
        if (floatingBtn != null && terminalScroll.getVisibility() == View.GONE) {
            floatingBtn.setVisibility(View.GONE);
        }
    };

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // 保持屏幕常亮
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. 初始化 WebView (底层)
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
        rootLayout.addView(webView);


// 在 onCreate 中初始化 WebView 之后，请求不独占的音频焦点
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK // 关键：允许与其它音频（如YouTube）同时播放并降低音量混音
            );
        }
        // 2. 初始化 Terminal 面板 (中层)
        terminalScroll = new ScrollView(this);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        scrollParams.setMargins(50, 50, 50, 50);
        terminalScroll.setLayoutParams(scrollParams);
        terminalScroll.setBackgroundColor(Color.parseColor("#CC000000")); // 80% 透明度黑底
        terminalScroll.setVisibility(View.GONE);

        terminalText = new TextView(this);
        terminalText.setTextColor(Color.GREEN);
        terminalText.setTypeface(Typeface.MONOSPACE);
        terminalText.setTextSize(12f);
        terminalText.setPadding(20, 20, 20, 20);
        terminalScroll.addView(terminalText);
        rootLayout.addView(terminalScroll);

        // 3. 初始化悬浮按钮 (顶层) - 移到右上角，并加入半透明防遮挡
        floatingBtn = new Button(this);
        floatingBtn.setText("终端");
        floatingBtn.setBackgroundColor(Color.parseColor("#99333333")); // 半透明深灰
        floatingBtn.setTextColor(Color.WHITE);
        floatingBtn.setAlpha(0.8f);

        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.TOP | Gravity.END; // 更改为右上角
        btnParams.setMargins(0, 50, 50, 0); // 调整边距
        floatingBtn.setLayoutParams(btnParams);

        floatingBtn.setOnClickListener(v -> {
            if (terminalScroll.getVisibility() == View.GONE) {
                terminalScroll.setVisibility(View.VISIBLE);
                floatingBtn.setText("关闭");
                hideHandler.removeCallbacks(hideRunnable);
            } else {
                terminalScroll.setVisibility(View.GONE);
                floatingBtn.setText("终端");
                wakeUpFloatingButton();
            }
        });
        rootLayout.addView(floatingBtn);
// 强制锁定当前界面为横屏
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(rootLayout);

        logToTerminal("系统初始化完成，开始拉取 Pace...");
        startPolling();
        wakeUpFloatingButton();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            wakeUpFloatingButton();
        }
        return super.dispatchTouchEvent(ev);
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onPause() {

    }
    private void wakeUpFloatingButton() {
        if (terminalScroll.getVisibility() == View.GONE) {
            floatingBtn.setVisibility(View.VISIBLE);
            hideHandler.removeCallbacks(hideRunnable);
            hideHandler.postDelayed(hideRunnable, 5000);
        }
    }

    private void logToTerminal(String msg) {
        String timeStr = timeFormat.format(new Date());
        String logMsg = "[" + timeStr + "] " + msg + "\n";

        runOnUiThread(() -> {
            terminalText.append(logMsg);
            terminalScroll.post(() -> terminalScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void startPolling() {
        new Thread(() -> {
            while (isRunning) {
                try {
                    String newStreamer = fetchPace();
                    if (newStreamer != null && !newStreamer.isEmpty()) {
                        if (!newStreamer.equals(currentStreamer)) {
                            currentStreamer = newStreamer;
                            logToTerminal("发现新 Pace，正在切换频道至: " + currentStreamer);
                            runOnUiThread(() -> {
                                webView.loadUrl("about:blank");
                            });
                            Thread.sleep(500);


                            runOnUiThread(() -> {
                                // 核心判断：当前系统是否有其他媒体（如 YouTube）正在播放声音
                                boolean isAudioBusy = audioManager != null && audioManager.isMusicActive();

                                // 根据占用状态，动态决定静音参数
                                String mutedParam = isAudioBusy ? "true" : "false";

                                // 拼接 URL
                                String url = "https://player.twitch.tv/?channel=" + currentStreamer +
                                        "&parent=twitch.tv&muted=" + mutedParam;

                                logToTerminal("换台: " + currentStreamer + " | 智能静音: " + mutedParam);
                                webView.loadUrl(url);
                            });
                        } else {
                            logToTerminal("当前无频道更新，维持: " + currentStreamer);
                        }
                    } else {
                        logToTerminal("API 返回数据为空或解析失败。");
                    }
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    logToTerminal("轮询线程被中断: " + e.getMessage());
                } catch (Exception e) {
                    logToTerminal("发生严重异常: " + e.getMessage());
                }
            }
        }).start();
    }

    private String fetchPace() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://paceman.gg/api/ars/liveruns?gameVersion=all&liveOnly=true");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONArray jsonArray = new JSONArray(sb.toString());
                if (jsonArray.length() > 0) {
                    JSONObject firstObj = jsonArray.getJSONObject(0);
                    if (firstObj.has("user")) {
                        JSONObject userObj = firstObj.getJSONObject("user");
                        if (userObj.has("liveAccount")) {
                            return userObj.getString("liveAccount");
                        }
                    }
                }
            } else {
                logToTerminal("网络请求失败，状态码: " + responseCode);
            }
        } catch (Exception e) {
            logToTerminal("获取 Pace 异常: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        hideHandler.removeCallbacks(hideRunnable);
    }
}