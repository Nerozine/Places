package com.networkcourse;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.networkcourse.models.GeocodeResponse;
import com.networkcourse.models.InterestingPlaceInfo;
import com.networkcourse.models.InterestingPlace;
import com.networkcourse.models.Weather;
import javafx.application.Platform;
import javafx.collections.ObservableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RequestManager {
    private final String GEOCODING_API_KEY;
    private final String OPENWEATHER_API_KEY;
    private final String OPEN_TRIP_MAP_KEY;
    private final ObjectMapper objectMapper;
    private static final String GEOCODING_API = "https://graphhopper.com/api/1/geocode";
    private static final String OPENWEATHER_API = "https://api.openweathermap.org/data/2.5/weather";
    private static final String OPEN_TRIP_MAP_API = "http://api.opentripmap.com/0.1/ru/places/radius";
    private static final String OPEN_TRIP_MAP_PLACE_INFO_API = "http://api.opentripmap.com/0.1/ru/places/xid";
    private static final String OPEN_TRIP_MAP_LANG = "ru";
    private static final int OPEN_TRIP_MAP_RADIUS = 1_000;
    private static final int OPEN_TRIP_MAP_LIMIT = 5;
    private static final String OPEN_TRIP_MAP_FORMAT = "json";
    private static final String OPENWEATHER_UNITS = "metric";
    private static final int OPENWEATHER_CITIES_CNT = 1;
    private static final String PROPERTIES_FILE_NAME = "properties";
    private static final int GEOCODING_LIMIT_PLACES = 3;
    private static final int HTTP_REQUEST_SUCCESS_CODE = 200;
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestManager.class);

    private final HttpClient client;

    public RequestManager() {
        objectMapper = new ObjectMapper();

        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(PROPERTIES_FILE_NAME)) {
            properties.load(input);
        } catch (FileNotFoundException ex) {
            LOGGER.error("Properties file not found!");
        } catch (IOException e) {
            LOGGER.error("IOException reading properties file!");
        }
        GEOCODING_API_KEY = properties.getProperty("GEOCODE_KEY");
        LOGGER.info("Got geocoding api key: \"" + GEOCODING_API_KEY + "\"");
        OPENWEATHER_API_KEY = properties.getProperty("OPENWEATHER_KEY");
        LOGGER.info("Got open weather api key: \"" + OPENWEATHER_API_KEY + "\"");
        OPEN_TRIP_MAP_KEY = properties.getProperty("OPEN_TRIP_MAP_KEY");
        LOGGER.info("Got OpenTripMap api key: \"" + OPEN_TRIP_MAP_KEY + "\"");

        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    private HttpRequest createRequest(String url) {
        return HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create(url))
                .GET()
                .build();
    }

    public CompletableFuture<List<GeocodeResponse.GeocodePoint>> getPlaces(String place) {
        String url = String.format(Locale.ENGLISH, "%s?q=%s&limit=%d&key=%s",
                GEOCODING_API,
                place.replaceAll(" ", "%20"),
                GEOCODING_LIMIT_PLACES,
                GEOCODING_API_KEY);
        LOGGER.info("Generated url: \"" + url + "\" for place: \"" + place + "\"");
        HttpRequest request = createRequest(url);
        LOGGER.info("Send request for url: \"" + url + "\". Waiting for reply");
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::checkHeader)
                .thenApply(this::parseResponsePlace);
    }

    private List<GeocodeResponse.GeocodePoint> parseResponsePlace(String response) {
        GeocodeResponse parsedResponse;
        try {
            parsedResponse = objectMapper.readValue(response, GeocodeResponse.class);
            LOGGER.info("Found " + parsedResponse.hits.size() + " places");
            return parsedResponse.hits;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing place JSON!");
        }
        return null;
    }

    public CompletableFuture<Weather> getWeather(GeocodeResponse.GeocodePoint place) {
        String url = String.format(Locale.ENGLISH, "%s?lat=%f&lon=%f&units=%s&cnt=%d&appid=%s",
                OPENWEATHER_API, place.point.lat, place.point.lng, OPENWEATHER_UNITS, OPENWEATHER_CITIES_CNT, OPENWEATHER_API_KEY);
        LOGGER.info("Generated url: \"" + url + "\" to get weather from: \"" + place.name + "\"");
        HttpRequest request = createRequest(url);
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::checkHeader)
                .thenApply(this::parseResponseWeather);
    }

    private String checkHeader(HttpResponse<String> response) {
        String info = "Response code: " + response.statusCode();
        if (response.statusCode() == HTTP_REQUEST_SUCCESS_CODE) {
            LOGGER.info(info + " Success!");
            LOGGER.info(response.body());
        } else {
            LOGGER.info(info + " Failure!");
        }
        return response.body();
    }

    private Weather parseResponseWeather(String response) {
        Weather parsedResponse = null;
        try {
            parsedResponse = objectMapper.readValue(response, Weather.class);
            LOGGER.info("Weather " + parsedResponse.weather.get(0).description +
                    ", " + parsedResponse.main.temp);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing weather JSON!");
        }
        return parsedResponse;
    }

    private List<InterestingPlace> parseResponsePlacesAround(String response) {
        List<InterestingPlace> places = new LinkedList<>();
        try {
            InterestingPlace[] parsedResponse = objectMapper.readValue(response, InterestingPlace[].class);
            LOGGER.info(parsedResponse.toString());
            Arrays.stream(parsedResponse).filter(e -> !e.name.isEmpty()).forEach(places::add);
            LOGGER.info("Found " + places.size() + " interesting places");
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing interesting places JSON!");
            e.printStackTrace();
        }
        return places;
    }

    public CompletableFuture<List<InterestingPlace>> getInterestingPlacesAround
            (GeocodeResponse.GeocodePoint place) {
        String url = String.format(Locale.ENGLISH, "%s?lang=%s&radius=%d&lon=%f&lat=%f&format=%s&limit=%d&apikey=%s",
                OPEN_TRIP_MAP_API, OPEN_TRIP_MAP_LANG, OPEN_TRIP_MAP_RADIUS, place.point.lng, place.point.lat,
                OPEN_TRIP_MAP_FORMAT, OPEN_TRIP_MAP_LIMIT, OPEN_TRIP_MAP_KEY);
        LOGGER.info("Generated url: \"" + url + "\" to get interesting places around the: \"" + place.name + "\"");
        HttpRequest request = createRequest(url);
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::checkHeader)
                .thenApply(this::parseResponsePlacesAround);
    }

    public void addPlacesInfo(ObservableMap<String, String> interestingPlaces) {
        LOGGER.info("map size: " + interestingPlaces.size());
        for (var i : interestingPlaces.entrySet()) {
            String url = String.format(Locale.ENGLISH, "%s/%s?lang=%s&apikey=%s",
                    OPEN_TRIP_MAP_PLACE_INFO_API, i.getKey(), OPEN_TRIP_MAP_LANG, OPEN_TRIP_MAP_KEY);
            LOGGER.info("Generated url: \"" + url + "\" to get info about place: \"" + i.getValue() + "\"");
            HttpRequest request = createRequest(url);
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(this::checkHeader)
                    .thenApply(this::parseResponsePlaceInfo)
                    .thenAccept(info -> Platform.runLater(() -> {
                        interestingPlaces.put(info.xid, "Name: " + info.name + "\nDescription: "
                                + info.wikipedia_extracts.text);
                        LOGGER.info("Added description to " + info.name);
                    }));
        }
    }

    private InterestingPlaceInfo parseResponsePlaceInfo(String response) {
        InterestingPlaceInfo info = null;
        LOGGER.info(response);
        try {
            info = objectMapper.readValue(response, InterestingPlaceInfo.class);
            LOGGER.info("Got description for interesting place: " + info.name + " description: " +
                    info.wikipedia_extracts.text);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing place info JSON!");
        }
        return info;
    }
}