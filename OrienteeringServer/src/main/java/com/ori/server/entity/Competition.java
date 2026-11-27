package com.ori.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 赛事表 */
@Entity
@Table(name = "competitions")
public class Competition {
    @Id
    @GeneratedValue
    public Long id;
    public String name;
    public String mode;
    public String date;
    public String description;
    public String status;
    public int timeLimit;      // 规定时间（分钟）
    public int validMinutes;   // 积分赛：有效时间（分钟），t<有效时间不扣分
    public int penaltyPerMin;  // 积分赛：有效时间后每分钟扣分 x
    public Long startTime;     // 预约开始时间（毫秒时间戳，0=未设置）
    public Long endTime;       // 预约结束时间（毫秒时间戳，0=未设置）
    public String joinKey;     // 6位数字加入码（运动员自助参赛用）
    public Long ownerId;      // 赛事发起者 user id
    public Long createdAt;
}
