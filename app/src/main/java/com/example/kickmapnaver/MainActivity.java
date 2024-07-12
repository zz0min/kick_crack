package com.example.kickmapnaver;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private NaverMap naverMap;
    private MapView mapView;
    private String startPointCoords = "";
    private String targetPointCoords = "";
    private PathOverlay path = new PathOverlay();
    private Marker currentLocationMarker = new Marker();
    private FusedLocationProviderClient fusedLocationClient;
    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private LatLng currentLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 검색 버튼 클릭 이벤트
        Button searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(v -> {
            EditText searchText = findViewById(R.id.search_text);
            String query = searchText.getText().toString().trim();
            if (!query.isEmpty()) {
                geocodeLocation(query);
            } else {
                Log.e(TAG, "검색어가 비어 있습니다.");
                searchText.setError("검색어를 입력해주세요.");
            }
        });

        // 현재 위치 버튼 클릭 이벤트
        Button currentLocationButton = findViewById(R.id.current_location_button);
        currentLocationButton.setOnClickListener(v -> {
            if (currentLocation != null) {
                naverMap.moveCamera(CameraUpdate.scrollTo(currentLocation));
            } else {
                Log.e(TAG, "현재 위치를 사용할 수 없습니다.");
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Log.e(TAG, "위치 권한이 거부되었습니다.");
            }
        }
    }

    private void getCurrentLocation() {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                startPointCoords = location.getLongitude() + "," + location.getLatitude();
                                Log.d(TAG, "현재 위치: " + startPointCoords);

                                currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                                currentLocationMarker.setPosition(currentLocation);
                                currentLocationMarker.setMap(naverMap);

                                naverMap.moveCamera(CameraUpdate.scrollTo(currentLocation)); // 현재 위치로 카메라 이동
                            } else {
                                Log.e(TAG, "현재 위치를 가져올 수 없습니다.");
                            }
                        }
                    });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMapReady(@NonNull NaverMap map) {
        naverMap = map;

        // 초기에 마커를 설정하지 않습니다.
        // 마커는 현재 위치를 가져온 후 설정됩니다.
        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setCompassEnabled(true); // 나침반 활성화
        uiSettings.setLocationButtonEnabled(true); // 위치 버튼 활성화
        uiSettings.setScaleBarEnabled(true); // 축척 바 활성화
        naverMap.addOnCameraIdleListener(() -> {
            startPointCoords = naverMap.getCameraPosition().target.longitude + "," + naverMap.getCameraPosition().target.latitude;
            Log.d(TAG, "startPointCoords: " + startPointCoords);
        });
    }

    private void geocodeLocation(String query) {
        String url = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=" + query;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-NCP-APIGW-API-KEY-ID", "5lfe49e4je") // 실제 클라이언트 ID 입력
                .addHeader("X-NCP-APIGW-API-KEY", "Vh1jk6n59v5KSJdBcYDxmfYt57ktwtUlPPFBKjEG")   // 실제 비밀 키 입력
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Geocoding 요청 실패: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Geocoding 응답 실패: " + response.code() + " " + response.message());
                    return;
                }

                String responseData = response.body().string();
                Log.d(TAG, "Geocoding 응답 데이터: " + responseData); // 응답 데이터를 로그에 출력
                try {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    JSONArray addresses = jsonResponse.getJSONArray("addresses");
                    if (addresses.length() > 0) {
                        JSONObject address = addresses.getJSONObject(0);
                        targetPointCoords = address.getString("x") + "," + address.getString("y");
                        Log.d(TAG, "Geocoding 결과 - targetPointCoords: " + targetPointCoords);
                        showRoute();
                    } else {
                        Log.e(TAG, "Geocoding 결과가 없습니다.");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e(TAG, "JSON 파싱 실패: " + e.getMessage());
                }
            }
        });
    }

    private void showRoute() {
        Log.d(TAG, "showRoute - startPointCoords: " + startPointCoords + ", targetPointCoords: " + targetPointCoords);
        directionCallRequest(startPointCoords, targetPointCoords, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.e(TAG, "경로 요청 실패: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "응답 실패: " + response.code() + " " + response.message());
                    Log.e(TAG, "응답 본문: " + response.body().string());
                    return;
                }

                String responseData = response.body().string();
                // 경로 데이터를 파싱하여 PathOverlay에 추가
                runOnUiThread(() -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        JSONArray route = jsonResponse.getJSONObject("route").getJSONArray("trafast");
                        JSONArray pathData = route.getJSONObject(0).getJSONArray("path");

                        List<LatLng> coords = new ArrayList<>();
                        for (int i = 0; i < pathData.length(); i++) {
                            JSONArray coord = pathData.getJSONArray(i);
                            coords.add(new LatLng(coord.getDouble(1), coord.getDouble(0)));
                        }

                        path.setCoords(coords);
                        path.setColor(0xFFFF0000); // 빨간색
                        path.setWidth(10); // 경로 두께 설정
                        path.setMap(naverMap);
                        Log.d(TAG, "경로 설정 완료: " + coords.toString());

                        // 경로가 표시되도록 카메라를 이동
                        if (!coords.isEmpty()) {
                            LatLngBounds bounds = new LatLngBounds.Builder().include(coords).build();
                            naverMap.moveCamera(CameraUpdate.fitBounds(bounds));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG, "JSON 파싱 실패: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void directionCallRequest(String start, String goal, Callback callback) {
        String url = "https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving?start=" + start + "&goal=" + goal + "&option=trafast";
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()


                .url(url)
                .addHeader("X-NCP-APIGW-API-KEY-ID", "5lfe49e4je") // 정확한 클라이언트 ID
                .addHeader("X-NCP-APIGW-API-KEY", "Vh1jk6n59v5KSJdBcYDxmfYt57ktwtUlPPFBKjEG")   // 정확한 Secret 키
                .build();

        Log.d(TAG, "directionCallRequest - URL: " + url);
        client.newCall(request).enqueue(callback);
    }
}
