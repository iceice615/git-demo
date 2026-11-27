package com.example.orienteeringassistant.model;

/**
 * 轨迹点（WGS84 坐标，画图时转换为百度坐标）
 */
public class TrackPoint {

    private long id;
    private long raceId;
    private double lat;
    private double lng;
    private long ts;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getRaceId() { return raceId; }
    public void setRaceId(long raceId) { this.raceId = raceId; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }
}
