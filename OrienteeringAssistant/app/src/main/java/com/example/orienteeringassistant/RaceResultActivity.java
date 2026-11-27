package com.example.orienteeringassistant;

import android.location.Location;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.example.orienteeringassistant.db.DBHelper;
import com.example.orienteeringassistant.model.Checkin;
import com.example.orienteeringassistant.model.RaceRecord;
import com.example.orienteeringassistant.model.TrackPoint;
import com.example.orienteeringassistant.util.ApiClient;
import com.example.orienteeringassistant.util.GeoUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 成绩详情页（本地 / 云端两种数据源）
 * - 本地：我的成绩列表点进来
 * - 云端：全员成绩里点别人的成绩 → 查看其打卡时间轴 + 轨迹图
 */
public class RaceResultActivity extends AppCompatActivity {

    private DBHelper db;
    private MapView mapView;
    private boolean mapCreated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_race_result);
        db = DBHelper.getInstance(this);

        long cloudRaceId = getIntent().getLongExtra("cloud_race_id", -1);
        if (cloudRaceId != -1) {
            loadCloudDetail(cloudRaceId);
        } else {
            long raceId = getIntent().getLongExtra("race_id", -1);
            RaceRecord record = db.getRaceRecord(raceId);
            if (record == null) {
                finish();
                return;
            }
            List<TrackPoint> track = db.getTrackPoints(raceId);
            // 本地没存到轨迹但成绩已上报云端 → 从云端拉回轨迹再看
            if (track.size() < 2 && record.getCloudRaceId() > 0) {
                loadCloudTrackAndRender(record, raceId, record.getCloudRaceId());
                return;
            }
            render(record, track, db.getCheckinsByRace(raceId), true);
        }
    }

    /** 本地轨迹缺失时从云端拉取轨迹后渲染（打卡明细仍用本地） */
    private void loadCloudTrackAndRender(RaceRecord record, long localRaceId,
                                         long cloudRaceId) {
        new Thread(() -> {
            List<TrackPoint> track = new ArrayList<>();
            try {
                JSONObject trackResp = ApiClient.get(this,
                        "/api/results/" + cloudRaceId + "/track");
                if (trackResp.optBoolean("ok")) {
                    JSONArray arr = trackResp.optJSONArray("data");
                    for (int i = 0; arr != null && i < arr.length(); i++) {
                        JSONObject t = arr.getJSONObject(i);
                        TrackPoint p = new TrackPoint();
                        p.setLat(t.optDouble("lat"));
                        p.setLng(t.optDouble("lng"));
                        p.setTs(t.optLong("ts"));
                        track.add(p);
                    }
                    // 顺带补回本地，下次不用联网也能看
                    for (TrackPoint p : track) {
                        db.insertTrackPoint(localRaceId, p.getLat(), p.getLng(), p.getTs());
                    }
                }
            } catch (Exception ignored) {
            }
            List<TrackPoint> finalTrack = track;
            List<Checkin> checkins = db.getCheckinsByRace(localRaceId);
            runOnUiThread(() -> render(record, finalTrack, checkins, true));
        }).start();
    }

    /** 云端模式：从服务器拉取成绩、打卡明细和轨迹 */
    private void loadCloudDetail(long cloudRaceId) {
        Toast.makeText(this, "正在从云端加载…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONObject recordJson = new JSONObject();
                recordJson.put("id", cloudRaceId);
                recordJson.put("competitionName",
                        getIntent().getStringExtra("cloud_comp_name"));
                recordJson.put("username",
                        getIntent().getStringExtra("cloud_username"));
                recordJson.put("mode", getIntent().getStringExtra("cloud_mode"));
                recordJson.put("startTime", getIntent().getLongExtra("cloud_start_time", 0));
                recordJson.put("elapsed", getIntent().getLongExtra("cloud_elapsed", 0));
                recordJson.put("score", getIntent().getIntExtra("cloud_score", 0));
                recordJson.put("valid", getIntent().getBooleanExtra("cloud_valid", false));
                recordJson.put("message", getIntent().getStringExtra("cloud_message"));

                List<Checkin> checkins = new ArrayList<>();
                JSONObject checkinsResp = ApiClient.get(this,
                        "/api/results/" + cloudRaceId + "/checkins");
                if (checkinsResp.optBoolean("ok")) {
                    JSONArray arr = checkinsResp.optJSONArray("data");
                    for (int i = 0; arr != null && i < arr.length(); i++) {
                        JSONObject c = arr.getJSONObject(i);
                        Checkin ci = new Checkin();
                        ci.setCode(c.optString("code"));
                        ci.setSeq(c.optInt("seq"));
                        ci.setTs(c.optLong("ts"));
                        ci.setValid(c.optBoolean("valid", true));
                        checkins.add(ci);
                    }
                }

                List<TrackPoint> track = new ArrayList<>();
                JSONObject trackResp = ApiClient.get(this,
                        "/api/results/" + cloudRaceId + "/track");
                if (trackResp.optBoolean("ok")) {
                    JSONArray arr = trackResp.optJSONArray("data");
                    for (int i = 0; arr != null && i < arr.length(); i++) {
                        JSONObject t = arr.getJSONObject(i);
                        TrackPoint p = new TrackPoint();
                        p.setLat(t.optDouble("lat"));
                        p.setLng(t.optDouble("lng"));
                        p.setTs(t.optLong("ts"));
                        track.add(p);
                    }
                }

                RaceRecord record = new RaceRecord();
                record.setId(recordJson.optLong("id"));
                record.setCompetitionName(recordJson.optString("competitionName"));
                record.setUsername(recordJson.optString("username"));
                record.setMode(recordJson.optString("mode"));
                record.setStartTime(recordJson.optLong("startTime"));
                record.setElapsed(recordJson.optLong("elapsed"));
                record.setScore(recordJson.optInt("score"));
                record.setValid(recordJson.optBoolean("valid"));
                record.setMessage(recordJson.optString("message"));
                record.setFinished(true);

                final List<Checkin> finalCheckins = checkins;
                final List<TrackPoint> finalTrack = track;
                runOnUiThread(() -> render(record, finalTrack, finalCheckins, false));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "云端加载失败：" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }).start();
    }

    /** 渲染整个页面 */
    private void render(RaceRecord record, List<TrackPoint> trackPoints,
                        List<Checkin> checkins, boolean localMode) {
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvStatus = findViewById(R.id.tvStatus);
        TextView tvSummary = findViewById(R.id.tvSummary);
        LinearLayout timelineContainer = findViewById(R.id.timelineContainer);
        LinearLayout rankingContainer = findViewById(R.id.rankingContainer);

        String compName = record.getCompetitionName() == null ? "赛事" : record.getCompetitionName();
        String who = record.getUsername() == null || record.getUsername().isEmpty()
                ? "" : " · " + record.getUsername();
        tvTitle.setText(compName + who);

        if (record.isValid()) {
            tvStatus.setText("✅ 成绩有效");
            tvStatus.setTextColor(getColor(R.color.success));
        } else {
            tvStatus.setText("❌ 成绩无效：" + record.getMessage());
            tvStatus.setTextColor(getColor(R.color.error));
        }

        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(record.getStartTime()));
        StringBuilder summary = new StringBuilder();
        summary.append("开始时间  ").append(date).append("\n");
        summary.append("总用时    ").append(RaceRecord.formatHundredths(record.getElapsed()));
        if (record.isScoreMode()) {
            summary.append("\n总积分    ").append(record.getScore()).append(" 分");
        }

        double distanceKm = showTrack(trackPoints);
        if (distanceKm >= 0) {
            summary.append("\n总里程    ").append(String.format(Locale.getDefault(),
                    "%.2f km（%d 个轨迹点）", distanceKm, trackPoints.size()));
        } else {
            summary.append("\n运动轨迹  未记录（可能未授权定位、室内 GPS 无信号，"
                    + "或本次为旧版本比赛）");
        }
        tvSummary.setText(summary.toString());

        buildTimeline(timelineContainer, record, checkins);
        if (localMode) {
            buildRanking(rankingContainer, record.getCompetitionId(), record.getId());
        } else {
            TextView tip = makeText("排名请返回「全员成绩」页面查看", R.color.text_secondary, 13, 12);
            rankingContainer.addView(tip);
        }
    }

    /**
     * 在顶部地图上绘制 GPS 轨迹
     * 返回总里程（km）；没有轨迹返回 -1（地图隐藏）
     */
    private double showTrack(List<TrackPoint> points) {
        mapView = findViewById(R.id.mapTrack);
        if (points == null || points.size() < 2) {
            mapView.setVisibility(View.GONE);
            return -1;
        }
        mapView.setVisibility(View.VISIBLE);
        mapView.onCreate(this, null);
        mapCreated = true;
        BaiduMap map = mapView.getMap();

        // WGS84（GPS 原始坐标）→ 百度坐标 BD09LL
        List<LatLng> path = new ArrayList<>();
        double totalMeters = 0;
        TrackPoint prev = null;
        for (TrackPoint p : points) {
            double[] bd = GeoUtils.wgs84ToBd09(p.getLat(), p.getLng());
            path.add(new LatLng(bd[0], bd[1]));
            if (prev != null) {
                float[] d = new float[1];
                Location.distanceBetween(prev.getLat(), prev.getLng(),
                        p.getLat(), p.getLng(), d);
                totalMeters += d[0];
            }
            prev = p;
        }

        map.addOverlay(new PolylineOptions()
                .width(10)
                .color(0xFF2E6DF6)
                .points(path));
        map.addOverlay(new MarkerOptions().position(path.get(0))
                .icon(makeDot(R.drawable.dot_start)));
        map.addOverlay(new MarkerOptions().position(path.get(path.size() - 1))
                .icon(makeDot(R.drawable.dot_finish)));
        map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(path.get(0), 16));
        return totalMeters / 1000.0;
    }

    /** 生成圆形标记图标（起点绿 / 终点红） */
    private BitmapDescriptor makeDot(int drawableRes) {
        android.widget.TextView dot = new android.widget.TextView(this);
        dot.setBackgroundResource(drawableRes);
        return BitmapDescriptorFactory.fromView(dot);
    }

    /** 打卡时间轴：展示分段用时与累计用时 */
    private void buildTimeline(LinearLayout container, RaceRecord record,
                               List<Checkin> checkins) {
        container.removeAllViews();

        if (checkins == null || checkins.isEmpty()) {
            container.addView(makeText("本次比赛没有有效打卡记录",
                    R.color.text_secondary, 14, 12));
            return;
        }

        container.addView(makeTimelineRow("起点", "开始计时",
                "00:00.00", "00:00.00", R.color.text_secondary, false));

        long prev = record.getStartTime();
        int index = 1;
        for (Checkin ci : checkins) {
            long split = ci.getTs() - prev;
            long total = ci.getTs() - record.getStartTime();
            String name = (ci.getCheckpointName() == null
                    ? ci.getCode() : ci.getCheckpointName());
            boolean last = (index == checkins.size());
            container.addView(makeTimelineRow(String.valueOf(index), name,
                    "+" + RaceRecord.formatHundredths(split),
                    RaceRecord.formatHundredths(total),
                    last ? R.color.error : R.color.primary, last));
            prev = ci.getTs();
            index++;
        }
    }

    /** 时间轴一行：[序号] 点位名    分段   累计 */
    private View makeTimelineRow(String seq, String name, String split, String total,
                                 int colorRes, boolean bold) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView tvSeq = makeText(seq, R.color.text_secondary, 13, 0);
        tvSeq.setWidth(dp(34));
        row.addView(tvSeq);

        TextView tvName = makeText(name, R.color.text_primary, 15, 0);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (bold) {
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        row.addView(tvName);

        TextView tvSplit = makeText(split, colorRes, 13, 0);
        tvSplit.setPadding(0, 0, dp(12), 0);
        row.addView(tvSplit);

        TextView tvTotal = makeText(total, R.color.text_primary, 14, 0);
        tvTotal.setTypeface(android.graphics.Typeface.MONOSPACE);
        row.addView(tvTotal);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row);
        View divider = new View(this);
        divider.setBackgroundColor(0xFFEDF0F3);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return wrapper;
    }

    /** 该赛事的成绩排名 */
    private void buildRanking(LinearLayout container, long competitionId, long selfRaceId) {
        container.removeAllViews();
        List<RaceRecord> ranking = db.getRanking(competitionId);
        if (ranking.isEmpty()) {
            container.addView(makeText("暂无其他完赛成绩", R.color.text_secondary, 14, 12));
            return;
        }
        int rank = 1;
        for (RaceRecord r : ranking) {
            String timeText = r.isScoreMode()
                    ? r.getScore() + " 分"
                    : RaceRecord.formatElapsed(r.getElapsed());
            String statusText = r.isValid() ? "成绩有效" : "成绩无效";
            String line = rank + ".  " + r.getUsername() + "   " + statusText + "   " + timeText;
            boolean self = r.getId() == selfRaceId;
            TextView tv = makeText(line, self ? R.color.primary : R.color.text_primary, 15, 12);
            if (self) {
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            container.addView(tv);
            rank++;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapCreated) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapCreated) {
            mapView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapCreated) {
            mapView.onDestroy();
            mapCreated = false;
        }
    }

    private TextView makeText(String text, int colorRes, int sizeSp, int paddingV) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getColor(colorRes));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (paddingV > 0) {
            tv.setPadding(0, dp(paddingV), 0, dp(paddingV));
        }
        return tv;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
