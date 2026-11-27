package com.example.orienteeringassistant;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.orienteeringassistant.db.DBHelper;
import com.example.orienteeringassistant.db.SessionManager;
import com.example.orienteeringassistant.model.Checkin;
import com.example.orienteeringassistant.model.Checkpoint;
import com.example.orienteeringassistant.model.Competition;
import com.example.orienteeringassistant.model.RaceRecord;
import com.example.orienteeringassistant.model.TrackPoint;
import com.example.orienteeringassistant.service.TrackService;
import com.example.orienteeringassistant.util.ApiClient;
import com.example.orienteeringassistant.util.NfcUtils;
import com.example.orienteeringassistant.util.QrUtils;
import com.example.orienteeringassistant.widget.SlideFinishView;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 比赛进行中界面（防误触设计）
 *
 * 关键机制：
 * 1. 比赛状态全部落库（raceId / startTs / 已打卡数 / 积分），
 *    扫码页返回、Activity 被系统回收重建、甚至退出后从首页重进，都能断点续赛；
 * 2. 计时基于「开始时间戳」实时计算，永不暂停、永不走偏；
 * 3. 积分赛显示倒计时，时间到自动结束并结算积分；
 * 4. 扫起点二维码自动开始计时，扫终点二维码自动停表完赛；
 * 5. 结束比赛必须是滑动操作，避免运动中误触。
 */
