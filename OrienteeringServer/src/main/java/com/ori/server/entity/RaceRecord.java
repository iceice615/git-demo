package com.ori.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 比赛成绩表（历史记录，只增不删） */
@Entity
@Table(name = "race_records")
public class RaceRecord {
    @Id
    @GeneratedValue
    public Long id;
    public Long competitionId;
    public Long userId;
    public String username;
    public String name;      // 姓名快照
    public String teamName;  // 团队赛：队伍名快照
    public String mode;
    public Long startTime;
    public Long endTime;
    public Long elapsed;
    public int score;
    public boolean valid;
    public boolean finished;
    public String message;
    public Long uploadedAt;
}
