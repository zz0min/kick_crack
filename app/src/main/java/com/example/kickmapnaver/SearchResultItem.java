package com.example.kickmapnaver;

public class SearchResultItem {
    private String title;
    private String address;
    private String roadAddress;

    public SearchResultItem(String title, String address, String roadAddress) {
        this.title = title;
        this.address = address;
        this.roadAddress = roadAddress;
    }

    public String getTitle() {
        return title;
    }

    public String getAddress() {
        return address;
    }

    public String getRoadAddress() {
        return roadAddress;
    }
}
