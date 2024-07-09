package com.example.kickmapnaver;

import java.util.List;

public class DirectionsResponse {
    public Route route;

    public static class Route {
        public List<Traoptimal> traoptimal;

        public static class Traoptimal {
            public List<double[]> path;
        }
    }
}
