package com.example.orienteeringassistant;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.example.orienteeringassistant.model.RaceRecord;
import com.example.orienteeringassistant.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 运动训练模式：地图实时显示位置与轨迹，统计时长和里程
 * 正式比赛不显示地图（定向越野规则：禁止导航），轨迹由后台服务默默记录
 */
public class TrainingActivity extends AppCompatActivity {

    private static final int REQ_LOCATION = 2001;

    private MapView mapView;
    private BaiduMap map;
    private LocationManager locationManager;
    private boolean recording = false;
    private long startTs = 0;
    private double totalMeters = 0;
    private boolean hasLast = false;
    private double lastLat = 0;
    private double lastLng = 0;

    private final List<LatLng> path = new ArrayList<>();
    private TextView tvTime;
    private TextView tvDistance;
    private Button btnToggle;

    private final Handler handler = new Handler();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            tvTime.setText(RaceRecord.formatElapsed(System.currentTimeMillis() - startTs));
            handler.postDelayed(this, 500);
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            onNewLocation(location);
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_training);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(this, null);
        map = mapView.getMap();
        tvTime = findViewById(R.id.tvTime);
        tvDistance = findViewById(R.id.tvDistance);
        btnToggle = findViewById(R.id.btnToggle);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        btnToggle.setOnClickListener(v -> toggle());
    }

    private void toggle() {
        if (!recording) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                return;
            }
            startTraining();
        } else {
            stopTraining();
        }
    }

    private void startTraining() {
        recording = true;
        startTs = System.currentTimeMillis();
        totalMeters = 0;
        hasLast = false;
        path.clear();
        tvDistance.setText("0.00 km");
        btnToggle.setText("结束训练");
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 2000L, 2f, locationListener);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 5000L, 5f, locationListener);
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "没有定位权限", Toast.LENGTH_SHORT).show();
            recording = false;
            return;
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
        Toast.makeText(this, "训练开始，轨迹实时记录中", Toast.LENGTH_SHORT).show();
    }

    private void stopTraining() {
        recording = false;
        btnToggle.setText("开始训练");
        handler.removeCallbacks(tick);
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        Toast.makeText(this, "本次训练：" + RaceRecord.formatElapsed(System.currentTimeMillis() - startTs)
                + "，" + String.format("%.2f km", totalMeters / 1000.0), Toast.LENGTH_LONG).show();
    }

    /** 收到新定位：画轨迹、算里程 */
    private void onNewLocation(Location location) {
        if (!recording || location == null) {
            return;
        }
        if (location.hasAccuracy() && location.getAccuracy() > 30f) {
            return;
        }
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        if (hasLast) {
            float[] d = new float[1];
            Location.distanceBetween(lastLat, lastLng, lat, lng, d);
            if (d[0] < 3f) {
                return;
            }
            totalMeters += d[0];
        }
        lastLat = lat;
        lastLng = lng;
        hasLast = true;

        // WGS84 → 百度坐标
        double[] bd = GeoUtils.wgs84ToBd09(lat, lng);
        LatLng bdPoint = new LatLng(bd[0], bd[1]);
        path.add(bdPoint);

        // 重画轨迹线
        map.clear();
        map.addOverlay(new PolylineOptions().width(10).color(0xFF2E6DF6).points(path));
        map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(bdPoint, 17));

        tvDistance.setText(String.format("%.2f km", totalMeters / 1000.0));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startTraining();
        } else {
            Toast.makeText(this, "没有定位权限无法记录轨迹", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(tick);
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        mapView.onDestroy();
    }
}
