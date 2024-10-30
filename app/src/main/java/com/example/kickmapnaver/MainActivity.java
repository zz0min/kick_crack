package com.example.kickmapnaver;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothServerSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "BluetoothReceiver";
    private static final String DEVICE_ADDRESS = "D8:3A:DD:1E:54:69";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final int SEARCH_RESULT_REQUEST_CODE = 1;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothServerSocket bluetoothServerSocket;
    private LinearLayout additionalInfoLayout;
    private TextView crackDetectionTextView, tiltTextView, impactTextView, speedTextView, estimatedTimeTextView;
    private double totalDistanceInMeters = 0.0;

    private NaverMap naverMap;
    private FusedLocationSource locationSource;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String startPointCoords = "";
    private String targetPointCoords = "";
    private PathOverlay path = new PathOverlay();
    private Marker currentLocationMarker = new Marker();
    private LatLng currentLocation;
    private boolean isCameraUpdating = false;
    private boolean isRouteDisplayed = false;
    private boolean isInfoVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        additionalInfoLayout = findViewById(R.id.additionalInfoLayout);
        ImageButton toggleButton = findViewById(R.id.toggleButton);

        toggleButton.setOnClickListener(v -> {
            if (isInfoVisible) {
                additionalInfoLayout.setVisibility(View.GONE);
                toggleButton.setImageResource(android.R.drawable.ic_menu_more);
            } else {
                additionalInfoLayout.setVisibility(View.VISIBLE);
                toggleButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            }
            isInfoVisible = !isInfoVisible;
        });

        initBluetoothUI();
        initMapUI();
    }

    private void initBluetoothUI() {
        crackDetectionTextView = findViewById(R.id.crackDetectionTextView);
        tiltTextView = findViewById(R.id.tiltTextView);
        impactTextView = findViewById(R.id.impactTextView);
        speedTextView = findViewById(R.id.speedTextView);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        estimatedTimeTextView = findViewById(R.id.estimatedTimeTextView);

        Button connectButton = findViewById(R.id.connectbutton);
        connectButton.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                startServerSocket();
                connectToDevice(DEVICE_ADDRESS);
            }
        });

        Button disconnectButton = findViewById(R.id.disconnectbutton);
        disconnectButton.setOnClickListener(v -> disconnectFromDevice());

        Button searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(v -> {
            EditText searchText = findViewById(R.id.search_text);
            String query = searchText.getText().toString().trim();
            if (!query.isEmpty()) {
                searchPlace(query);
            } else {
                Log.e(TAG, "검색어가 비어 있습니다.");
                searchText.setError("검색어를 입력해주세요.");
            }
        });
    }

    private void initMapUI() {
        MapFragment mapFragment = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getSupportFragmentManager().beginTransaction().add(R.id.map, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);

        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private boolean checkAndRequestPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
            };
        }

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSIONS);
            return false;
        }
        return true;
    }

    private void searchPlace(String query) {
        String url = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=" + query;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-NCP-APIGW-API-KEY-ID", "5lfe49e4je")
                .addHeader("X-NCP-APIGW-API-KEY", "Vh1jk6n59v5KSJdBcYDxmfYt57ktwtUlPPFBKjEG")
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
                Intent intent = new Intent(MainActivity.this, SearchResultsActivity.class);
                intent.putExtra("responseData", responseData);
                startActivityForResult(intent, SEARCH_RESULT_REQUEST_CODE);
            }
        });
    }

    @Override
    public void onMapReady(@NonNull NaverMap map) {
        naverMap = map;

        // UI 설정
        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setCompassEnabled(true);
        uiSettings.setScaleBarEnabled(true);
        uiSettings.setZoomControlEnabled(true);
        uiSettings.setLocationButtonEnabled(true);

        // 위치 소스 설정
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);

        // 위치 오버레이 설정
        LocationOverlay locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true); // 위치 오버레이 표시

        naverMap.addOnCameraIdleListener(() -> {
            if (!isCameraUpdating) {
                if (!isRouteDisplayed) {
                    startPointCoords = naverMap.getCameraPosition().target.longitude + "," + naverMap.getCameraPosition().target.latitude;
                    Log.d(TAG, "startPointCoords: " + startPointCoords);
                }
            }
        });

        naverMap.setOnMapClickListener((point, coord) -> {
            Log.d(TAG, "지도 클릭: " + coord.latitude + ", " + coord.longitude);
            getPlaceInfo(coord.latitude, coord.longitude);
        });

        // 위치 업데이트 호출
        startLocationUpdates(); // 현재 위치 업데이트 시작
    }


    private void startLocationUpdates() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    startPointCoords = latLng.longitude + "," + latLng.latitude;
                    updateCurrentLocationMarker(latLng); // 현재 위치 마커 업데이트

                    // 위치 오버레이 업데이트
                    LocationOverlay locationOverlay = naverMap.getLocationOverlay();
                    locationOverlay.setPosition(latLng); // 현재 위치로 원 위치 설정
                }
            }
        };

        // LocationRequest 및 권한 요청 설정은 그대로 유지
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        }
    }

    // updateCurrentLocationMarker 메서드는 startLocationUpdates 외부에 있어야 합니다.
    private void updateCurrentLocationMarker(LatLng latLng) {
        if (currentLocationMarker != null) {
            currentLocationMarker.setMap(null); // 기존 마커 제거
        }
        currentLocation = latLng;
        currentLocationMarker.setPosition(latLng);
        currentLocationMarker.setMap(naverMap); // 새로운 마커 추가
    }



    private void getPlaceInfo(double latitude, double longitude) {
        String coords = longitude + "," + latitude;
        String url = "https://naveropenapi.apigw.ntruss.com/map-reversegeocode/v2/gc?coords=" + coords + "&orders=addr,roadaddr&output=json";
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-NCP-APIGW-API-KEY-ID", "5lfe49e4je")
                .addHeader("X-NCP-APIGW-API-KEY", "Vh1jk6n59v5KSJdBcYDxmfYt57ktwtUlPPFBKjEG")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.e(TAG, "역지오코딩 요청 실패: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "역지오코딩 응답 실패: " + response.code() + " " + response.message());
                    return;
                }

                String responseData = response.body().string();
                Log.d(TAG, "역지오코딩 응답 데이터: " + responseData);
                try {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    JSONArray results = jsonResponse.getJSONArray("results");
                    if (results.length() > 0) {
                        JSONObject result = results.getJSONObject(0);
                        String address = result.getJSONObject("region").getJSONObject("area1").getString("name") + " " +
                                result.getJSONObject("region").getJSONObject("area2").getString("name") + " " +
                                result.getJSONObject("region").getJSONObject("area3").getString("name") + " " +
                                result.getJSONObject("region").getJSONObject("area4").getString("name");
                        Log.d(TAG, "주소: " + address);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "주소: " + address, Toast.LENGTH_LONG).show();
                        });
                    } else {
                        Log.e(TAG, "역지오코딩 결과가 없습니다.");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e(TAG, "JSON 파싱 실패: " + e.getMessage());
                }
            }
        });
    }

    private void startServerSocket() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }

            bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("MyApp", MY_UUID);
            new Thread(new AcceptConnectionTask()).start();
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server socket", e);
        }
    }

    private class AcceptConnectionTask implements Runnable {
        @Override
        public void run() {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                byte[] buffer = new byte[1024];
                int bytes;

                while (bluetoothSocket.isConnected()) {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String message = new String(buffer, 0, bytes);
                        String content = message.substring(1);

                        switch (message.charAt(0)) {
                            case '1':
                                runOnUiThread(() -> crackDetectionTextView.setText("균열 탐지: " + content));
                                break;
                            case '2':
                                runOnUiThread(() -> tiltTextView.setText("기울기: " + content));
                                break;
                            case '3':
                                runOnUiThread(() -> impactTextView.setText("충격량: " + content));
                                break;
                            case '4':
                                runOnUiThread(() -> {
                                    speedTextView.setText("속도: " + content + " km/h");
                                    double speed = Double.parseDouble(content);
                                    updateEstimatedTime(speed);
                                });
                                break;
                            default:
                                Log.w(TAG, "Unknown message type received: " + message);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading message", e);
            }
        }
    }

    private void connectToDevice(String address) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }

            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            bluetoothSocket.connect();
            Log.d(TAG, "Connected to " + address);

            showConnectionSuccessDialog();
            new Thread(new ReceiveMessageTask()).start();

        } catch (IOException e) {
            Log.e(TAG, "Connection failed", e);
        }

    }

    private void showConnectionSuccessDialog() {
        runOnUiThread(() -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Bluetooth 연결 성공")
                    .setMessage("장치와 성공적으로 연결되었습니다.")
                    .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }

    private class ReceiveMessageTask implements Runnable {
        @Override
        public void run() {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                byte[] buffer = new byte[1024];
                int bytes;

                while (bluetoothSocket.isConnected()) {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String message = new String(buffer, 0, bytes);
                        String content = message.substring(1);

                        switch (message.charAt(0)) {
                            case '1':
                                runOnUiThread(() -> crackDetectionTextView.setText("균열 탐지: " + content));
                                break;
                            case '2':
                                runOnUiThread(() -> tiltTextView.setText("기울기: " + content));
                                break;
                            case '3':
                                runOnUiThread(() -> impactTextView.setText("충격량: " + content));
                                break;
                            case '4':
                                runOnUiThread(() -> speedTextView.setText("속도: " + content));
                                break;
                            default:
                                Log.w(TAG, "Unknown message type received: " + message);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading message", e);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SEARCH_RESULT_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                String selectedPlace = data.getStringExtra("selectedPlace");
                geocodeSelectedLocation(selectedPlace);
            }
        }
    }

    private void disconnectFromDevice() {
        try {
            if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                bluetoothSocket.close();
                bluetoothSocket = null;
                Toast.makeText(this, "Bluetooth 연결이 해제되었습니다.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Bluetooth connection closed");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing Bluetooth socket", e);
        }
    }

    private void geocodeLocation(String query) {
        String url = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=" + query;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-NCP-APIGW-API-KEY-ID", "5lfe49e4je")
                .addHeader("X-NCP-APIGW-API-KEY", "Vh1jk6n59v5KSJdBcYDxmfYt57ktwtUlPPFBKjEG")
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
                Log.d(TAG, "Geocoding 응답 데이터: " + responseData);
                Intent intent = new Intent(MainActivity.this, SearchResultsActivity.class);
                intent.putExtra("responseData", responseData);
                startActivityForResult(intent, SEARCH_RESULT_REQUEST_CODE);
            }
        });
    }

    private void geocodeSelectedLocation(String address) {
        String url = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=" + address;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-NCP-APIGW-API-KEY-ID", "5lfe49e4je")
                .addHeader("X-NCP-APIGW-API-KEY", "Vh1jk6n59v5KSJdBcYDxmfYt57ktwtUlPPFBKjEG")
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
                Log.d(TAG, "Geocoding 응답 데이터: " + responseData);
                try {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    JSONArray addresses = jsonResponse.getJSONArray("addresses");
                    if (addresses.length() > 0) {
                        JSONObject addressObj = addresses.getJSONObject(0);
                        targetPointCoords = addressObj.getString("x") + "," + addressObj.getString("y");
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
                runOnUiThread(() -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        JSONArray route = jsonResponse.getJSONObject("route").getJSONArray("trafast");
                        JSONArray pathData = route.getJSONObject(0).getJSONArray("path");

                        List<LatLng> coords = new ArrayList<>();
                        totalDistanceInMeters = 0.0;

                        for (int i = 0; i < pathData.length(); i++) {
                            JSONArray coord = pathData.getJSONArray(i);
                            LatLng latLng = new LatLng(coord.getDouble(1), coord.getDouble(0));
                            coords.add(latLng);

                            if (i > 0) {
                                totalDistanceInMeters += calculateDistance(coords.get(i - 1), latLng);
                            }
                        }

                        path.setCoords(coords);
                        path.setColor(0xFFFF0000);
                        path.setWidth(10);
                        path.setMap(naverMap);
                        Log.d(TAG, "경로 설정 완료: " + coords.toString());

                        if (!coords.isEmpty() && !isRouteDisplayed) {
                            LatLngBounds bounds = new LatLngBounds.Builder().include(coords).build();
                            isCameraUpdating = true;
                            isRouteDisplayed = true;
                            naverMap.moveCamera(CameraUpdate.fitBounds(bounds).finishCallback(() -> isCameraUpdating = false));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG, "JSON 파싱 실패: " + e.getMessage());
                    }
                });
            }
        });
    }

    private double calculateDistance(LatLng start, LatLng end) {
        double earthRadius = 6371000;
        double dLat = Math.toRadians(end.latitude - start.latitude);
        double dLng = Math.toRadians(end.longitude - start.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(start.latitude)) * Math.cos(Math.toRadians(end.latitude)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private void updateEstimatedTime(double speedKmh) {
        if (speedKmh > 0) {
            double timeInHours = (totalDistanceInMeters / 1000) / speedKmh;
            int minutes = (int) (timeInHours * 60);
            runOnUiThread(() -> estimatedTimeTextView.setText("예상 소요 시간: " + minutes + "분"));
        } else {
            runOnUiThread(() -> estimatedTimeTextView.setText("예상 소요 시간: 계산 불가"));
        }
    }

    private void directionCallRequest(String start, String goal, Callback callback) {
        String url = "https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving?start=" + start + "&goal=" + goal + "&option=trafast";
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-NCP-APIGW-API-KEY-ID", "5lfe49e4je")
                .addHeader("X-NCP-APIGW-API-KEY", "Vh1jk6n59v5KSJdBcYDxmfYt57ktwtUlPPFBKjEG")
                .build();

        Log.d(TAG, "directionCallRequest - URL: " + url);
        client.newCall(request).enqueue(callback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectFromDevice();
        try {
            if (bluetoothServerSocket != null) {
                bluetoothServerSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket", e);
        }
    }
}
