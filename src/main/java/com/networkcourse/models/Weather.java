package com.networkcourse.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Weather {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeatherDescription {
        public String main;
        public String description;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeatherTemperature {
        public double temp;
    }
    public List<WeatherDescription> weather;
    public WeatherTemperature main;
}