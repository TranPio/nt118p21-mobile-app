package com.example.mobile_app.maps;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý việc hiển thị các tuyến đường trên bản đồ Google Maps
 */
public class RouteManager {
    private static final String TAG = "RouteManager";
    
    private final Context context;
    private final GoogleMap map;
    private final List<Polyline> routePolylines;
    private final List<Marker> routeMarkers;
    private List<DirectionsService.Route> currentRoutes;
    private int selectedRouteIndex = 0;
    
    // Colors cho các tuyến đường khác nhau
    private static final int[] ROUTE_COLORS = {
        Color.parseColor("#4285F4"), // Primary blue
        Color.parseColor("#34A853"), // Green
        Color.parseColor("#EA4335"), // Red
        Color.parseColor("#FBBC04"), // Yellow
        Color.parseColor("#9C27B0")  // Purple
    };
    
    private static final int SELECTED_ROUTE_WIDTH = 8;
    private static final int ALTERNATIVE_ROUTE_WIDTH = 5;
    
    public interface RouteSelectionListener {
        void onRouteSelected(DirectionsService.Route route, int index);
    }
    
    private RouteSelectionListener routeSelectionListener;
    
    public RouteManager(Context context, GoogleMap map) {
        this.context = context;
        this.map = map;
        this.routePolylines = new ArrayList<>();
        this.routeMarkers = new ArrayList<>();
        this.currentRoutes = new ArrayList<>();
    }
    
    /**
     * Hiển thị các tuyến đường trên bản đồ
     * 
     * @param routes Danh sách các tuyến đường
     * @param origin Điểm bắt đầu
     * @param destination Điểm đích
     */
    public void displayRoutes(List<DirectionsService.Route> routes, LatLng origin, LatLng destination) {
        // Xóa các route cũ
        clearRoutes();
        
        if (routes == null || routes.isEmpty()) {
            Log.w(TAG, "No routes to display");
            return;
        }
        
        this.currentRoutes = new ArrayList<>(routes);
        this.selectedRouteIndex = 0;
        
        Log.d(TAG, "Displaying " + routes.size() + " routes");
        
        // Hiển thị từng tuyến đường
        for (int i = 0; i < routes.size(); i++) {
            DirectionsService.Route route = routes.get(i);
            displaySingleRoute(route, i, i == selectedRouteIndex);
        }
        
        // Thêm markers cho điểm bắt đầu và điểm đích
        addRouteMarkers(origin, destination);
        
        // Điều chỉnh camera để hiển thị toàn bộ route
        adjustCameraToShowRoute();
        
        // Setup click listeners cho polylines
        setupPolylineClickListeners();
    }
      /**
     * Hiển thị một tuyến đường đơn lẻ
     */
    private void displaySingleRoute(DirectionsService.Route route, int index, boolean isSelected) {
        List<LatLng> points = route.getPoints();
        if (points == null || points.isEmpty()) {
            Log.w(TAG, "Route " + index + " has no points");
            return;
        }
        
        // Tạo polyline options
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(points)
                .width(isSelected ? SELECTED_ROUTE_WIDTH : ALTERNATIVE_ROUTE_WIDTH)
                .color(getRouteColor(index, isSelected))
                .geodesic(true)
                .clickable(true);
        
        // Thêm polyline vào map
        Polyline polyline = map.addPolyline(polylineOptions);
        polyline.setTag(index); // Lưu index để xử lý click
        routePolylines.add(polyline);
        
        // Thêm nhãn thời gian lên tuyến đường
        addRouteTimeLabel(route, points, index);
        
        Log.d(TAG, "Added route " + index + " with " + points.size() + " points, " +
                   "duration: " + route.getDuration() + ", distance: " + route.getDistance());
    }
    
