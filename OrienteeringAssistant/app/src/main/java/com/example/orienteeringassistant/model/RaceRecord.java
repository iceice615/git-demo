package com.example.orienteeringassistant.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 一次比赛的成绩记录
 */
public class RaceRecord {

    private long id;
    private long competitionId;
    private long userId;
    private String username;
    private String competitionName; // 联表查询出来的赛事名，不入库
    private String mode;            // 比赛模式（比赛开始时的快照）
    private long startTime;
    private long endTime;
    private long elapsed;   // 用时（毫秒）
    private boolean valid;  // 成绩是否有效
    private boolean finished; // 是否已结束（false = 还在比赛中）
    private boolean uploaded; // 是否已上报云端
    private int score;      // 积分赛得分
    private String message; // 成绩说明（无效原因等）
    private String teamName; // 团队赛：队伍名（比赛开始时的快照）
    private long cloudRaceId; // 云端成绩 id（0=未上报）

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCompetitionId() { return competitionId; }
    public void setCompetitionId(long competitionId) { this.competitionId = competitionId; }

    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getCompetitionName() { return competitionName; }
    public void setCompetitionName(String competitionName) { this.competitionName = competitionName; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public long getElapsed() { return elapsed; }
    public void setElapsed(long elapsed) { this.elapsed = elapsed; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public boolean isUploaded() { return uploaded; }
    public void setUploaded(boolean uploaded) { this.uploaded = uploaded; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public long getCloudRaceId() { return cloudRaceId; }
    public void setCloudRaceId(long cloudRaceId) { this.cloudRaceId = cloudRaceId; }

    public boolean isScoreMode() {
        return Competition.MODE_SCORE.equals(mode);
    }

    /** 毫秒格式化为 mm:ss 或 HH:mm:ss */
    public static String formatElapsed(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    /** 毫秒格式化为 mm:ss.SS（带百分秒，成绩详情用） */
    public static String formatHundredths(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        long cs = (ms % 1000) / 10;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d.%02d", h, m, s, cs);
        }
        return String.format(Locale.getDefault(), "%02d:%02d.%02d", m, s, cs);
    }

    /** 时间戳格式化为 yyyy-MM-dd HH:mm（预约时间显示用） */
    public static String formatDateTime(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(ts));
    }

    @Override
    public String toString() {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(startTime));
        String name = competitionName == null ? "（赛事已删除）" : competitionName;
        String result;
        if (!finished) {
            result = "  ⏱ 比赛进行中";
        } else if (valid) {
            result = "  ✅ 有效";
        } else {
            result = "  ❌ 无效：" + message;
        }
        String scoreText = (finished && isScoreMode()) ? "  积分 " + score + " 分" : "";
        String elapsedText = finished ? "  用时 " + formatElapsed(elapsed) : "";
        String cloudText = (finished && uploaded) ? "  ☁已同步" : "";
        String teamText = (teamName != null && !teamName.isEmpty()) ? "  🚩" + teamName : "";
        return name + teamText + "\n" + time + elapsedText + scoreText + result + cloudText;
    }
}
