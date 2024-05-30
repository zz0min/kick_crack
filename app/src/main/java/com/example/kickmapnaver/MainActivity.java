package com.example.kickmapnaver;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.util.FusedLocationSource;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private NaverMap map;
    @Nullable
    private LocationManager locationManager;
    private FusedLocationSource locationSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 레이아웃 파일 설정

        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment) fm.findFragmentById(R.id.map_fragment);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map_fragment, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationSource = new FusedLocationSource(this, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasPermission() && locationManager != null) {
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 1000, 10f, this);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
            locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (hasPermission()) {
            if (locationManager != null) {
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 1000, 10f, this);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (map == null || location == null) {
            return;
        }

        LatLng coord = new LatLng(location.getLatitude(), location.getLongitude());

        LocationOverlay locationOverlay = map.getLocationOverlay();
        locationOverlay.setVisible(true);
        locationOverlay.setPosition(coord);
        locationOverlay.setBearing(location.getBearing());

        map.moveCamera(CameraUpdate.scrollTo(coord));

        // Add a marker at the current location
        Marker marker = new Marker();
        marker.setPosition(coord);
        marker.setMap(map);
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

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.map = naverMap;
        this.map.setLocationSource(locationSource);

        // Set initial camera position
        LatLng initialPosition = new LatLng(37.5670135, 126.9783740);
        CameraUpdate cameraUpdate = CameraUpdate.scrollTo(initialPosition);
        naverMap.moveCamera(cameraUpdate);

        // Add a marker at the initial position
        Marker initialMarker = new Marker();
        initialMarker.setPosition(initialPosition);
        initialMarker.setMap(naverMap);

        // UI settings for location button
        naverMap.getUiSettings().setLocationButtonEnabled(true);

        if (hasPermission()) {
            if (locationManager != null) {
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 1000, 10f, this);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(this, PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, PERMISSIONS[1]) == PackageManager.PERMISSION_GRANTED;
    }
}
