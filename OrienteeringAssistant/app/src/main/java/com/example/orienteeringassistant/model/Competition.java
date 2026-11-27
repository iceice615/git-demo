package com.example.orienteeringassistant.model;

/**
 * 赛事模型
 */
public class Competition {

    /** 积分赛：打卡不限顺序、按点位分值累计、倒计时制 */
    public static final String MODE_SCORE = "积分赛";
    /** 团队赛：必达点按序 + 自由点全队共享 + 最后完赛队员计时 */
    public static final String MODE_TEAM = "团队赛";

    private long id;
    private String name;        // 赛事名称
    private String mode;        // 比赛模式：个人赛/团队赛/积分赛/接力赛
    private String date;        // 比赛日期，如 2026-11-20
    private String description; // 赛事说明
    private String status;      // 状态：未开始/进行中/已结束
    private int timeLimit = 60; // 规定时间（分钟）
    private int validMinutes;   // 积分赛有效时间（分钟）：t<有效时间不扣分
    private int penaltyPerMin;  // 积分赛：超过有效时间后每分钟扣分
    private long startTime;     // 预约开始时间戳（0=未设置）
    private long endTime;       // 预约结束时间戳（0=未设置）
    private Long cloudId;       // 云端赛事 id（发布到云端后回填；null=仅本地）
    private String joinKey;     // 6位数字加入码（发布到云端后生成）

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTimeLimit() { return timeLimit; }
    public void setTimeLimit(int timeLimit) { this.timeLimit = timeLimit; }

    public int getValidMinutes() { return validMinutes; }
    public void setValidMinutes(int validMinutes) { this.validMinutes = validMinutes; }

    public int getPenaltyPerMin() { return penaltyPerMin; }
    public void setPenaltyPerMin(int penaltyPerMin) { this.penaltyPerMin = penaltyPerMin; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public Long getCloudId() { return cloudId; }
    public void setCloudId(Long cloudId) { this.cloudId = cloudId; }

    public String getJoinKey() { return joinKey; }
    public void setJoinKey(String joinKey) { this.joinKey = joinKey; }

    /** 是否为积分赛 */
    public boolean isScoreMode() {
        return MODE_SCORE.equals(mode);
    }

    /** 是否为团队赛 */
    public boolean isTeamMode() {
        return MODE_TEAM.equals(mode);
    }

    /** 是否设置了预约开始时间且还没到 */
    public boolean notStartedYet() {
        return startTime > 0 && System.currentTimeMillis() < startTime;
    }

    /** 是否已过预约结束时间 */
    public boolean alreadyEnded() {
        return endTime > 0 && System.currentTimeMillis() > endTime;
    }

    /** ListView 直接显示 toString() 的结果 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("\n").append(mode).append(" ｜ ").append(date);
        if (isScoreMode()) {
            sb.append(" ｜ 限时 ").append(timeLimit).append(" 分钟");
            if (validMinutes > 0) {
                sb.append(" ｜ 有效 ").append(validMinutes).append(" 分钟");
            }
        }
        if (startTime > 0 || endTime > 0) {
            sb.append("\n⏰ 预约：").append(startTime > 0
                    ? RaceRecord.formatDateTime(startTime) : "不限");
            sb.append(" ~ ").append(endTime > 0
                    ? RaceRecord.formatDateTime(endTime) : "不限");
        }
        if (cloudId != null) {
            sb.append(" ｜ ☁已发布");
            if (joinKey != null && !joinKey.isEmpty()) {
                sb.append(" ｜ 加入码 ").append(joinKey);
            }
        }
        return sb.toString();
    }
}
