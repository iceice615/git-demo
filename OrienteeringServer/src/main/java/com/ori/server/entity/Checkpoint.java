package com.ori.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 检查点表 */
@Entity
@Table(name = "checkpoints")
public class Checkpoint {
    @Id
    @GeneratedValue
    public Long id;
    public Long competitionId;
    public String code;
    public String name;
    public int seq;
    public int points;
    public int type;       // 1=起点 2=检查点 3=终点
    public boolean required; // 团队赛：true=必达点(按顺序) false=自由点(全队共享)
    public Double lat;
    public Double lng;
}
