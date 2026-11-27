package com.ori.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 定向越野助手 云端服务
 * 提供用户、赛事、参赛成员、成绩与轨迹的 REST 接口
 */
@SpringBootApplication
public class OriServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OriServerApplication.class, args);
    }
}
