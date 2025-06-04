package com.example.mobile_app.maps;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lớp hỗ trợ xử lý nhiều địa điểm cùng lúc
 */
public class MultiPlaceDetailsHelper {
    private static final String TAG = "MultiPlaceDetailsHelper";
    private final Context context;
    private final PlacesClient placesClient;
    private final LatLng currentLocation;

    public MultiPlaceDetailsHelper(Context context, PlacesClient placesClient, LatLng currentLocation) {
        this.context = context;
        this.placesClient = placesClient;
        this.currentLocation = currentLocation;
    }

    /**
     * Interface callback khi lấy thông tin chi tiết nhiều địa điểm
     */
    public interface MultiPlaceDetailsCallback {
        void onPlacesDetailsFetched(List<Place> places);
        void onError(String message);
    }

    /**
     * Lấy thông tin chi tiết của nhiều địa điểm cùng lúc
     */
    public void fetchMultiplePlaceDetails(List<AutocompletePrediction> predictions, MultiPlaceDetailsCallback callback) {
        if (predictions == null || predictions.isEmpty()) {
            callback.onError("Danh sách địa điểm trống");
            return;
        }

        List<Place> placesList = new ArrayList<>();
        AtomicInteger pendingRequests = new AtomicInteger(predictions.size());

        for (AutocompletePrediction prediction : predictions) {
            String placeId = prediction.getPlaceId();

            // Các trường thông tin cần lấy
            List<Place.Field> placeFields = Arrays.asList(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS,
                    Place.Field.TYPES,
                    Place.Field.RATING,
                    Place.Field.USER_RATINGS_TOTAL,
                    Place.Field.PHOTO_METADATAS
            );

            // Tạo request
            FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, placeFields);

            // Gọi API để lấy thông tin chi tiết
            placesClient.fetchPlace(request)
                    .addOnSuccessListener(response -> {
                        Place place = response.getPlace();
                        synchronized (placesList) {
                            placesList.add(place);
                        }

                        // Kiểm tra xem đã lấy hết thông tin các địa điểm chưa
                        if (pendingRequests.decrementAndGet() == 0) {
                            callback.onPlacesDetailsFetched(placesList);
                        }
                    })
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Không thể lấy thông tin địa điểm: " + exception.getMessage());

                        // Kiểm tra xem đã lấy hết thông tin các địa điểm chưa
                        if (pendingRequests.decrementAndGet() == 0) {
                            if (placesList.isEmpty()) {
                                callback.onError("Không thể lấy thông tin địa điểm");
                            } else {
                                callback.onPlacesDetailsFetched(placesList);
                            }
                        }
                    });
        }
    }

    /**
     * Lấy thông tin chi tiết của một địa điểm
     */
    public void fetchPlaceDetails(String placeId, PlaceDetailsHelper.PlaceDetailsCallback callback) {
        PlaceDetailsHelper singlePlaceHelper = new PlaceDetailsHelper(context, placesClient, currentLocation);
        singlePlaceHelper.fetchPlaceDetails(placeId, callback);
    }
}
