package com.networkcourse.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InterestingPlace {
    public String name;
    public String xid;
}
    