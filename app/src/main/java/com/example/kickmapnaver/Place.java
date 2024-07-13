package com.example.kickmapnaver;

public class Place {
    private String name;
    private String address;

    public Place(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }
}