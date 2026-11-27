package com.ori.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 参赛成员表（赛事发起者建的“群”） */
@Entity
@Table(name = "enrollments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"competitionId", "userId"}))
public class Enrollment {
    @Id
    @GeneratedValue
    public Long id;
    public Long competitionId;
    public Long userId;
    public String teamName;  // 团队赛：所在队伍名
    public Long joinedAt;
}
