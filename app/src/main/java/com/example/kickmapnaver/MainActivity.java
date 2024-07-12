package com.example.kickmapnaver;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.geometry.LatLng;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // 검색 버튼 클릭 이벤트
        Button searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(v -> {
            EditText searchText = findViewById(R.id.search_text);
            searchLocation(searchText.getText().toString());
        });
    }

    @Override
    public void onMapReady(@NonNull NaverMap map) {
        naverMap = map;
        naverMap.moveCamera(CameraUpdate.scrollTo(new LatLng(37.5665, 126.9780))); // 초기 위치 설정

        Marker marker = new Marker();
        marker.setPosition(naverMap.getCameraPosition().target);
        marker.setMap(naverMap);

        naverMap.addOnCameraChangeListener((reason, animated) -> {
            marker.setPosition(naverMap.getCameraPosition().target);
        });

        naverMap.addOnCameraIdleListener(() -> {
            startPointCoords = naverMap.getCameraPosition().target.longitude + "," + naverMap.getCameraPosition().target.latitude;
        });
    }

    private void searchLocation(String query) {
        // Geocoding API 호출 및 targetPointCoords 설정
        // 여기에서 targetPointCoords 값을 설정하고 showRoute() 메서드를 호출
        targetPointCoords = "126.9784,37.5665"; // 예시 좌표 (서울 시청)
        showRoute();
    }

    private void showRoute() {
        directionCallRequest(startPointCoords, targetPointCoords, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
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
                            path.setMap(naverMap);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        });
    }

    private void directionCallRequest(String start, String goal, Callback callback) {
        String url = "https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving?start=" + start + "&goal=" + goal + "&option=trafast";
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("5lfe49e4je", "클라이언트 Id")
                .addHeader("wcuq8VQDoJIv2LOtzhhuo7pN8lqAlRcxDmoU4kls", "Secret 키")
                .build();

        client.newCall(request).enqueue(callback);
    }
}
