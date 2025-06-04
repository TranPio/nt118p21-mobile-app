package com.example.mobile_app;

import static org.junit.Assert.assertEquals;

import com.example.mobile_app.domain.GetDirectionsUseCase;
import com.example.mobile_app.domain.MapRepository;
import com.google.android.gms.maps.model.LatLng;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class GetDirectionsUseCaseTest {
    @Test
    public void execute_returnsRouteFromRepository() {
        MapRepository repo = (origin, dest) -> Arrays.asList(origin, dest);
        GetDirectionsUseCase useCase = new GetDirectionsUseCase(repo);
        LatLng a = new LatLng(0,0);
        LatLng b = new LatLng(1,1);
        List<LatLng> result = useCase.execute(a, b);
        assertEquals(2, result.size());
        assertEquals(a, result.get(0));
        assertEquals(b, result.get(1));
    }
}
