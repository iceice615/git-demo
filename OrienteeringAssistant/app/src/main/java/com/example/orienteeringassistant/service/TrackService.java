package com.example.orienteeringassistant.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.orienteeringassistant.R;
import com.example.orienteeringassistant.db.DBHelper;

/**
 * 比赛轨迹记录前台服务
 *
 * 为什么用前台服务：比赛过程中扫码、切屏、甚至比赛界面被系统回收，
 * 轨迹记录都不能中断 —— 前台服务（通知栏常驻）保证系统不随便杀掉它。
 * 结束比赛时由 RaceActivity 调 stopService() 停止。
 */
public class TrackService extends Service {

    public static final String EXTRA_RACE_ID = "race_id";
    private static final String CHANNEL_ID = "track_channel";
    private static final int NOTIFICATION_ID = 1001;

    private LocationManager locationManager;
    private long raceId = -1;
    private double lastLat = 0;
    private double lastLng = 0;
    private boolean hasLast = false;

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            savePoint(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(EXTRA_RACE_ID)) {
            raceId = intent.getLongExtra(EXTRA_RACE_ID, -1);
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        startListening();
        return START_STICKY;
    }

    private void startListening() {
        if (locationManager == null || raceId == -1) {
            return;
        }
        try {
            // GPS 优先（定向越野是户外运动），网络定位兜底
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 2000L, 2f, listener);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 5000L, 5f, listener);
            }
        } catch (SecurityException e) {
            // 没有定位权限时服务空转，不影响比赛本身
            stopSelf();
        }
    }

    private void savePoint(Location location) {
        if (raceId == -1 || location == null) {
            return;
        }
        // 精度太差的点丢弃（室内/弱信号会漂移；放宽到 50 米避免全程无轨迹）
        if (location.hasAccuracy() && location.getAccuracy() > 50f) {
            return;
        }
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        // 与上一个点距离小于 3 米的点丢弃，减少噪点
        if (hasLast) {
            float[] result = new float[1];
            Location.distanceBetween(lastLat, lastLng, lat, lng, result);
            if (result[0] < 3f) {
                return;
            }
        }
        lastLat = lat;
        lastLng = lng;
        hasLast = true;
        DBHelper.getInstance(this).insertTrackPoint(
                raceId, lat, lng, location.getTime());
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "比赛轨迹记录", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("定向越野助手")
                .setContentText("比赛进行中，正在记录轨迹…")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(listener);
            } catch (SecurityException ignored) {
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
