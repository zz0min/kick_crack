package com.example.kickmapnaver;

public class SearchResultItem {
    private String title;
    private String address;
    private String roadAddress;
    private double distance;
    private int estimatedTime;

    public SearchResultItem(String title, String address, String roadAddress, double distance, int estimatedTime) {
        this.title = title;
        this.address = address;
        this.roadAddress = roadAddress;
        this.distance = distance;
        this.estimatedTime = estimatedTime;
    }

    public String getTitle() { return title; }

    public String getAddress() { return address; }

    public String getRoadAddress() { return roadAddress; }

    public double getDistance() {return distance; }

    public int getEstimatedTime() { return estimatedTime; }
}
