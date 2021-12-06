package com.networkcourse.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    public boolean triedGetInfo;

    public InterestingPlaceInfo() {}

    @JsonIgnore
    public InterestingPlaceInfo(InterestingPlace place) {
        this.xid = place.xid;
        this.name = place.name;
        this.wikipedia_extracts = null;
        this.triedGetInfo = false;
    }

    @Override
    @JsonIgnore
    public String toString() {
        String str = "Name: " + name;
        if (wikipedia_extracts == null) {
            return str;
        }
        return str + "\nDescription: " + wikipedia_extracts.text;
    }
}
    