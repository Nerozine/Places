package com.networkcourse.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InterestingPlaceInfo {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {
        public String text;
    }
    public String xid;
    public String name;
    public Info wikipedia_extracts;
}
    