package com.example.kickmapnaver;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class SearchResultsActivity extends AppCompatActivity {

    private static final String TAG = "SearchResultsActivity";
    private RecyclerView recyclerView;
    private PlaceAdapter placeAdapter;
    private List<Place> placeList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        placeAdapter = new PlaceAdapter(placeList, new PlaceAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Place place) {
                Toast.makeText(SearchResultsActivity.this, "도착지: " + place.getName(), Toast.LENGTH_SHORT).show();
                // 선택된 장소에 대한 추가 작업 수행
                Intent resultIntent = new Intent();
                resultIntent.putExtra("selectedPlace", place.getAddress()); // 주소를 반환
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
        recyclerView.setAdapter(placeAdapter);

        // 검색 결과를 받아서 RecyclerView에 표시
        String responseData = getIntent().getStringExtra("responseData");
        if (responseData != null) {
            parseAndDisplayResults(responseData);
        }
    }

    private void parseAndDisplayResults(String responseData) {
        try {
            JSONObject jsonResponse = new JSONObject(responseData);
            JSONArray addresses = jsonResponse.getJSONArray("addresses");
            placeList.clear(); // 이전 결과를 지우고 새로운 결과를 추가
            for (int i = 0; i < addresses.length(); i++) {
                JSONObject address = addresses.getJSONObject(i);
                String name = address.getString("roadAddress");
                String addr = address.getString("jibunAddress");
                placeList.add(new Place(name, addr));
            }
            placeAdapter.notifyDataSetChanged();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "JSON 파싱 실패: " + e.getMessage());
        }
    }
}
