package com.example.kickmapnaver;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface NaverDirectionsService {
    @GET("driving")
    Call<DirectionsResponse> getRoute(
            @Header("5lfe49e4je") String clientId,
            @Header("wcuq8VQDoJIv2LOtzhhuo7pN8lqAlRcxDmoU4kls") String clientSecret,
            @Query("start") String start,
            @Query("goal") String goal
    );
}
