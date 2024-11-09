package com.example.kickmapnaver;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothServerSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Path;
import android.graphics.BitmapFactory;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.util.Date;

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

    private MediaPlayer mediaPlayer;
    private TextView alertTextView;
    private Handler alertHandler;

    private double currentLatitude;
    private double currentLongitude;

    private final Handler handler = new Handler();
    private final int INTERVAL = 30000; // 30초 (밀리초 단위)
    private ImageView kickboardImage;

    // 마커 리스트를 유지하는 변수 추가
    private List<Marker> markers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        alertTextView = findViewById(R.id.alertTextView);

        // 소리 파일 초기화
        mediaPlayer = MediaPlayer.create(this, R.raw.alert_sound);
        alertHandler = new Handler();

        // 사운드 및 테두리 깜박임 효과
        mediaPlayer.setOnCompletionListener(mp -> mediaPlayer.seekTo(0)); // 반복 재생 가능하게 설정

        // 예상 소요 시간 TextView 참조
        estimatedTimeTextView = findViewById(R.id.estimatedTimeTextView);

        // 경로 방향 버튼을 ImageButton으로 참조
        ImageButton routeDirectionButton = findViewById(R.id.routeDirectionButton);
        routeDirectionButton.setOnClickListener(v -> moveToRouteDirection());

        // Bluetooth UI 버튼 참조 - ImageButton으로 변경
        ImageButton connectButton = findViewById(R.id.connectbutton);
        ImageButton disconnectButton = findViewById(R.id.disconnectbutton);

        // 버튼 클릭 리스너 추가 (예시)
        connectButton.setOnClickListener(v -> connectBluetooth());
        disconnectButton.setOnClickListener(v -> disconnectBluetooth());

        // 경로 취소 버튼 설정
        ImageButton cancelRouteButton = findViewById(R.id.cancelRouteButton);
        cancelRouteButton.setOnClickListener(v -> cancelRoute());

        // ImageView 초기화
        kickboardImage = findViewById(R.id.kickboardImage);

        // 초기화 이후 필요한 코드 작성
        // 예를 들어, 시작 시 kickboardImage의 초기 회전 설정
        kickboardImage.setRotation(0);
        initBluetoothUI();
        initMapUI();
        startLocationUpdates();

        // 주기적으로 마커를 불러오는 작업 시작
        startLoadingMarkersPeriodically();
    }

    private void cancelRoute() {
        if (path != null) {
            path.setMap(null); // 경로를 지도에서 제거
            isRouteDisplayed = false; // 경로 표시 상태를 초기화
            Log.d(TAG, "경로안내가 취소되었습니다.");

            // 사용자에게 경로가 취소되었음을 알리는 UI 메시지
            runOnUiThread(() -> {
                // 메시지 표시를 위한 TextView 생성
                TextView messageTextView = new TextView(this);
                messageTextView.setText("경로안내가 취소되었습니다.");
                messageTextView.setTextSize(18);
                messageTextView.setTextColor(Color.BLACK); // 검은색 글자
                messageTextView.setBackgroundColor(Color.WHITE); // 하얀 배경
                messageTextView.setPadding(20, 20, 20, 20);
                messageTextView.setGravity(Gravity.CENTER);
                messageTextView.setBackgroundResource(R.drawable.red_border_background); // 빨간색 테두리 적용

                // 레이아웃 파라미터 설정 (화면 중앙에 위치)
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                );
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                messageTextView.setLayoutParams(layoutParams);

                // 메시지를 추가할 루트 레이아웃 찾기
                RelativeLayout rootLayout = findViewById(R.id.root_layout);
                rootLayout.addView(messageTextView);

                // 일정 시간 후 메시지를 제거
                new Handler().postDelayed(() -> rootLayout.removeView(messageTextView), 2000); // 2초 후 제거
            });
        }
    }


    private void connectBluetooth() {
        // Bluetooth 연결을 처리하는 로직을 여기에 추가하세요
        Toast.makeText(this, "Bluetooth 연결 시도 중...", Toast.LENGTH_SHORT).show();
    }

    private void disconnectBluetooth() {
        // Bluetooth 연결 해제를 처리하는 로직을 여기에 추가하세요
        Toast.makeText(this, "Bluetooth 연결 해제 중...", Toast.LENGTH_SHORT).show();
    }



    // 경로 방향으로 카메라를 이동하는 메서드
    private void moveToRouteDirection() {
        if (naverMap != null && path.getCoords() != null && !path.getCoords().isEmpty() && currentLocation != null) {
            // 경로의 첫 번째 지점을 가져옵니다.
            LatLng firstPoint = path.getCoords().get(0);

            // 현재 위치와 첫 번째 지점 사이의 방위 각도를 계산합니다.
            double angle = calculateBearing(currentLocation, firstPoint);

            // 카메라 위치, 기울기, 회전 설정
            CameraPosition cameraPosition = new CameraPosition(
                    currentLocation, // 현재 위치를 중심으로 설정
                    16,              // 줌 레벨
                    30,              // 기울기 (tilt)
                    (float) angle    // 회전 각도 (bearing)
            );

            // NaverMap에 카메라 업데이트
            naverMap.setCameraPosition(cameraPosition);

            Toast.makeText(this, "경로 방향으로 카메라 이동", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "경로 정보가 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }


    // 두 지점 사이의 방위 각도를 계산하는 메서드
    private double calculateBearing(LatLng start, LatLng end) {
        double startLat = Math.toRadians(start.latitude);
        double startLng = Math.toRadians(start.longitude);
        double endLat = Math.toRadians(end.latitude);
        double endLng = Math.toRadians(end.longitude);

        double dLng = endLng - startLng;
        double y = Math.sin(dLng) * Math.cos(endLat);
        double x = Math.cos(startLat) * Math.sin(endLat) -
                Math.sin(startLat) * Math.cos(endLat) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private void displaySearchResults(String responseData) {
        List<SearchResultItem> searchResults = new ArrayList<>(); // MainActivity.SearchResultItem 대신 SearchResultItem 사용

        try {
            JSONObject jsonResponse = new JSONObject(responseData);
            JSONArray items = jsonResponse.getJSONArray("items");

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String title = item.getString("title").replaceAll("<.*?>", ""); // HTML 태그 제거
                String address = item.getString("address");
                String roadAddress = item.getString("roadAddress");

                searchResults.add(new SearchResultItem(title, address, roadAddress));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "JSON 파싱 오류: " + e.getMessage());
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_view); // ID 확인
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        SearchResultAdapter adapter = new SearchResultAdapter(searchResults);
        recyclerView.setAdapter(adapter);
    }

    private void initBluetoothUI() {
        speedTextView = findViewById(R.id.speedTextView);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        estimatedTimeTextView = findViewById(R.id.estimatedTimeTextView);

        ImageButton connectButton = findViewById(R.id.connectbutton);
        connectButton.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                startServerSocket();
                connectToDevice(DEVICE_ADDRESS);
            }
        });

        ImageButton disconnectButton = findViewById(R.id.disconnectbutton);
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

        // Enter 키 이벤트를 추가하여 검색 실행
        EditText searchText = findViewById(R.id.search_text);
        searchText.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                performSearch(); // Enter 키를 눌렀을 때 검색 실행
                return true;
            }
            return false;
        });
    }

    // 검색 로직을 공통으로 사용하는 메서드로 분리
    private void performSearch() {
        EditText searchText = findViewById(R.id.search_text);
        String query = searchText.getText().toString().trim();
        if (!query.isEmpty()) {
            searchPlace(query);
        } else {
            Log.e(TAG, "검색어가 비어 있습니다.");
            searchText.setError("검색어를 입력해주세요.");
        }
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

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void searchPlace(String query) {
        OkHttpClient client = new OkHttpClient();
        String encodedQuery = null;

        try {
            encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }

        String url = "https://openapi.naver.com/v1/search/local.json?query=" + encodedQuery + "&display=5&start=1&sort=random";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-Naver-Client-Id", "gEkMN9J1RL5OL_WuTwP7")
                .addHeader("X-Naver-Client-Secret", "735JQY6Q6F")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                System.out.println("로컬 검색 요청 실패: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    System.out.println("로컬 검색 응답 실패: " + response.code() + " " + response.message());
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
        requestLocationPermissions(); // 위치 권한 요청
        startLocationUpdates(); // 위치 업데이트 시작 (권한이 있을 경우)

        // UI 설정
        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setCompassEnabled(true); // 나침반 표시 활성화
        uiSettings.setScaleBarEnabled(true); // 축척 막대 활성화
        uiSettings.setZoomControlEnabled(true); // 줌 컨트롤 활성화
        uiSettings.setLocationButtonEnabled(true); // 현재 위치 버튼 활성화

        // 위치 소스와 위치 추적 모드 설정
        if (locationSource != null) {
            naverMap.setLocationSource(locationSource); // 위치 소스 설정
            naverMap.setLocationTrackingMode(LocationTrackingMode.Follow); // 사용자 위치를 따라 이동
        } else {
            Log.e(TAG, "locationSource가 null입니다. 올바르게 초기화되었는지 확인하세요.");
        }

        // 위치 오버레이 설정
        LocationOverlay locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true); // 위치 오버레이 표시
        locationOverlay.setZIndex(1); // Z-Index 설정 (다른 레이어보다 위에 표시)

        // Firebase에서 데이터를 불러와 마커를 표시 (오래된 데이터 제거 후)
        removeOldDataAndLoadMarkers();

        // 초기 카메라 위치 설정
        LatLng initialPosition = new LatLng(34.81233, 126.43940);
        naverMap.moveCamera(CameraUpdate.scrollTo(initialPosition));

        // 카메라 이동 상태 체크
        naverMap.addOnCameraIdleListener(() -> {
            if (!isCameraUpdating) { // 카메라가 수동으로 움직이고 있을 때
                if (!isRouteDisplayed) { // 경로가 표시되지 않은 경우에만
                    CameraPosition cameraPosition = naverMap.getCameraPosition();
                    startPointCoords = cameraPosition.target.longitude + "," + cameraPosition.target.latitude;
                    Log.d(TAG, "startPointCoords: " + startPointCoords);
                }
            }
        });

        // 지도 클릭 리스너 설정
        naverMap.setOnMapClickListener((point, coord) -> {
            Log.d(TAG, "지도 클릭: " + coord.latitude + ", " + coord.longitude);
            getPlaceInfo(coord.latitude, coord.longitude); // 클릭한 위치에 대한 정보 가져오기
        });
    }

    private void drawWarningMarker(LatLng location, String description) {
        Marker warningMarker = new Marker();
        warningMarker.setPosition(location);

        // ic_warning 이미지를 비트맵으로 불러오고 크기를 조정
        Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_warning);

        // 크기 조정
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 80, 80, false); // 80x80으로 조정

        // 크기 조정된 비트맵을 아이콘으로 설정
        warningMarker.setIcon(OverlayImage.fromBitmap(resizedBitmap));

        warningMarker.setMap(naverMap); // 마커를 지도에 추가
        warningMarker.setCaptionText(description); // 설명 추가
    }

    private final Runnable loadMarkersRunnable = new Runnable() {
        @Override
        public void run() {
            loadWarningMarkersFromFirebase();
            handler.postDelayed(this, INTERVAL); // 30초 후에 다시 실행
        }
    };

    // 주기적 실행 시작 메소드
    private void startLoadingMarkersPeriodically() {
        handler.post(loadMarkersRunnable);
    }

    // 주기적 실행 중단 메소드
    private void stopLoadingMarkersPeriodically() {
        handler.removeCallbacks(loadMarkersRunnable);
    }

    private void loadWarningMarkersFromFirebase() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference locationRef = database.getReference("locations");

        // 기존 마커 제거
        for (Marker marker : markers) {
            marker.setMap(null);
        }
        markers.clear(); // 마커 리스트 초기화

        locationRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    LocationData locationData = snapshot.getValue(LocationData.class);
                    if (locationData != null) {
                        LatLng location = new LatLng(locationData.latitude, locationData.longitude);
                        String description = "Frequency: " + locationData.frequency +
                                ", Intensity: " + locationData.intensity +
                                ", Timestamp: " + locationData.timestamp;

                        // drawWarningMarker 메소드를 사용해 마커 그리기
                        drawWarningMarker(location, description);
                    }
                }
                Log.d(TAG, "Firebase 데이터로 마커 추가 완료");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Firebase 데이터 불러오기 실패: " + databaseError.getMessage());
            }
        });
    }



    private void removeOldDataAndLoadMarkers() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference locationRef = database.getReference("locations");

        // 현재 날짜에서 30일 전 날짜 계산
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -30);
        Date thirtyDaysAgo = calendar.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        locationRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                boolean dataDeleted = false;

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    LocationData locationData = snapshot.getValue(LocationData.class);
                    if (locationData != null && locationData.timestamp != null) {
                        try {
                            Date dataDate = sdf.parse(locationData.timestamp);
                            if (dataDate != null && dataDate.before(thirtyDaysAgo)) {
                                snapshot.getRef().removeValue()
                                        .addOnSuccessListener(aVoid -> Log.d("removeOldData", "오래된 데이터 삭제 성공"))
                                        .addOnFailureListener(e -> Log.e("removeOldData", "오래된 데이터 삭제 실패", e));
                                dataDeleted = true;
                            }
                        } catch (ParseException e) {
                            Log.e("removeOldData", "날짜 파싱 실패: " + e.getMessage());
                        }
                    }
                }
                // 모든 데이터 삭제 작업이 완료된 후 마커를 그리기 위한 메소드 호출
                if (dataDeleted) {
                    locationRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            loadWarningMarkersFromFirebase();
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            Log.e(TAG, "데이터 다시 불러오기 실패: " + databaseError.getMessage());
                        }
                    });
                } else {
                    loadWarningMarkersFromFirebase(); // 삭제할 데이터가 없으면 바로 마커 로드
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("removeOldData", "데이터 불러오기 실패: " + databaseError.getMessage());
            }
        });
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
                    currentLocation = latLng; // 현재 위치를 currentLocation에 업데이트
                    Log.d(TAG, "주기적인 위치 업데이트 - 위도: " + latLng.longitude + ", 경도: " + latLng.latitude);
                    updateCurrentLocationMarker(latLng); // 현재 위치 마커 업데이트

                    // 위치 오버레이 업데이트
                    if (naverMap != null) {
                        LocationOverlay locationOverlay = naverMap.getLocationOverlay();
                        locationOverlay.setPosition(latLng); // 현재 위치로 원 위치 설정
                    }
                }
            }
        };

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000) // 1초 주기
                .setMinUpdateIntervalMillis(1000) // 가장 빠른 업데이트 간격 1초
                .build();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates(); // 위치 업데이트 시작
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap createTriangleMarker() {
        int width = 100;  // 삼각형 너비
        int height = 100; // 삼각형 높이

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED); // 삼각형 색상
        paint.setStyle(Paint.Style.FILL);

        // 삼각형 그리기
        Path path = new Path();
        path.moveTo(width / 2, 0); // 삼각형의 꼭대기
        path.lineTo(0, height);     // 왼쪽 꼭지점
        path.lineTo(width, height); // 오른쪽 꼭지점
        path.close();

        canvas.drawPath(path, paint);
        return bitmap;
    }


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
            new Thread(new AcceptConnectionTask()).start(); // AcceptConnectionTask를 서버 소켓에서 실행
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server socket", e);
        }
    }

    // AcceptConnectionTask에서 bluetoothSocket을 새로 할당하는 방식으로 변경
    private class AcceptConnectionTask implements Runnable {
        @Override
        public void run() {
            BluetoothSocket socket;
            try {
                Log.d(TAG, "Waiting for client to connect...");
                socket = bluetoothServerSocket.accept(); // 연결 수락 시 새 소켓을 생성
                if (socket != null) {
                    bluetoothSocket = socket; // 새로 생성된 소켓을 bluetoothSocket에 할당
                    Log.d(TAG, "Client connected");
                    showConnectionSuccessDialog(); // 연결 성공 시 알림 표시
                    new Thread(new ReceiveMessageTask()).start(); // 새로운 ReceiveMessageTask 스레드 시작
                    bluetoothServerSocket.close(); // 서버 소켓 닫기
                }
            } catch (IOException e) {
                Log.e(TAG, "Error accepting connection", e);
            }
        }
    }


    // connectToDevice 메서드 수정 - 연결 후 새 소켓 할당 방식 동일
    private void connectToDevice(String address) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }

            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID); // BluetoothSocket 새로 생성
            bluetoothSocket.connect();
            Log.d(TAG, "Connected to " + address);

            showConnectionSuccessDialog(); // 연결 성공 시 다이얼로그 표시
            new Thread(new ReceiveMessageTask()).start(); // 수신 스레드 시작

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
                                runOnUiThread(() -> {
                                    showAlert();
                                });

                                if (currentLocation != null) {
                                    double latitude = currentLocation.latitude;
                                    double longitude = currentLocation.longitude;
                                    int newIntensity = 1;

                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                    String timestamp = sdf.format(new Date());

                                    FirebaseDatabase database = FirebaseDatabase.getInstance();
                                    DatabaseReference locationRef = database.getReference("locations");

                                    locationRef.get().addOnSuccessListener(dataSnapshot -> {
                                        boolean nearbyDataFound = false;
                                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                            LocationData existingData = snapshot.getValue(LocationData.class);
                                            if (existingData != null) {
                                                double distance = calculateDistance(latitude, longitude, existingData.latitude, existingData.longitude);
                                                if (distance <= 5) {
                                                    // 기존 데이터가 반경 5m 내에 있음 -> 업데이트
                                                    int updatedFrequency = existingData.frequency + 1;
                                                    double updatedIntensity = (existingData.intensity + newIntensity) / 2;

                                                    snapshot.getRef().child("frequency").setValue(updatedFrequency);
                                                    snapshot.getRef().child("intensity").setValue(updatedIntensity);
                                                    snapshot.getRef().child("timestamp").setValue(timestamp);

                                                    Log.d(TAG, "기존 데이터 갱신 완료");
                                                    nearbyDataFound = true;
                                                    break;
                                                }
                                            }
                                        }

                                        if (!nearbyDataFound) {
                                            // 반경 5m 내에 데이터가 없으면 새 데이터 추가
                                            LocationData newData = new LocationData(latitude, longitude, 1, newIntensity, timestamp);
                                            locationRef.push().setValue(newData)
                                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "새로운 위치 데이터 저장 성공"))
                                                    .addOnFailureListener(e -> Log.e(TAG, "위치 데이터 저장 실패", e));
                                        }
                                    }).addOnFailureListener(e -> Log.e(TAG, "Firebase 데이터 읽기 실패", e));
                                } else {
                                    Log.e(TAG, "현재 위치가 null입니다. 위치 정보를 저장할 수 없습니다.");
                                }
                                break;

                            case '2':
                                runOnUiThread(() -> {
                                    // 기울기 레벨을 파싱
                                    int tiltLevel = Integer.parseInt(content); // `content`가 "1", "2", "3"과 같은 형식일 때

                                    // ImageView 찾기
                                    ImageView kickboardImage = findViewById(R.id.kickboardImage);

                                    // 기울기 레벨에 따라 위쪽으로 회전 각도 설정
                                    float rotationAngle = 0;
                                    if (tiltLevel == 1) {
                                        rotationAngle = 0f;    // 레벨 1: 기울기 없음
                                    } else if (tiltLevel == 2) {
                                        rotationAngle = -5f;  // 레벨 2: 약간 위쪽으로 기울기
                                    } else if (tiltLevel == 3) {
                                        rotationAngle = -15f;  // 레벨 3: 많이 위쪽으로 기울기
                                    }
                                    kickboardImage.setRotation(rotationAngle);
                                });
                                break;

                            case '3':
                                if (currentLocation != null) {
                                    double latitude = currentLocation.latitude;
                                    double longitude = currentLocation.longitude;
                                    int newIntensity = Integer.parseInt(content);

                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                    String timestamp = sdf.format(new Date());

                                    FirebaseDatabase database = FirebaseDatabase.getInstance();
                                    DatabaseReference locationRef = database.getReference("locations");

                                    locationRef.get().addOnSuccessListener(dataSnapshot -> {
                                        boolean nearbyDataFound = false;
                                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                            LocationData existingData = snapshot.getValue(LocationData.class);
                                            if (existingData != null) {
                                                double distance = calculateDistance(latitude, longitude, existingData.latitude, existingData.longitude);
                                                if (distance <= 5) {
                                                    // 기존 데이터가 반경 5m 내에 있음 -> 업데이트
                                                    int updatedFrequency = existingData.frequency + 1;
                                                    double updatedIntensity = (existingData.intensity + newIntensity) / 2;

                                                    snapshot.getRef().child("frequency").setValue(updatedFrequency);
                                                    snapshot.getRef().child("intensity").setValue(updatedIntensity);
                                                    snapshot.getRef().child("timestamp").setValue(timestamp);

                                                    Log.d(TAG, "기존 데이터 갱신 완료");
                                                    nearbyDataFound = true;
                                                    break;
                                                }
                                            }
                                        }

                                        if (!nearbyDataFound) {
                                            // 반경 5m 내에 데이터가 없으면 새 데이터 추가
                                            LocationData newData = new LocationData(latitude, longitude, 1, newIntensity, timestamp);
                                            locationRef.push().setValue(newData)
                                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "새로운 위치 데이터 저장 성공"))
                                                    .addOnFailureListener(e -> Log.e(TAG, "위치 데이터 저장 실패", e));
                                        }
                                    }).addOnFailureListener(e -> Log.e(TAG, "Firebase 데이터 읽기 실패", e));
                                } else {
                                    Log.e(TAG, "현재 위치가 null입니다. 위치 정보를 저장할 수 없습니다.");
                                }
                                break;

                            case '4':
                                runOnUiThread(() -> speedTextView.setText(content +" km/h" ));
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

        // 거리 계산 메소드 (반경 5m 체크)
        private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
            double earthRadius = 6371e3; // 지구 반경 (미터)
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                            Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return earthRadius * c;
        }
    }



    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        // 위치 정보를 변수에 저장
                        currentLatitude = location.getLatitude();
                        currentLongitude = location.getLongitude();

                        // 현재 위치 정보를 사용하여 UI 업데이트 또는 다른 동작 수행
                        Log.d("CurrentLocation", "현재 위치 - 위도: " + currentLatitude + ", 경도: " + currentLongitude);
                        Toast.makeText(MainActivity.this, "현재 위치: 위도 " + currentLatitude + ", 경도 " + currentLongitude, Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("CurrentLocation", "위치를 가져올 수 없습니다.");
                        Toast.makeText(MainActivity.this, "위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CurrentLocation", "위치 가져오기 실패: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "위치 가져오기 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    public static class LocationData {
        public double latitude;
        public double longitude;
        public int frequency;
        public int intensity;
        public String timestamp;

        // 기본 생성자 (Firebase 역직렬화용, 매개변수 없음)
        public LocationData() {
            // 빈 생성자는 Firebase가 객체를 역직렬화할 때 필요합니다.
        }

        // 매개변수가 있는 생성자
        public LocationData(double latitude, double longitude, int frequency, int intensity, String timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.frequency = frequency;
            this.intensity = intensity;
            this.timestamp = timestamp;
        }
    }


    // 알림과 테두리 깜박임 효과
    private void showAlert() {
        // 소리 1초 간격으로 3번 재생
        playAlertSound();

        // 테두리 깜박임 애니메이션
        Animation borderBlinkAnimation = new AlphaAnimation(1, 0);
        borderBlinkAnimation.setDuration(500);
        borderBlinkAnimation.setRepeatMode(Animation.REVERSE);
        borderBlinkAnimation.setRepeatCount(5); // 1초 간격으로 3번 반복

        // 알림 텍스트 깜박임 및 표시
        alertTextView.setVisibility(View.VISIBLE);
        alertTextView.startAnimation(borderBlinkAnimation);

        // 3초 후에 텍스트와 테두리 숨기기
        alertHandler.postDelayed(() -> {
            alertTextView.setVisibility(View.GONE);
        }, 3000);
    }

    // 소리 재생 메서드
    private void playAlertSound() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
            alertHandler.postDelayed(() -> mediaPlayer.start(), 1000);
            alertHandler.postDelayed(() -> mediaPlayer.start(), 2000);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SEARCH_RESULT_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                String roadAddress = data.getStringExtra("roadAddress");
                if (roadAddress != null && !roadAddress.isEmpty()) {
                    geocodeSelectedLocation(roadAddress);
                } else {
                    Log.e(TAG, "선택된 주소가 비어 있습니다.");
                    Toast.makeText(this, "선택된 주소를 찾을 수 없습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
                }
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

    private void geocodeSelectedLocation(String address) {
        String encodedAddress = null;
        try {
            // 주소를 URL에 사용 가능한 형태로 인코딩합니다.
            encodedAddress = java.net.URLEncoder.encode(address, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "주소 인코딩 실패: " + e.getMessage());
            return;
        }

        String url = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=" + encodedAddress;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-NCP-APIGW-API-KEY-ID", "se61eob1kk")
                .addHeader("X-NCP-APIGW-API-KEY", "5VxOL9ERMzqmbgmjCXDUP4iuONVDgbV7WlnAVyUn")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
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
                    JSONArray addresses = jsonResponse.optJSONArray("addresses");

                    if (addresses != null && addresses.length() > 0) {
                        JSONObject addressObj = addresses.getJSONObject(0);
                        String x = addressObj.optString("x", null);
                        String y = addressObj.optString("y", null);

                        if (x != null && y != null) {
                            targetPointCoords = x + "," + y;
                            showRoute(); // 변환된 좌표를 사용하여 경로 표시
                        } else {
                            Log.e(TAG, "Geocoding 결과에 좌표가 없습니다.");
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "유효한 좌표가 없습니다. 다른 위치를 선택해주세요.", Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        Log.e(TAG, "Geocoding 결과가 없습니다.");
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "지오코딩 결과가 없습니다. 다른 위치를 선택해주세요.", Toast.LENGTH_SHORT).show());
                    }
                } catch (JSONException e) {
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

                        // 예상 소요 시간 업데이트
                        double averageSpeedKmh = 50.0; // 예시로 평균 속도 50km/h 사용 (필요에 따라 조정 가능)
                        updateEstimatedTime(averageSpeedKmh);

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
        stopLoadingMarkersPeriodically();
        try {
            if (bluetoothServerSocket != null) {
                bluetoothServerSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket", e);
        }
    }
}