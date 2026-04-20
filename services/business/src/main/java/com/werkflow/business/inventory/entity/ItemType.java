package com.werkflow.business.inventory.entity;

public enum ItemType {
    INDIVIDUAL,  // tracked physical item assigned to a person
    BULK         // stock quantity at a location, not individually assigned
}
