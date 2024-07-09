package com.example.kickmapnaver;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GeocodingResponse {
    @SerializedName("addresses")
    public List<Address> addresses;

    public static class Address {
        @SerializedName("x")
        public String x;

        @SerializedName("y")
        public String y;
    }
}