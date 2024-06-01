package com.example.kickmapnaver;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class title extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.title);

        // 5초 후에 MainActivity로 전환
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(title.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, 1000); // 5000 밀리초 = 5초
    }
}