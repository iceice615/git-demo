package com.ori.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 用户表 */
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue
    public Long id;

    public String username;

    /**
     * 口令字段：存放 PBKDF2 哈希（格式 pbkdf2$迭代次数$盐$摘要）。
     * 保留原列名 password 以兼容已有数据，但新数据一律写哈希，禁止再存明文。
     */
    @Column(name = "password", length = 512)
    public String password;

    public String name;     // 姓名
    public String clazz;    // 班级
    public Long createdAt;

    /** 当前有效会话令牌（登录签发，单端登录） */
    @Column(length = 128)
    public String token;

    /** 令牌过期时间（毫秒时间戳） */
    public Long tokenExpires;
}
