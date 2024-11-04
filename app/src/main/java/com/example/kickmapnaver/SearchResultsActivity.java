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
    private List<SearchResultItem> placeList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        placeAdapter = new PlaceAdapter(placeList, item -> {
            // 선택된 장소에 대한 주소를 MainActivity로 전달
            Intent resultIntent = new Intent();
            resultIntent.putExtra("roadAddress", item.getRoadAddress());
            setResult(RESULT_OK, resultIntent);
            finish();
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
            JSONArray items = jsonResponse.optJSONArray("items"); // items 배열에서 데이터를 가져옴

            if (items == null) {
                Log.e(TAG, "결과 JSON에 'items' 배열이 없습니다.");
                return;
            }

            placeList.clear();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String title = item.optString("title", "이름 없음").replaceAll("<.*?>", ""); // HTML 태그 제거
                String roadAddress = item.optString("roadAddress", "주소 없음");
                placeList.add(new SearchResultItem(title, "", roadAddress)); // address는 빈 문자열로 설정
            }
            placeAdapter.notifyDataSetChanged();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "JSON 파싱 실패: " + e.getMessage());
        }
    }
}
