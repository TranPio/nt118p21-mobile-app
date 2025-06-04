package com.example.mobile_app.domain;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

/**
 * Repository interface for map related data.
 */
public interface MapRepository {
    /**
     * Request route polyline between origin and destination.
     */
    List<LatLng> getRoute(LatLng origin, LatLng destination);
}
