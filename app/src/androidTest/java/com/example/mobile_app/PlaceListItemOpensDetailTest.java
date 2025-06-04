package com.example.mobile_app;

import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.text.SpannableString;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.mobile_app.maps.MapsActivity;
import com.example.mobile_app.maps.PlaceDetailsHelper;
import com.example.mobile_app.maps.PlaceDetailsHelper.PlaceDetailsCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlaceListItemOpensDetailTest {
    @Test
    public void clickItem_opensBottomSheet() throws Exception {
        ActivityScenario<MapsActivity> scenario = ActivityScenario.launch(MapsActivity.class);
        scenario.onActivity(activity -> {
            // Inject fake PlaceDetailsHelper
            PlaceDetailsHelper helper = new PlaceDetailsHelper(activity, null, null) {
                @Override
                public void fetchPlaceDetails(String placeId, PlaceDetailsCallback callback) {
                    Place place = Place.builder()
                            .setName("Test Place")
                            .setLatLng(new LatLng(0,0))
                            .build();
                    callback.onPlaceDetailsFetched(place);
                }
            };
            try {
                java.lang.reflect.Field f = MapsActivity.class.getDeclaredField("placeDetailsHelper");
                f.setAccessible(true);
                f.set(activity, helper);
            } catch (Exception ignored) {}            AutocompletePrediction p = mock(AutocompletePrediction.class);
            when(p.getPlaceId()).thenReturn("1");
            when(p.getPrimaryText(null)).thenReturn(new SpannableString("Test Place"));

            activity.onPlaceSelected(p);
        });
        // Verify card displayed
        androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withId(R.id.place_info_card))
                .check(matches(isDisplayed()));
    }
}
