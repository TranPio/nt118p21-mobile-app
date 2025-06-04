package com.example.mobile_app.presentation;

import static org.mockito.Mockito.*;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import org.junit.Test;

/** Unit tests for MapController. */
public class MapControllerTest {
    @Test
    public void moveCamera_callsGoogleMap() {
        GoogleMap map = mock(GoogleMap.class);
        MapController controller = new MapController(map);
        controller.moveCamera(new LatLng(0,0), 10f);
        verify(map).moveCamera(any());
    }

    @Test
    public void setMapType_appliesToGoogleMap() {
        GoogleMap map = mock(GoogleMap.class);
        MapController controller = new MapController(map);
        controller.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        verify(map).setMapType(GoogleMap.MAP_TYPE_SATELLITE);
    }

    @Test
    public void setTrafficEnabled_updatesGoogleMap() {
        GoogleMap map = mock(GoogleMap.class);
        MapController controller = new MapController(map);
        controller.setTrafficEnabled(true);
        verify(map).setTrafficEnabled(true);
    }
}
