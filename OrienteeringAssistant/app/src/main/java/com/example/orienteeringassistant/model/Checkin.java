package com.example.orienteeringassistant.model;

/**
 * 一次打卡记录（成绩详情时间轴用）
 */
public class Checkin {

    private long id;
    private long raceId;
    private long checkpointId;
    private String code;
    private int seq;
    private long ts;        // 打卡时间戳
    private boolean valid;
    private String checkpointName; // 联表查询出来的点位名称

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getRaceId() { return raceId; }
    public void setRaceId(long raceId) { this.raceId = raceId; }

    public long getCheckpointId() { return checkpointId; }
    public void setCheckpointId(long checkpointId) { this.checkpointId = checkpointId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public String getCheckpointName() { return checkpointName; }
    public void setCheckpointName(String checkpointName) { this.checkpointName = checkpointName; }
}
