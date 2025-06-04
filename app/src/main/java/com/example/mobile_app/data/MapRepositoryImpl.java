package com.example.mobile_app.data;

import com.example.mobile_app.domain.MapRepository;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple mock implementation returning a straight line polyline.
 */
public class MapRepositoryImpl implements MapRepository {
    @Override
    public List<LatLng> getRoute(LatLng origin, LatLng destination) {
        List<LatLng> path = new ArrayList<>();
        path.add(origin);
        path.add(destination);
        return path;
    }
}