    /**
     * Thêm nhãn thời gian lên tuyến đường
     */
    private void addRouteTimeLabel(DirectionsService.Route route, List<LatLng> points, int index) {
        if (route.getDuration() == null || points.isEmpty()) {
            return;
        }
        
        // Tìm điểm giữa tuyến đường để đặt nhãn
        int midIndex = points.size() / 2;
        LatLng midPoint = points.get(midIndex);
        
        // Tạo marker nhãn thời gian với background màu
        MarkerOptions labelOptions = new MarkerOptions()
                .position(midPoint)
                .title(route.getDuration())
                .snippet("Tuyến đường " + (index + 1))
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.fromBitmap(
                    createTimeLabelBitmap(route.getDuration(), getRouteColor(index, index == selectedRouteIndex))));
        
        Marker labelMarker = map.addMarker(labelOptions);
        if (labelMarker != null) {
            labelMarker.setTag("time_label_" + index);
            routeMarkers.add(labelMarker);
        }
    }
    
    /**
     * Tạo bitmap cho nhãn thời gian
     */
    private android.graphics.Bitmap createTimeLabelBitmap(String timeText, int backgroundColor) {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setTextSize(32);
        paint.setColor(android.graphics.Color.WHITE);
        paint.setAntiAlias(true);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
        android.graphics.Paint backgroundPaint = new android.graphics.Paint();
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setAntiAlias(true);
        
        // Đo kích thước text
        android.graphics.Rect textBounds = new android.graphics.Rect();
        paint.getTextBounds(timeText, 0, timeText.length(), textBounds);
        
        int padding = 20;
        int width = textBounds.width() + padding * 2;
        int height = textBounds.height() + padding * 2;
        
        // Tạo bitmap
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        
        // Vẽ background bo tròn
        float radius = height / 2f;
        canvas.drawRoundRect(0, 0, width, height, radius, radius, backgroundPaint);
        
        // Vẽ text
        float x = padding;
        float y = height / 2f + textBounds.height() / 2f;
        canvas.drawText(timeText, x, y, paint);
        
        return bitmap;
    }
    
    /**
     * Lấy màu cho tuyến đường
     */
    private int getRouteColor(int index, boolean isSelected) {
        int baseColor = ROUTE_COLORS[index % ROUTE_COLORS.length];
        
        if (isSelected) {
            return baseColor;
        } else {
            // Làm mờ routes không được chọn
            int alpha = 180; // 70% opacity
            return Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        }
    }
    
    /**
     * Thêm markers cho điểm bắt đầu và đích
     */
    private void addRouteMarkers(LatLng origin, LatLng destination) {
        // Marker điểm bắt đầu (màu xanh lá)
        MarkerOptions originMarkerOptions = new MarkerOptions()
                .position(origin)
                .title("Điểm bắt đầu")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
        
        Marker originMarker = map.addMarker(originMarkerOptions);
        routeMarkers.add(originMarker);
        
        // Marker điểm đích (màu đỏ)
        MarkerOptions destinationMarkerOptions = new MarkerOptions()
                .position(destination)
                .title("Điểm đích")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        
        Marker destinationMarker = map.addMarker(destinationMarkerOptions);
        routeMarkers.add(destinationMarker);
    }
    
    /**
     * Điều chỉnh camera để hiển thị toàn bộ route
     */
    private void adjustCameraToShowRoute() {
        if (currentRoutes.isEmpty()) return;
        
        DirectionsService.Route selectedRoute = currentRoutes.get(selectedRouteIndex);
        List<LatLng> points = selectedRoute.getPoints();
        
        if (points.isEmpty()) return;
        
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (LatLng point : points) {
            boundsBuilder.include(point);
        }
        
        LatLngBounds bounds = boundsBuilder.build();
        int padding = 100; // pixels
        
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting camera", e);
        }
    }
    
    /**
     * Setup click listeners cho polylines
     */
    private void setupPolylineClickListeners() {
        map.setOnPolylineClickListener(polyline -> {
            Object tag = polyline.getTag();
            if (tag instanceof Integer) {
                int clickedRouteIndex = (Integer) tag;
                selectRoute(clickedRouteIndex);
            }
        });
    }
    
    /**
     * Chọn một tuyến đường khác
     */
    public void selectRoute(int routeIndex) {
        if (routeIndex < 0 || routeIndex >= currentRoutes.size()) {
            Log.w(TAG, "Invalid route index: " + routeIndex);
            return;
        }
        
        if (routeIndex == selectedRouteIndex) {
            return; // Đã được chọn rồi
        }
        
        Log.d(TAG, "Selecting route " + routeIndex);
        
        // Cập nhật giao diện các polylines
        for (int i = 0; i < routePolylines.size(); i++) {
            Polyline polyline = routePolylines.get(i);
            boolean isSelected = (i == routeIndex);
            
            polyline.setWidth(isSelected ? SELECTED_ROUTE_WIDTH : ALTERNATIVE_ROUTE_WIDTH);
            polyline.setColor(getRouteColor(i, isSelected));
        }
        
        selectedRouteIndex = routeIndex;
        
        // Thông báo về việc thay đổi route
        if (routeSelectionListener != null) {
            routeSelectionListener.onRouteSelected(currentRoutes.get(routeIndex), routeIndex);
        }
        
        // Điều chỉnh camera để hiển thị route mới
        adjustCameraToShowRoute();
    }
    
    /**
     * Xóa tất cả các routes khỏi bản đồ
     */
    public void clearRoutes() {
        // Xóa polylines
        for (Polyline polyline : routePolylines) {
            polyline.remove();
        }
        routePolylines.clear();
        
        // Xóa markers
        for (Marker marker : routeMarkers) {
            marker.remove();
        }
        routeMarkers.clear();
        
        // Clear data
        currentRoutes.clear();
        selectedRouteIndex = 0;
        
        Log.d(TAG, "Cleared all routes");
    }
    
    /**
     * Lấy route hiện tại được chọn
     */
    public DirectionsService.Route getSelectedRoute() {
        if (selectedRouteIndex >= 0 && selectedRouteIndex < currentRoutes.size()) {
            return currentRoutes.get(selectedRouteIndex);
        }
        return null;
    }
    
    /**
     * Lấy index của route hiện tại được chọn
     */
    public int getSelectedRouteIndex() {
        return selectedRouteIndex;
    }
    
    /**
     * Lấy tất cả routes hiện tại
     */
    public List<DirectionsService.Route> getCurrentRoutes() {
        return new ArrayList<>(currentRoutes);
    }
    
    /**
     * Kiểm tra có routes nào đang hiển thị không
     */
    public boolean hasRoutes() {
        return !currentRoutes.isEmpty();
    }
    
    /**
     * Set listener cho sự kiện chọn route
     */
    public void setRouteSelectionListener(RouteSelectionListener listener) {
        this.routeSelectionListener = listener;
    }
    
    /**
     * Lấy thông tin tóm tắt của route được chọn
     */
    public String getSelectedRouteSummary() {
        DirectionsService.Route route = getSelectedRoute();
        if (route != null) {
            return String.format("Khoảng cách: %s • Thời gian: %s", 
                                route.getDistance(), route.getDuration());
        }
        return "";
    }
    
    /**
     * Highlight một route cụ thể tạm thời (ví dụ khi hover)
     */
    public void highlightRoute(int routeIndex, boolean highlight) {
        if (routeIndex < 0 || routeIndex >= routePolylines.size()) return;
        
        Polyline polyline = routePolylines.get(routeIndex);
        if (highlight && routeIndex != selectedRouteIndex) {
            polyline.setWidth(SELECTED_ROUTE_WIDTH - 1);
        } else {
            boolean isSelected = (routeIndex == selectedRouteIndex);
            polyline.setWidth(isSelected ? SELECTED_ROUTE_WIDTH : ALTERNATIVE_ROUTE_WIDTH);
        }
    }
}
