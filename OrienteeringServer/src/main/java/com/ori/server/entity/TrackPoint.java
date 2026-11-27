package com.ori.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** GPS 轨迹点表 */
@Entity
@Table(name = "track_points")
public class TrackPoint {
    @Id
    @GeneratedValue
    public Long id;
    public Long raceId;
    public double lat;
    public double lng;
    public Long ts;
}
