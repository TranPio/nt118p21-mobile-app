package com.example.mobile_app.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.mobile_app.domain.GetDirectionsUseCase;
import com.google.android.gms.maps.model.LatLng;

import java.util.List;

/**
 * ViewModel for map screen.
 */
public class MapViewModel extends ViewModel {
    private final GetDirectionsUseCase useCase;
    private final MutableLiveData<List<LatLng>> routeLiveData = new MutableLiveData<>();

    public MapViewModel(GetDirectionsUseCase useCase) {
        this.useCase = useCase;
    }

    public LiveData<List<LatLng>> getRoute() {
        return routeLiveData;
    }
    
    public void fetchRoute(LatLng origin, LatLng dest) {
        List<LatLng> route = useCase.execute(origin, dest);
        routeLiveData.setValue(route);
    }
}
