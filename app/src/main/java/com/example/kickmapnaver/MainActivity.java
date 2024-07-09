package com.example.kickmapnaver;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.example.kickmapnaver.DirectionsResponse;
import com.example.kickmapnaver.GeocodingResponse;
import com.example.kickmapnaver.NaverDirectionsService;
import com.example.kickmapnaver.NaverGeocodingService;
import com.example.kickmapnaver.R;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private NaverMap map;
    private LocationManager locationManager;
    private FusedLocationSource locationSource;
    private Marker marker;
    private LatLng currentLocation;
    private LatLng destination;

    private EditText destinationInput;
    private Button findRouteButton;

    // 핸들러 추가
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        destinationInput = findViewById(R.id.destination_input);
        findRouteButton = findViewById(R.id.find_route_button);

        findRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String destinationText = destinationInput.getText().toString();
                if (!TextUtils.isEmpty(destinationText)) {
                    findDestinationLocation(destinationText);
                } else {
                    Toast.makeText(MainActivity.this, "Please enter a destination", Toast.LENGTH_SHORT).show();
                }
            }
        });

        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment) fm.findFragmentById(R.id.map_fragment);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map_fragment, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationSource = new FusedLocationSource(this, PERMISSION_REQUEST_CODE);

        // 핸들러 초기화
        handler = new Handler(Looper.getMainLooper());
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

        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

        LocationOverlay locationOverlay = map.getLocationOverlay();
        locationOverlay.setVisible(true);
        locationOverlay.setPosition(currentLocation);
        locationOverlay.setBearing(location.getBearing());

        map.moveCamera(CameraUpdate.scrollTo(currentLocation));

        if (marker == null) {
            marker = new Marker();
            marker.setPosition(currentLocation);
            marker.setMap(map);
        } else {
            marker.setPosition(currentLocation);
        }
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

        LatLng initialPosition = new LatLng(37.5670135, 126.9783740);
        CameraUpdate cameraUpdate = CameraUpdate.scrollTo(initialPosition);
        naverMap.moveCamera(cameraUpdate);

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

    private void findDestinationLocation(String destinationText) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://naveropenapi.apigw.ntruss.com/map-geocode/v2/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        NaverGeocodingService service = retrofit.create(NaverGeocodingService.class);
        Call<GeocodingResponse> call = service.getCoordinates(
                "5lfe49e4je",
                "wcuq8VQDoJIv2LOtzhhuo7pN8lqAlRcxDmoU4kls",
                destinationText
        );

        call.enqueue(new Callback<GeocodingResponse>() {
            @Override
            public void onResponse(Call<GeocodingResponse> call, Response<GeocodingResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<GeocodingResponse.Address> addresses = response.body().addresses;
                    if (addresses != null && addresses.size() > 0) {
                        GeocodingResponse.Address firstAddress = addresses.get(0);
                        double latitude = Double.parseDouble(firstAddress.y);
                        double longitude = Double.parseDouble(firstAddress.x);
                        destination = new LatLng(latitude, longitude);

                        // 핸들러를 사용하여 UI 업데이트
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                findRoute();
                            }
                        });
                    } else {
                        Toast.makeText(MainActivity.this, "No matching coordinates found", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Failed to geocode destination1", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<GeocodingResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Failed to geocode destination: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                t.printStackTrace();
            }
        });
    }

    private void findRoute() {
        if (currentLocation == null || destination == null) {
            return;
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://naveropenapi.apigw.ntruss.com/map-direction/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        NaverDirectionsService service = retrofit.create(NaverDirectionsService.class);
        Call<DirectionsResponse> call = service.getRoute(
                "5lfe49e4je", // 여기에 네이버 지도 API 키 ID 입력
                "wcuq8VQDoJIv2LOtzhhuo7pN8lqAlRcxDmoU4kls", // 여기에 네이버 지도 API 키 입력
                currentLocation.latitude + "," + currentLocation.longitude,
                destination.latitude + "," + destination.longitude
        );

        call.enqueue(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<LatLng> path = new ArrayList<>();
                    DirectionsResponse.Route route = response.body().route;
                    for (DirectionsResponse.Route.Traoptimal traoptimal : route.traoptimal) {
                        for (double[] step : traoptimal.path) {
                            path.add(new LatLng(step[1], step[0]));
                        }
                    }

                    // 핸들러를 사용하여 UI 업데이트
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            PolylineOverlay polyline = new PolylineOverlay();
                            polyline.setCoords(path);
                            polyline.setWidth(20); // 폴리라인의 두께 설정
                            polyline.setColor(getResources().getColor(R.color.colorPrimary)); // 폴리라인의 색상 설정
                            polyline.setMap(map); // 폴리라인을 지도에 추가
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                t.printStackTrace();
                Toast.makeText(MainActivity.this, "Failed to fetch directions: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