public class RaceActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 1001;
    private static final int REQ_LOCATION = 1002;

    private NfcAdapter nfcAdapter;

    private DBHelper db;
    private SessionManager session;
    private Competition competition;
    private List<Checkpoint> checkpoints;

    // ===== 比赛状态（同时写入数据库，可从数据库恢复）=====
    private long raceId = -1;
    private long startTs = 0;
    private boolean racing = false;
    private int doneCount = 0;      // 个人赛：全部点；团队赛：必达点完成数
    private int score = 0;
    private long limitMs = 0;        // >0 表示倒计时（积分赛）
    private long validMs = 0;        // 积分赛有效时间（不扣分窗口）
    private int penaltyPerMin = 0;   // 积分赛超时每分钟扣分
    private boolean countdownMode = false;
    private boolean teamMode = false;
    private String teamName = "";

    private TextView tvCompInfo;
    private TextView tvTimer;
    private TextView tvProgress;
    private TextView tvHint;
    private Button btnScan;
    private SlideFinishView slideFinish;
    private ArrayAdapter<Checkpoint> adapter;

    /** 计时刷新：每 0.5 秒按时间戳重算，绝不累加误差 */
    private final Handler handler = new Handler();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!racing) {
                return;
            }
            long elapsed = System.currentTimeMillis() - startTs;
            if (countdownMode) {
                long remain = limitMs - elapsed;
                if (remain <= 0) {
                    tvTimer.setText("00:00");
                    finishRace(true);   // 时间到，自动结束
                    return;
                }
                tvTimer.setText(RaceRecord.formatElapsed(remain));
                tvTimer.setTextColor(remain <= 60_000
                        ? Color.parseColor("#D92D20") : Color.parseColor("#2E6DF6"));
            } else {
                tvTimer.setText(RaceRecord.formatElapsed(elapsed));
            }
            handler.postDelayed(this, 500);
        }
    };

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                // 扫码页返回：无论扫没扫、退没退，比赛状态都不受影响
                if (result.getContents() != null) {
                    handleScan(result.getContents());
                }
            });

    /** NFC 读卡回调：读到标签文本后走和扫码完全相同的打卡逻辑 */
    private final NfcAdapter.ReaderCallback nfcCallback = tag -> {
        String text = NfcUtils.readTagText(tag);
        if (text != null) {
            runOnUiThread(() -> handleScan(text));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 运动中长时间看屏幕，保持常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        db = DBHelper.getInstance(this);
        session = new SessionManager(this);

        long competitionId = getIntent().getLongExtra("competition_id", -1);
        competition = db.getCompetition(competitionId);
        checkpoints = db.getCheckpoints(competitionId);
        if (competition == null || checkpoints.isEmpty()) {
            Toast.makeText(this, "赛事数据异常", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        countdownMode = competition.isScoreMode();
        teamMode = competition.isTeamMode();
        limitMs = countdownMode ? competition.getTimeLimit() * 60_000L : 0;
        validMs = countdownMode ? competition.getValidMinutes() * 60_000L : 0;
        penaltyPerMin = competition.getPenaltyPerMin();

        setContentView(R.layout.activity_race);

        tvCompInfo = findViewById(R.id.tvCompInfo);
        tvTimer = findViewById(R.id.tvTimer);
        tvProgress = findViewById(R.id.tvProgress);
        tvHint = findViewById(R.id.tvHint);
        btnScan = findViewById(R.id.btnScan);
        slideFinish = findViewById(R.id.slideFinish);
        ListView lvCheckpoints = findViewById(R.id.lvCheckpoints);

        tvCompInfo.setText(competition.getName() + " ｜ " + competition.getMode()
                + " ｜ 共 " + checkpoints.size() + " 个点");

        adapter = new ArrayAdapter<Checkpoint>(this,
                android.R.layout.simple_list_item_1, checkpoints) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = (TextView) view;
                Checkpoint cp = getItem(position);
                if (cp == null) {
                    return view;
                }
                boolean checked = isChecked(cp);
                String status;
                int color;
                boolean isNext = teamMode
                        ? (cp.isRequired() && cp.getType() == Checkpoint.TYPE_NORMAL
                        && !checked && cp == nextRequired())
                        : (!countdownMode && position == doneCount);
                if (checked) {
                    status = "  ✅ 已打卡";
                    color = Color.parseColor("#1E9E50");
                } else if (isNext) {
                    status = "  👉 下一个";
                    color = Color.parseColor("#2E6DF6");
                } else {
                    status = "  ⬜ 未打卡";
                    color = Color.parseColor("#667085");
                }
                String reqTag = (teamMode && cp.getType() == Checkpoint.TYPE_NORMAL)
                        ? (cp.isRequired() ? "[必达] " : "[自由] ") : "";
                tv.setText(cp.getSeq() + "号点 " + reqTag + cp.typeTag() + " " + cp.getName()
                        + "（" + cp.getPoints() + "分）" + status);
                tv.setTextColor(color);
                tv.setTextSize(15);
                tv.setPadding(28, 24, 28, 24);
                return view;
            }
        };
        lvCheckpoints.setAdapter(adapter);

        btnScan.setOnClickListener(v -> tryScan());
        slideFinish.setOnSlideCompleteListener(this::confirmFinish);

        // ===== 断点续赛：数据库里还有未结束的比赛就恢复 =====
        RaceRecord ongoing = db.getOngoingRace(session.getUserId(), competitionId);
        if (ongoing != null) {
            restoreRace(ongoing);
        } else {
            racing = false;
            slideFinish.setEnabled(false);
            if (countdownMode) {
                tvTimer.setText(RaceRecord.formatElapsed(limitMs));
                String rule = validMs > 0
                        ? " " + competition.getValidMinutes() + "分钟内完赛不扣分，之后每分钟扣 "
                        + penaltyPerMin + " 分。" : "";
                tvHint.setText("限时 " + competition.getTimeLimit() + " 分钟倒计时。"
                        + rule + "扫码任意检查点开始计时，扫终点自动结束。");
            } else if (teamMode) {
                tvTimer.setText("00:00");
                tvHint.setText("团队赛：必达点按顺序打，自由点全队任意一人打卡即算（不可漏点）。"
                        + "扫码起点开始计时，扫终点自动结束。");
            } else {
                tvTimer.setText("00:00");
                tvHint.setText("扫码起点开始计时，按顺序打卡，扫终点自动结束。");
            }
            // 预约时间提示
            if (competition.notStartedYet()) {
                tvHint.append("\n⏰ 本赛事 " + RaceRecord.formatDateTime(competition.getStartTime())
                        + " 才开始，现在还不能开赛！");
            } else if (competition.alreadyEnded()) {
                tvHint.append("\n⏰ 本赛事已于 " + RaceRecord.formatDateTime(competition.getEndTime())
                        + " 结束，不能再开赛。");
            }
        }
        refreshUi();

        // 定位权限提前申请（拒绝也能比赛，只是没有轨迹图）
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        }
    }

    /** 已经打过卡的点（用于列表状态显示） */
    private boolean isChecked(Checkpoint cp) {
        return raceId != -1 && db.hasCheckin(raceId, cp.getId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (racing) {
            handler.removeCallbacks(tick);
            handler.post(tick);
            // 恢复界面时确保轨迹服务还在跑（防止系统杀掉后没重启）
            startTrackService();
        }
        // NFC 读卡模式：页面可见时碰一下标签即可打卡
        if (nfcAdapter != null) {
            nfcAdapter.enableReaderMode(this, nfcCallback,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_NFC_V, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 只是停止界面刷新，比赛时间靠时间戳计算，不会因此中断
        handler.removeCallbacks(tick);
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
    }

    /** 从数据库恢复一场未结束的比赛 */
    private void restoreRace(RaceRecord record) {
        raceId = record.getId();
        startTs = record.getStartTime();
        score = record.getScore();
        doneCount = db.countValidCheckins(raceId);
        racing = true;
        slideFinish.setEnabled(true);
        adapter.notifyDataSetChanged();
        handler.removeCallbacks(tick);
        handler.post(tick);
        startTrackService();
        tvHint.setText("比赛进行中（已恢复）。计时不会因为切屏或扫码中断。");
        Toast.makeText(this, "已恢复进行中的比赛", Toast.LENGTH_SHORT).show();
    }

    private void startRace() {
        // 预约时间管控：未到开始时间 / 已过结束时间都不能开赛
        if (competition.notStartedYet()) {
            toast("⏰ 赛事 " + RaceRecord.formatDateTime(competition.getStartTime())
                    + " 才开始，还不能开赛");
            return;
        }
        if (competition.alreadyEnded()) {
            toast("⏰ 赛事已于 " + RaceRecord.formatDateTime(competition.getEndTime())
                    + " 结束，不能再开赛");
            return;
        }
        startTs = System.currentTimeMillis();
        teamName = loadTeamName();
        raceId = db.insertRaceRecord(competition.getId(), session.getUserId(),
                session.getUsername(), startTs, competition.getMode(), teamName);
        racing = true;
        slideFinish.setEnabled(true);
        handler.removeCallbacks(tick);
        handler.post(tick);
        tvHint.setText("比赛进行中。计时不会因为切屏或扫码中断。"
                + (teamMode && !teamName.isEmpty() ? "\n🚩 你的队伍：" + teamName : ""));
        startTrackService();
        Toast.makeText(this, "⏱ 比赛开始，计时中…", Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    /** 团队赛：读取加入赛事时选择的队伍名 */
    private String loadTeamName() {
        if (competition.getCloudId() == null) {
            return "";
        }
        return getSharedPreferences("team_names", MODE_PRIVATE)
                .getString("comp_" + competition.getCloudId(), "");
    }

    /** 团队赛：下一个待打的必达点（按 seq 顺序） */
    private Checkpoint nextRequired() {
        for (Checkpoint cp : checkpoints) {
            if (cp.getType() == Checkpoint.TYPE_NORMAL && cp.isRequired()
                    && raceId != -1 && !db.hasCheckin(raceId, cp.getId())) {
                return cp;
            }
        }
        return null;
    }

    /** 启动轨迹记录前台服务（有定位权限才启动） */
    private void startTrackService() {
        if (raceId == -1) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent service = new Intent(this, TrackService.class);
        service.putExtra(TrackService.EXTRA_RACE_ID, raceId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
    }

    private void stopTrackService() {
        Intent service = new Intent(this, TrackService.class);
        stopService(service);
    }

    // ==================== 扫码 ====================

    private void tryScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchScan();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchScan();
            } else {
                Toast.makeText(this, "没有相机权限无法扫码打卡", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void launchScan() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("对准检查点二维码（可随时返回，不影响比赛）");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        scanLauncher.launch(options);
    }

    /** 扫码结果判定 */
    private void handleScan(String content) {
        String code = QrUtils.parseCode(content);
        if (code == null) {
            toast("❌ 无效二维码：不是本赛事的检查点");
            return;
        }
        Checkpoint cp = db.getCheckpointByCode(competition.getId(), code);
        if (cp == null) {
            toast("❌ 检查点「" + code + "」不属于本场比赛");
            return;
        }

        // 还没开始：扫到任意有效检查点就自动开始计时（起点）
        if (!racing) {
            startRace();
            if (!racing) {
                return; // 预约时间不满足，开赛被拒绝
            }
        }

        if (countdownMode) {
            handleScoreModeScan(cp);
        } else if (teamMode) {
            handleTeamScan(cp);
        } else {
            handleOrderedScan(cp);
        }
        refreshUi();
    }

    /** 积分赛：不限顺序、按分值累计、扫终点结束 */
    private void handleScoreModeScan(Checkpoint cp) {
        if (db.hasCheckin(raceId, cp.getId())) {
            toast("该检查点已经打过卡了");
            return;
        }
        db.insertCheckin(raceId, cp.getId(), cp.getCode(), cp.getSeq(), true);
        score += cp.getPoints();
        doneCount++;
        db.updateRaceScore(raceId, score);
        toast("✅ " + cp.getName() + "  +" + cp.getPoints() + " 分（当前 " + score + " 分）");
        if (cp.getType() == Checkpoint.TYPE_FINISH) {
            finishRace(false);
        }
    }

    /** 个人赛/接力赛：必须按顺序打卡、扫终点结束 */
    private void handleOrderedScan(Checkpoint cp) {
        if (doneCount >= checkpoints.size()) {
            toast("所有检查点已完成，请滑动结束比赛");
            return;
        }
        if (db.hasCheckin(raceId, cp.getId())) {
            toast("该检查点已经打过卡了");
            return;
        }
        Checkpoint expected = checkpoints.get(doneCount);
        if (cp.getId() != expected.getId()) {
            toast("❌ 顺序错误！当前应打 " + expected.getSeq() + "号点「"
                    + expected.getName() + "」");
            return;
        }
        db.insertCheckin(raceId, cp.getId(), cp.getCode(), cp.getSeq(), true);
        doneCount++;
        toast("✅ " + cp.getName() + " 打卡成功");
        if (cp.getType() == Checkpoint.TYPE_FINISH || doneCount >= checkpoints.size()) {
            finishRace(false);   // 终点打卡 → 自动停表完赛
        }
    }

    /**
     * 团队赛：
     * - 必达点：本人必须按 seq 顺序逐个打（漏点/乱序视为无效打卡）
     * - 自由点：不限顺序，本人打了就记入本人名下（全队任一人打过即算队伍完成）
     * - 终点：扫终点自动停表；队伍成绩 = 最后完赛队员的时间（服务器聚合）
     */
    private void handleTeamScan(Checkpoint cp) {
        if (cp.getType() == Checkpoint.TYPE_FINISH) {
            db.insertCheckin(raceId, cp.getId(), cp.getCode(), cp.getSeq(), true);
            finishRace(false);
            return;
        }
        if (cp.getType() == Checkpoint.TYPE_START) {
            if (!db.hasCheckin(raceId, cp.getId())) {
                db.insertCheckin(raceId, cp.getId(), cp.getCode(), cp.getSeq(), true);
                toast("🚩 起点打卡成功，比赛开始！");
            } else {
                toast("起点已经打过了");
            }
            return;
        }
        if (db.hasCheckin(raceId, cp.getId())) {
            toast("该检查点已经打过卡了");
            return;
        }
        if (cp.isRequired()) {
            Checkpoint expected = nextRequired();
            if (expected != null && cp.getId() != expected.getId()) {
                toast("❌ 必达点顺序错误！当前应打 " + expected.getSeq()
                        + "号点「" + expected.getName() + "」");
                return;
            }
            db.insertCheckin(raceId, cp.getId(), cp.getCode(), cp.getSeq(), true);
            doneCount++;
            toast("✅ 必达点「" + cp.getName() + "」打卡成功");
        } else {
            db.insertCheckin(raceId, cp.getId(), cp.getCode(), cp.getSeq(), true);
            toast("✅ 自由点「" + cp.getName() + "」打卡成功（全队共享）");
        }
    }

    // ==================== 结束比赛 ====================

    private void confirmFinish() {
        if (!racing) {
            slideFinish.reset();
            return;
        }
        int remain = checkpoints.size() - doneCount;
        String msg = countdownMode
                ? "当前积分 " + score + " 分，确认结束比赛？"
                : (remain == 0 ? "确认结束比赛并提交成绩？"
                : "还有 " + remain + " 个检查点未打卡，结束将按无效成绩计算，确定吗？");
        new AlertDialog.Builder(this)
                .setTitle("结束比赛")
                .setMessage(msg)
                .setPositiveButton("确定结束", (d, w) -> finishRace(false))
                .setNegativeButton("继续比赛", (d, w) -> slideFinish.reset())
                .setCancelable(false)
                .show();
    }

    private void finishRace(boolean timeUp) {
        if (!racing) {
            return;
        }
        long end = System.currentTimeMillis();
        long elapsed = end - startTs;
        if (countdownMode && elapsed > limitMs) {
            elapsed = limitMs;
        }

        boolean valid;
        String message;
        int finalScore = score;
        if (countdownMode) {
            // ===== 积分赛扣分规则 =====
            // t < 有效时间：不扣分；有效时间 ≤ t < 规定时间：每分钟扣 x 分；t ≥ 规定时间：0 分
            if (timeUp || elapsed >= limitMs) {
                finalScore = 0;
                message = "超过规定时间（" + competition.getTimeLimit() + "分钟），成绩 0 分";
            } else if (validMs > 0 && elapsed > validMs && penaltyPerMin > 0) {
                long overMs = elapsed - validMs;
                int overMinutes = (int) Math.ceil(overMs / 60000.0);
                finalScore = Math.max(0, score - overMinutes * penaltyPerMin);
                message = "超时 " + overMinutes + " 分钟，扣 " + (overMinutes * penaltyPerMin)
                        + " 分（" + score + " → " + finalScore + "）";
            } else {
                message = validMs > 0
                        ? "有效时间内完赛，不扣分" : "完赛，成绩有效";
            }
            valid = true;
        } else if (teamMode) {
            // ===== 团队赛：本人必达点必须全部按序完成，自由点由全队共享（服务器聚合） =====
            int requiredTotal = 0;
            int requiredDone = 0;
            for (Checkpoint cp : checkpoints) {
                if (cp.getType() == Checkpoint.TYPE_NORMAL && cp.isRequired()) {
                    requiredTotal++;
                    if (db.hasCheckin(raceId, cp.getId())) {
                        requiredDone++;
                    }
                }
            }
            valid = requiredDone >= requiredTotal;
            message = valid
                    ? "必达点全部按序完成（" + requiredDone + "/" + requiredTotal
                    + "），自由点覆盖情况见团队赛况"
                    : "必达点未完成（" + requiredDone + "/" + requiredTotal + "），成绩无效";
        } else {
            valid = doneCount >= checkpoints.size();
            message = valid ? "完赛，成绩有效"
                    : "缺少检查点（" + doneCount + "/" + checkpoints.size() + "），成绩无效";
        }
        score = finalScore;
        db.finishRace(raceId, end, elapsed, valid, message, finalScore);
        // 已发布的赛事：后台自动上报云端（失败不影响本地成绩）
        uploadResult(raceId, valid, message, elapsed, finalScore);

        racing = false;
        handler.removeCallbacks(tick);
        btnScan.setEnabled(false);
        btnScan.setAlpha(0.5f);
        slideFinish.setEnabled(false);
        stopTrackService();
        refreshUi();

        String detail;
        if (countdownMode) {
            detail = "用时 " + RaceRecord.formatElapsed(elapsed) + "\n积分 " + finalScore
                    + " 分\n" + message;
        } else if (teamMode && !teamName.isEmpty()) {
            detail = "用时 " + RaceRecord.formatElapsed(elapsed) + "\n🚩 队伍：" + teamName
                    + "\n" + message + "\n（队伍最终成绩以最后完赛队员为准）";
        } else {
            detail = "用时 " + RaceRecord.formatElapsed(elapsed) + "\n" + message;
        }

        new AlertDialog.Builder(this)
                .setTitle(valid ? "🏁 比赛完成" : "比赛结束")
                .setMessage(detail)
                .setPositiveButton("查看成绩", (d, w) -> {
                    Intent intent = new Intent(this, RaceResultActivity.class);
                    intent.putExtra("race_id", raceId);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("返回", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    // ==================== 界面刷新 ====================

    /**
     * 把完赛成绩（含打卡明细 + GPS 轨迹）上报云端
     * 在子线程执行，失败静默（本地成绩不受影响）
     */
    private void uploadResult(long raceId, boolean valid, String message,
                              long elapsed, int score) {
        if (competition.getCloudId() == null) {
            return; // 纯本地赛事不上传
        }
        final long cloudId = competition.getCloudId();
        final String mode = competition.getMode();
        final long rid = raceId;
        new Thread(() -> {
            try {
                RaceRecord local = db.getRaceRecord(rid);
                if (local == null) {
                    return;
                }
                JSONObject body = new JSONObject();
                body.put("competitionId", cloudId);
                body.put("userId", session.getUserId());
                body.put("username", session.getUsername());
                body.put("name", session.getName());
                body.put("teamName", local.getTeamName() == null ? "" : local.getTeamName());
                body.put("mode", mode);
                body.put("startTime", local.getStartTime());
                body.put("endTime", local.getEndTime());
                body.put("elapsed", elapsed);
                body.put("score", score);
                body.put("valid", valid);
                body.put("message", message);

                JSONArray checkins = new JSONArray();
                for (Checkin ci : db.getCheckinsByRace(rid)) {
                    JSONObject c = new JSONObject();
                    c.put("code", ci.getCode());
                    c.put("seq", ci.getSeq());
                    c.put("ts", ci.getTs());
                    c.put("valid", ci.isValid());
                    checkins.put(c);
                }
                body.put("checkins", checkins);

                JSONArray track = new JSONArray();
                for (TrackPoint p : db.getTrackPoints(rid)) {
                    JSONObject t = new JSONObject();
                    t.put("lat", p.getLat());
                    t.put("lng", p.getLng());
                    t.put("ts", p.getTs());
                    track.put(t);
                }
                body.put("track", track);

                JSONObject resp = ApiClient.post(this, "/api/results", body);
                if (resp.optBoolean("ok")) {
                    db.markRaceUploaded(rid, resp.optLong("id", 0));
                }
            } catch (Exception ignored) {
                // 上传失败：本地成绩已保存，成绩列表标记未同步
            }
        }).start();
    }

    private void refreshUi() {
        adapter.notifyDataSetChanged();
        if (countdownMode) {
            tvProgress.setText("积分 " + score + " 分 ｜ 已打卡 " + doneCount + "/" + checkpoints.size());
        } else {
            tvProgress.setText("打卡进度 " + doneCount + " / " + checkpoints.size());
        }
        btnScan.setText(racing ? "扫码打卡" : "扫码开始比赛");
    }

    /** 运动场景下避免误触退出：返回键需二次确认 */
    @Override
    public void onBackPressed() {
        if (racing) {
            new AlertDialog.Builder(this)
                    .setTitle("离开比赛界面？")
                    .setMessage("离开后比赛仍在计时，可从首页「开始比赛」继续。")
                    .setPositiveButton("离开", (d, w) -> finish())
                    .setNegativeButton("继续比赛", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(tick);
    }
}
