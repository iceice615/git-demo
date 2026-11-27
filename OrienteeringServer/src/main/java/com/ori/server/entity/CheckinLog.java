package com.ori.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 打卡记录表（成绩时间轴数据） */
@Entity
@Table(name = "checkins")
public class CheckinLog {
    @Id
    @GeneratedValue
    public Long id;
    public Long raceId;
    public String code;
    public int seq;
    public Long ts;
    public boolean valid;
}
