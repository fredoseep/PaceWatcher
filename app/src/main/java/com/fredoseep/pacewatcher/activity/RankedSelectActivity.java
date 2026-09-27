package com.fredoseep.pacewatcher.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fredoseep.pacewatcher.AppRepository;
import com.fredoseep.pacewatcher.MyApplication;
import com.fredoseep.pacewatcher.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class RankedSelectActivity extends AppCompatActivity {
    private static final String TAG = "RankedSelectActivity";
    private static final int GREEN = Color.rgb(30, 125, 72);
    private static final int RED = Color.rgb(190, 55, 55);
    private static final int GRAY = Color.rgb(115, 120, 128);

    private EditText searchBar;
    private Button searchBtn;
    private ImageView avatar;
    private TextView playerName;
    private TextView status;
    private LinearLayout dayList;
    private int requestVersion = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ranked_select);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        searchBar = findViewById(R.id.ranked_search_edittext);
        searchBtn = findViewById(R.id.comfirm_searching_btn);
        avatar = findViewById(R.id.ranked_avatar);
        playerName = findViewById(R.id.ranked_player_name);
        status = findViewById(R.id.ranked_status);
        dayList = findViewById(R.id.ranked_day_list);
        searchBtn.setOnClickListener(v -> fetchGamePlayData(searchBar.getText().toString().trim()));
    }

    private void fetchGamePlayData(String username) {
        if (username.isEmpty()) {
            searchBar.setError("请输入玩家名称");
            return;
        }
        AppRepository repository = ((MyApplication) getApplication()).getAppRepository();
        int season = repository.getCurrentSeason();
        if (season < 0) {
            status.setText("赛季信息尚未加载，请稍后重试");
            status.setVisibility(View.VISIBLE);
            return;
        }
        final int version = ++requestVersion;
        searchBtn.setEnabled(false);
        dayList.removeAllViews();
        avatar.setVisibility(View.GONE);
        playerName.setVisibility(View.GONE);
        status.setText("正在加载比赛…");
        status.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(username, "UTF-8");
                String url = "https://mcsrranked.com/api/users/" + encoded
                        + "/matches?type=2&season=" + season + "&sort=newest&count=100";
                JSONObject root = new JSONObject(readText(url));
                if (!"success".equals(root.optString("status"))) {
                    throw new IllegalStateException("API 返回失败：" + root.optString("data"));
                }
                JSONArray matches = root.getJSONArray("data");
                // 独立获取当前玩家资料：即使本赛季没有比赛，也能展示头像。
                JSONObject profile = null;
                try {
                    JSONObject userRoot = new JSONObject(readText("https://mcsrranked.com/api/users/" + encoded));
                    if ("success".equals(userRoot.optString("status"))) {
                        profile = userRoot.optJSONObject("data");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "玩家资料加载失败，使用比赛记录中的信息", e);
                }
                LinkedHashMap<String, JSONArray> byDay = new LinkedHashMap<>();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String canonicalName = profile == null ? username : profile.optString("nickname", username);
                String uuid = profile == null ? null : profile.optString("uuid", null);
                for (int i = 0; i < matches.length(); i++) {
                    JSONObject match = matches.getJSONObject(i);
                    JSONArray players = match.optJSONArray("players");
                    if (players == null || players.length() != 2) continue;
                    for (int j = 0; j < players.length(); j++) {
                        JSONObject player = players.getJSONObject(j);
                        if ((uuid != null && uuid.equalsIgnoreCase(player.optString("uuid")))
                                || username.equalsIgnoreCase(player.optString("nickname"))) {
                            canonicalName = player.optString("nickname", username);
                            uuid = player.optString("uuid");
                        }
                    }
                    String day = dateFormat.format(new Date(match.getLong("date") * 1000L));
                    if (!byDay.containsKey(day)) byDay.put(day, new JSONArray());
                    byDay.get(day).put(match);
                }
                final String shownName = canonicalName;
                final String playerUuid = uuid;
                runOnUiThread(() -> {
                    if (version != requestVersion || isFinishing() || isDestroyed()) return;
                    searchBtn.setEnabled(true);
                    playerName.setText(shownName);
                    playerName.setVisibility(View.VISIBLE);
                    status.setVisibility(View.GONE);
                    if (byDay.isEmpty()) {
                        status.setText("该赛季没有找到排位比赛");
                        status.setVisibility(View.VISIBLE);
                    } else {
                        for (Map.Entry<String, JSONArray> entry : byDay.entrySet()) {
                            addDay(entry.getKey(), entry.getValue(), shownName, playerUuid);
                        }
                    }
                    // 玩家 UUID 来自比赛记录，头像下载也在后台线程执行。
                    loadAvatar(TextUtils.isEmpty(playerUuid) ? shownName : playerUuid, version);
                });
            } catch (Exception e) {
                Log.e(TAG, "加载比赛失败", e);
                runOnUiThread(() -> {
                    if (version != requestVersion || isFinishing() || isDestroyed()) return;
                    searchBtn.setEnabled(true);
                    status.setText("加载失败，请检查网络或玩家名称后重试");
                    status.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void addDay(String day, JSONArray matches, String searchedName, String searchedUuid) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        card.setBackground(background);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(12);
        dayList.addView(card, cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header);
        TextView heading = text(day + "  ·  " + matches.length() + " 场", Color.rgb(45, 50, 58), 17);
        heading.setTypeface(null, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        JSONObject firstVod = earliestVod(matches, searchedUuid);
        if (firstVod != null) {
            Button watch = new Button(this);
            watch.setText("观看回放");
            watch.setTextSize(12);
            watch.setTextColor(Color.WHITE);
            watch.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(49, 87, 164)));
            header.addView(watch);
            watch.setOnClickListener(v -> openVod(firstVod, matches, searchedUuid));
        }
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.optJSONObject(i);
            if (match == null) continue;
            JSONArray players = match.optJSONArray("players");
            if (players == null || players.length() != 2) continue;
            JSONObject first = players.optJSONObject(0);
            JSONObject second = players.optJSONObject(1);
            if (first == null || second == null) continue;
            JSONObject searched = first;
            JSONObject opponent = second;
            if (searchedUuid != null && !searchedUuid.isEmpty()) {
                if (searchedUuid.equalsIgnoreCase(second.optString("uuid"))) {
                    searched = second;
                    opponent = first;
                } else if (!searchedUuid.equalsIgnoreCase(first.optString("uuid"))) continue;
            } else if (searchedName.equalsIgnoreCase(second.optString("nickname"))) {
                searched = second;
                opponent = first;
            } else if (!searchedName.equalsIgnoreCase(first.optString("nickname"))) continue;

            JSONObject result = match.optJSONObject("result");
            String winner = result == null ? null : result.optString("uuid", null);
            if (result != null && result.isNull("uuid")) winner = null;
            boolean draw = TextUtils.isEmpty(winner);
            boolean searchedWon = !draw && winner.equalsIgnoreCase(searched.optString("uuid"));
            int selfColor = draw ? GRAY : searchedWon ? GREEN : RED;
            int otherColor = draw ? GRAY : searchedWon ? RED : GREEN;

            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(235, 238, 242));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
            dividerParams.topMargin = dp(10);
            dividerParams.bottomMargin = dp(10);
            card.addView(divider, dividerParams);

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            card.addView(row);
            TextView left = text(searched.optString("nickname", searchedName), selfColor, 15);
            left.setSingleLine(true);
            left.setEllipsize(TextUtils.TruncateAt.END);
            left.setTypeface(null, Typeface.BOLD);
            row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            TextView vs = text("  vs  ", GRAY, 14);
            row.addView(vs);
            TextView right = text(opponent.optString("nickname", "?"), otherColor, 15);
            right.setGravity(Gravity.END);
            right.setSingleLine(true);
            right.setEllipsize(TextUtils.TruncateAt.END);
            right.setTypeface(null, Typeface.BOLD);
            row.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    // 只使用被搜索玩家本人发布的 VOD；date 和 startsAt 都是秒级时间戳。
    private JSONObject findVod(JSONObject match, String uuid) {
        if (TextUtils.isEmpty(uuid)) return null;
        JSONArray vods = match.optJSONArray("vod");
        if (vods == null) return null;
        for (int i = 0; i < vods.length(); i++) {
            JSONObject vod = vods.optJSONObject(i);
            if (vod != null && uuid.equalsIgnoreCase(vod.optString("uuid"))
                    && vod.optLong("startsAt", -1) > 0 && !vod.optString("url").isEmpty()) {
                return vod;
            }
        }
        return null;
    }

    private JSONObject earliestVod(JSONArray matches, String uuid) {
        JSONObject selected = null;
        long earliest = Long.MAX_VALUE;
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.optJSONObject(i);
            if (match == null) continue;
            JSONObject vod = findVod(match, uuid);
            long date = match.optLong("date", -1);
            if (vod != null && date > 0 && date < earliest) {
                earliest = date;
                selected = vod;
            }
        }
        return selected;
    }

    private void openVod(JSONObject chosen, JSONArray matches, String uuid) {
        String vodUrl = chosen.optString("url");
        long startsAt = chosen.optLong("startsAt");
        JSONArray markers = new JSONArray();
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.optJSONObject(i);
            if (match == null) continue;
            JSONObject vod = findVod(match, uuid);
            if (vod == null || !vodUrl.equals(vod.optString("url"))
                    || startsAt != vod.optLong("startsAt")) continue;
            JSONObject result = match.optJSONObject("result");
            long durationMs = result == null ? -1 : result.optLong("time", -1);
            long matchDate = match.optLong("date", -1);
            if (durationMs <= 0 || matchDate < startsAt) continue;
            // date 是比赛结束时间（秒），result.time 是比赛时长（毫秒）。
            long startMs = (matchDate - startsAt) * 1000L - durationMs;
            markers.put(Math.max(0L, startMs - 10_000L));
        }
        Intent intent = new Intent(this, RankedVodActivity.class);
        intent.putExtra(RankedVodActivity.EXTRA_VOD_URL, vodUrl);
        intent.putExtra(RankedVodActivity.EXTRA_MARKERS, markers.toString());
        startActivity(intent);
    }

    private void loadAvatar(String playerId, int version) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://mc-heads.net/avatar/" + playerId + "/96").openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) runOnUiThread(() -> {
                        if (version == requestVersion && !isFinishing() && !isDestroyed()) {
                            avatar.setImageBitmap(bitmap);
                            avatar.setVisibility(View.VISIBLE);
                        }
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "头像加载失败", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String readText(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
                return result.toString();
            }
        } finally {
            connection.disconnect();
        }
    }

    private TextView text(String value, int color, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
