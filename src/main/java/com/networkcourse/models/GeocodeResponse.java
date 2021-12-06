package com.networkcourse.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedList;

@JsonIgnoreProperties(ignoreUnknown=true)
public class GeocodeResponse {
    @JsonIgnoreProperties(ignoreUnknown=true)
    public static class GeocodePoint {
        public String name;
        public String city;
        public static class Point {
            public double lat;
            public double lng;
        }
        public Point point;

        @Override
        public String toString() {
            if (city != null) {
                return name + ", " + city;
            }
            return name;
        }
    }
    public LinkedList<GeocodePoint> hits;
}
