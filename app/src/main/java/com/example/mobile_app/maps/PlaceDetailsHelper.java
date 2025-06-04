package com.example.mobile_app.maps;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Lớp trợ giúp quản lý các tác vụ liên quan đến thông tin chi tiết địa điểm
 */
public class PlaceDetailsHelper {
    private static final String TAG = "PlaceDetailsHelper";
    private final Context context;
    private final PlacesClient placesClient;
    private final LatLng currentLocation;

    public PlaceDetailsHelper(Context context, PlacesClient placesClient, LatLng currentLocation) {
        this.context = context;
        this.placesClient = placesClient;
        this.currentLocation = currentLocation;
    }

    /**
     * Interface callback khi lấy thông tin chi tiết địa điểm
     */
    public interface PlaceDetailsCallback {
        void onPlaceDetailsFetched(Place place);
        void onError(String message);
    }    /**
     * Lấy thông tin chi tiết của một địa điểm dựa vào placeId
     */
    public void fetchPlaceDetails(String placeId, PlaceDetailsCallback callback) {
        // Các trường thông tin cần lấy
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.TYPES,
                Place.Field.RATING,
                Place.Field.USER_RATINGS_TOTAL,
                Place.Field.PHOTO_METADATAS,
                Place.Field.PHONE_NUMBER,
                Place.Field.OPENING_HOURS,
                Place.Field.WEBSITE_URI,
                Place.Field.BUSINESS_STATUS
        );

        // Tạo request
        FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, placeFields);

        // Gọi API để lấy thông tin chi tiết
        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    callback.onPlaceDetailsFetched(place);
                })
                .addOnFailureListener(exception -> {
                    Log.e(TAG, "Không thể lấy thông tin địa điểm: " + exception.getMessage());
                    callback.onError(exception.getMessage());
                });
    }

    /**
     * Tính khoảng cách từ vị trí hiện tại đến địa điểm
     */
    public float getDistanceInMeters(LatLng destination) {
        if (currentLocation == null || destination == null) {
            return -1;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                destination.latitude, destination.longitude,
                results);

        return results[0];
    }

    /**
     * Định dạng khoảng cách thành chuỗi dễ đọc
     */
    public String getFormattedDistance(LatLng destination) {
        float distanceInMeters = getDistanceInMeters(destination);
        if (distanceInMeters < 0) {
            return "N/A";
        }

        if (distanceInMeters < 1000) {
            return Math.round(distanceInMeters) + " m";
        } else {
            DecimalFormat df = new DecimalFormat("#.#");
            return df.format(distanceInMeters / 1000) + " km";
        }
    }
}
