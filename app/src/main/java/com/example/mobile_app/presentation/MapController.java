package com.example.mobile_app.presentation;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Simple wrapper around GoogleMap.
 */
public class MapController {
    private final GoogleMap googleMap;

    public MapController(GoogleMap googleMap) {
        this.googleMap = googleMap;
    }

    public void moveCamera(LatLng position, float zoom) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoom));
    }

    public void addMarker(LatLng position, String title) {
        googleMap.addMarker(new MarkerOptions().position(position).title(title));
    }

    public void setTrafficEnabled(boolean enabled) {
        googleMap.setTrafficEnabled(enabled);
    }

    /** Set the GoogleMap map type, e.g. GoogleMap.MAP_TYPE_NORMAL. */
    public void setMapType(int mapType) {
        googleMap.setMapType(mapType);
    }

    /** Apply a custom map style from a JSON string if provided. */
    public void applyMapStyle(String styleJson) {
        if (styleJson != null) {
            googleMap.setMapStyle(new MapStyleOptions(styleJson));
        } else {
            googleMap.setMapStyle(null);
        }
    }
}
