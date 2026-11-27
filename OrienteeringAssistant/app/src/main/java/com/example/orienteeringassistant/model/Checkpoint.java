package com.example.orienteeringassistant.model;

/**
 * 检查点模型
 * 类型：1=起点 2=检查点 3=终点
 */
public class Checkpoint {

    public static final int TYPE_START = 1;
    public static final int TYPE_NORMAL = 2;
    public static final int TYPE_FINISH = 3;

    private long id;
    private long competitionId;
    private String code;   // 检查点代码（二维码内容的核心，如 A01）
    private String name;   // 检查点名称，如 起点、1号点、终点
    private int seq;       // 打卡顺序号，个人赛按 seq 从小到大依次打卡
    private int points;    // 分值（积分赛用，个人赛默认 10）
    private int type = TYPE_NORMAL; // 点位类型
    private boolean required = true; // 团队赛：true=必达点(每人按顺序) false=自由点(全队共享)
    private Double lat;    // 纬度（历史字段，界面已移除录入）
    private Double lng;    // 经度（历史字段，界面已移除录入）

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCompetitionId() { return competitionId; }
    public void setCompetitionId(long competitionId) { this.competitionId = competitionId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String typeTag() {
        if (type == TYPE_START) return "🚩";
        if (type == TYPE_FINISH) return "🏁";
        return "";
    }

    @Override
    public String toString() {
        String tag = type == TYPE_START ? "[起点] " : (type == TYPE_FINISH ? "[终点] " : "");
        String req = (type == TYPE_NORMAL && !required) ? "[自由点] " : "";
        return seq + "号点  " + tag + req + code + "  " + name + "（" + points + "分）";
    }
}
