package com.networkcourse;

import java.io.File;
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
    private static int OPEN_TRIP_MAP_RADIUS;
    private static int OPEN_TRIP_MAP_LIMIT;
    private static final String OPEN_TRIP_MAP_FORMAT = "json";
    private static final String OPENWEATHER_UNITS = "metric";
    private static final int OPENWEATHER_CITIES_CNT = 1;
    private static final String PROPERTIES_FILE_NAME = "src" + File.separator + "main" + File.separator
            + "properties" + File.separator + "places.properties";
    private static int GEOCODING_LIMIT_PLACES;
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
            LOGGER.error("IOException reading places.properties file!");
        }
        GEOCODING_API_KEY = properties.getProperty("GEOCODE_KEY");
        LOGGER.info("Got geocoding api key: \"{}\"", GEOCODING_API_KEY);
        OPENWEATHER_API_KEY = properties.getProperty("OPENWEATHER_KEY");
        LOGGER.info("Got open weather api key: \"{}\"", OPENWEATHER_API_KEY);
        OPEN_TRIP_MAP_KEY = properties.getProperty("OPEN_TRIP_MAP_KEY");
        LOGGER.info("Got OpenTripMap api key: \"{}\"", OPEN_TRIP_MAP_KEY);
        GEOCODING_LIMIT_PLACES = Integer.parseInt(properties.getProperty("GEOCODING_LIMIT_PLACES"));
        LOGGER.info("Got max number of options in combo box: \"{}\"", GEOCODING_LIMIT_PLACES);
        OPEN_TRIP_MAP_LIMIT = Integer.parseInt(properties.getProperty("OPEN_TRIP_MAP_LIMIT"));
        LOGGER.info("Got max number of interesting places: \"{}\"", OPEN_TRIP_MAP_LIMIT);
        OPEN_TRIP_MAP_RADIUS = Integer.parseInt(properties.getProperty("OPEN_TRIP_MAP_RADIUS"));
        LOGGER.info("Got search radius of interesting places in meters: \"{}\"", OPEN_TRIP_MAP_RADIUS);

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
        LOGGER.info("Generated url: \"{}\" for place: \"{}\"", url, place);
        HttpRequest request = createRequest(url);
        LOGGER.info("Send request for url: \"{}\". Waiting for reply", url);
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::checkHeader)
                .thenApply(this::parseResponsePlace);
    }

    private List<GeocodeResponse.GeocodePoint> parseResponsePlace(String response) {
        GeocodeResponse parsedResponse;
        try {
            parsedResponse = objectMapper.readValue(response, GeocodeResponse.class);
            LOGGER.info("Found {} places", parsedResponse.hits.size());
            return parsedResponse.hits;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing place JSON!");
        }
        return null;
    }

    public CompletableFuture<Weather> getWeather(GeocodeResponse.GeocodePoint place) {
        String url = String.format(Locale.ENGLISH, "%s?lat=%f&lon=%f&units=%s&cnt=%d&appid=%s",
                OPENWEATHER_API, place.point.lat, place.point.lng, OPENWEATHER_UNITS, OPENWEATHER_CITIES_CNT, OPENWEATHER_API_KEY);
        LOGGER.info("Generated url: \"{}\" to get weather from: \"{}\"", url, place.name);
        HttpRequest request = createRequest(url);
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::checkHeader)
                .thenApply(this::parseResponseWeather);
    }

    private String checkHeader(HttpResponse<String> response) {
        String info = "Response code: " + response.statusCode();
        if (response.statusCode() == HTTP_REQUEST_SUCCESS_CODE) {
            LOGGER.info("{} Success!", info);
            LOGGER.info(response.body());
        } else {
            LOGGER.info("{} Failure!", info);
        }
        return response.body();
    }

    private Weather parseResponseWeather(String response) {
        Weather parsedResponse = null;
        try {
            parsedResponse = objectMapper.readValue(response, Weather.class);

            List<Weather.WeatherDescription> desc = parsedResponse.weather;
            if (desc == null) {
                LOGGER.error("Error getting weather description!");
                return parsedResponse;
            }

            LOGGER.info("Weather {}, {}°C", parsedResponse.weather.get(0).description, parsedResponse.main.temp);
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
            LOGGER.info("Found {} interesting places", places.size());
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
        LOGGER.info("Generated url: \"{}\" to get interesting places around the: \"{}\"", url, place.name);
        HttpRequest request = createRequest(url);
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::checkHeader)
                .thenApply(this::parseResponsePlacesAround);
    }

    public CompletableFuture<Void> addPlaceInfo(InterestingPlaceInfo place) {
        String url = String.format(Locale.ENGLISH, "%s/%s?lang=%s&apikey=%s",
                OPEN_TRIP_MAP_PLACE_INFO_API, place.xid, OPEN_TRIP_MAP_LANG, OPEN_TRIP_MAP_KEY);
        LOGGER.info("Generated url: \"{}\" to get info about place: \"{}\"", url, place);
        HttpRequest request = createRequest(url);
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::checkHeader)
                .thenAccept(response -> parseResponsePlaceInfo(response, place));
    }

    private void parseResponsePlaceInfo(String response, InterestingPlaceInfo place) {
        Platform.runLater(() -> place.triedGetInfo = true);
        LOGGER.info(response);
        try {
            InterestingPlaceInfo info = objectMapper.readValue(response, InterestingPlaceInfo.class);
            LOGGER.info("Got description for interesting place: {} description: {}",
                    info.name, info.wikipedia_extracts.text);
            Platform.runLater(() -> {
                place.wikipedia_extracts = info.wikipedia_extracts;
                LOGGER.info("Added description to {}", info);
            });
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing place info JSON!");
            e.printStackTrace();
        } catch (NullPointerException e) {
            LOGGER.warn("Probably no description for this place");
        }
    }
}