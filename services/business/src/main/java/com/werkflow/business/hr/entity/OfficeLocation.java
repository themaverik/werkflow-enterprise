package com.werkflow.business.hr.entity;

public enum OfficeLocation {
    SEATTLE_US,
    BANGALORE_IN,
    SHILLONG_IN,
    STOCKHOLM_SE,
    MELBOURNE_AU;

    public String displayName() {
        return switch (this) {
            case SEATTLE_US -> "Seattle, USA";
            case BANGALORE_IN -> "Bangalore, India";
            case SHILLONG_IN -> "Shillong, India";
            case STOCKHOLM_SE -> "Stockholm, Sweden";
            case MELBOURNE_AU -> "Melbourne, Australia";
        };
    }
}
