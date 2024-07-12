package com.example.kickmapnaver;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface NaverDirectionsService {
    @GET("driving")
    Call<DirectionsResponse> getRoute(
            @Header("5lfe49e4je") String clientId,
            @Header("Vh1jk6n59v5KSJdBcYDxmfYt57ktwtUlPPFBKjEG") String clientSecret,
            @Query("start") String start,
            @Query("goal") String goal
    );
}
