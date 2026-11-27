package com.example.orienteeringassistant;

import android.app.Application;

import com.baidu.mapapi.SDKInitializer;

/**
 * 全局 Application：在所有地图组件创建前初始化百度地图 SDK
 */
public class OrienteeringApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 百度 SDK 合规要求：先同意隐私政策再初始化
        SDKInitializer.setAgreePrivacy(this, true);
        SDKInitializer.initialize(getApplicationContext());
    }
}
