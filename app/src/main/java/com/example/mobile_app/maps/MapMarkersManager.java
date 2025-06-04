package com.example.mobile_app.maps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.example.mobile_app.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.model.Place;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lớp quản lý hiển thị nhiều địa điểm cùng lúc trên bản đồ
 */
public class MapMarkersManager {
    private static final String TAG = "MapMarkersManager";
    private final Context context;
    private final GoogleMap googleMap;
    private final Map<String, Marker> markers = new HashMap<>();
    private final Map<String, Place> places = new HashMap<>();
    private BitmapDescriptor defaultMarkerIcon;
    private BitmapDescriptor selectedMarkerIcon;

    public MapMarkersManager(Context context, GoogleMap googleMap) {
        this.context = context;
        this.googleMap = googleMap;
        initMarkerIcons();
    }

    /**
     * Khởi tạo các biểu tượng marker
     */
    private void initMarkerIcons() {
        defaultMarkerIcon = getBitmapDescriptor(R.drawable.ic_map_marker);
        selectedMarkerIcon = getBitmapDescriptor(R.drawable.ic_map_marker_selected);
    }

    /**
     * Chuyển đổi drawable thành BitmapDescriptor
     */
    private BitmapDescriptor getBitmapDescriptor(@DrawableRes int resourceId) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, resourceId);
        if (vectorDrawable == null) {
            Log.e(TAG, "Resource not found: " + resourceId);
            return BitmapDescriptorFactory.defaultMarker();
        }

        int width = vectorDrawable.getIntrinsicWidth();
        int height = vectorDrawable.getIntrinsicHeight();

        vectorDrawable.setBounds(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * Thêm một địa điểm vào bản đồ
     */
    public void addPlace(Place place) {
        if (place.getLatLng() == null) {
            Log.e(TAG, "Không thể thêm địa điểm không có tọa độ: " + place.getName());
            return;
        }

        String placeId = place.getId();

        // Lưu thông tin địa điểm
        places.put(placeId, place);

        // Tạo marker cho địa điểm
        MarkerOptions markerOptions = new MarkerOptions()
                .position(place.getLatLng())
                .title(place.getName())
                .icon(defaultMarkerIcon);

        // Thêm marker vào bản đồ
        Marker marker = googleMap.addMarker(markerOptions);
        if (marker != null) {
            marker.setTag(placeId);
            markers.put(placeId, marker);
        }
    }

    /**
     * Thêm nhiều địa điểm vào bản đồ
     */
    public void addPlaces(List<Place> placesList) {
        for (Place place : placesList) {
            addPlace(place);
        }

        // Di chuyển camera để hiển thị tất cả các địa điểm
        if (!placesList.isEmpty()) {
            animateCameraToBounds(placesList);
        }
    }

    /**
     * Xóa tất cả các địa điểm khỏi bản đồ
     */
    public void clearPlaces() {
        for (Marker marker : markers.values()) {
            marker.remove();
        }
        markers.clear();
        places.clear();
    }

    /**
     * Làm nổi bật một địa điểm
     */
    public void highlightPlace(String placeId) {
        Marker marker = markers.get(placeId);
        if (marker != null) {
            // Đổi biểu tượng marker thành biểu tượng được chọn
            marker.setIcon(selectedMarkerIcon);
            // Hiển thị thông tin của marker
            marker.showInfoWindow();
            // Di chuyển camera đến vị trí của marker
            googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 15f),
                    300,
                    null
            );
        }
    }

    /**
     * Bỏ làm nổi bật một địa điểm
     */
    public void unhighlightPlace(String placeId) {
        Marker marker = markers.get(placeId);
        if (marker != null) {
            marker.setIcon(defaultMarkerIcon);
        }
    }

    /**
     * Di chuyển camera để hiển thị tất cả các địa điểm
     */
    public void animateCameraToBounds(List<Place> placesList) {
        if (placesList.isEmpty()) {
            return;
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Place place : placesList) {
            if (place.getLatLng() != null) {
                builder.include(place.getLatLng());
            }
        }

        try {
            LatLngBounds bounds = builder.build();
            int padding = 100; // Khoảng cách từ viền bản đồ đến marker, đơn vị là pixel
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
        } catch (IllegalStateException e) {
            // Xử lý trường hợp không có điểm nào trên bản đồ
            Log.e(TAG, "Không thể tạo bounds: " + e.getMessage());
        }
    }

    /**
     * Lấy địa điểm theo placeId
     */
    public Place getPlace(String placeId) {
        return places.get(placeId);
    }

    /**
     * Lấy tất cả các địa điểm
     */
    public List<Place> getAllPlaces() {
        return new ArrayList<>(places.values());
    }    /**
     * Thiết lập OnMarkerClickListener
     */
    public void setOnMarkerClickListener(GoogleMap.OnMarkerClickListener listener) {
        googleMap.setOnMarkerClickListener(listener);
    }

    /**
     * Xóa highlight tất cả marker
     */
    public void clearHighlight() {
        for (Marker marker : markers.values()) {
            if (marker != null) {
                marker.setIcon(defaultMarkerIcon);
            }
        }
    }
}
