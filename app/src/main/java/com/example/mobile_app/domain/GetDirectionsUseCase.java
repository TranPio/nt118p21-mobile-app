package com.example.mobile_app.domain;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

/**
 * Use case to fetch directions from repository.
 */
public class GetDirectionsUseCase {
    private final MapRepository repository;

    public GetDirectionsUseCase(MapRepository repository) {
        this.repository = repository;
    }

    public List<LatLng> execute(LatLng origin, LatLng destination) {
        return repository.getRoute(origin, destination);
    }
}
