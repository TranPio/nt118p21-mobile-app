package com.example.mobile_app.maps;

import android.content.Context;
import android.graphics.Color;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Quản lý chế độ navigation trong ứng dụng với polyline highlighting,
 * turn-by-turn instructions, và Vietnamese TTS
 */
public class InAppNavigationManager implements TextToSpeech.OnInitListener {
    private static final String TAG = "InAppNavigationManager";
    
    private final Context context;
    private final GoogleMap map;
    private TextToSpeech tts;
    
    // Navigation state
    private DirectionsService.Route currentRoute;
    private List<LatLng> routePoints;
    private Polyline navigationPolyline;
    private Marker currentLocationMarker;
    private Marker destinationMarker;
    private boolean isNavigating = false;
    private int currentInstructionIndex = 0;
    
    // Navigation UI listeners
    public interface NavigationListener {
        void onNavigationStarted(DirectionsService.Route route);
        void onNavigationStopped();
        void onInstructionChanged(String instruction, String distance);
        void onDestinationReached();
    }
    
    private NavigationListener navigationListener;
    
    public InAppNavigationManager(Context context, GoogleMap map) {
        this.context = context;
        this.map = map;
        initializeTTS();
    }
    
    /**
     * Initialize Vietnamese Text-to-Speech
     */
    private void initializeTTS() {
        tts = new TextToSpeech(context, this);
    }
    
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Thiết lập tiếng Việt
            int result = tts.setLanguage(new Locale("vi", "VN"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Vietnamese not supported, using English");
                tts.setLanguage(Locale.ENGLISH);
            }
            tts.setSpeechRate(0.8f); // Nói chậm hơn một chút
        } else {
            Log.e(TAG, "TTS initialization failed");
        }
    }
    
    /**
     * Bắt đầu navigation với route đã chọn
     */
    public void startNavigation(DirectionsService.Route route, LatLng currentLocation) {
        if (route == null || route.getPoints() == null || route.getPoints().isEmpty()) {
            Log.e(TAG, "Invalid route for navigation");
            return;
        }
        
        this.currentRoute = route;
        this.routePoints = new ArrayList<>(route.getPoints());
        this.isNavigating = true;
        this.currentInstructionIndex = 0;
        
        Log.d(TAG, "🚀 Starting in-app navigation with " + routePoints.size() + " points");
        
        // Xóa polylines cũ và tạo polyline navigation
        clearNavigationElements();
        createNavigationPolyline();
        
        // Thêm markers
        addNavigationMarkers(currentLocation);
        
        // Điều chỉnh camera để hiển thị toàn bộ route
        adjustCameraToRoute();
        
        // Đọc instruction đầu tiên
        announceFirstInstruction();
        
        // Thông báo cho listener
        if (navigationListener != null) {
            navigationListener.onNavigationStarted(route);
        }
    }
      /**
     * Tạo polyline navigation với màu nổi bật
     */
    private void createNavigationPolyline() {
        if (routePoints == null || routePoints.isEmpty()) return;
        
        // Create a bold, vibrant polyline similar to Google Maps navigation
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(routePoints)                .width(15f) // Thicker line for better visibility in navigation mode
                .color(Color.parseColor("#4285F4")) // Google Maps blue color
                .geodesic(true)
                .pattern(null); // Solid line
        
        navigationPolyline = map.addPolyline(polylineOptions);
        Log.d(TAG, "✅ Navigation polyline created with " + routePoints.size() + " points");
    }
    
    /**
     * Thêm markers cho navigation
     */    private void addNavigationMarkers(LatLng currentLocation) {
        // Current location marker (blue Google Maps style marker)
        if (currentLocation != null) {
            currentLocationMarker = map.addMarker(new MarkerOptions()
                    .position(currentLocation)
                    .title("Vị trí của bạn")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .zIndex(2.0f)); // Higher z-index to show on top
        }
        
        // Destination marker (red pin - Google Maps style)
        if (routePoints != null && !routePoints.isEmpty()) {
            LatLng destination = routePoints.get(routePoints.size() - 1);
            destinationMarker = map.addMarker(new MarkerOptions()
                    .position(destination)
                    .title("Điểm đến")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .zIndex(1.0f));
        }
    }
    
    /**
     * Điều chỉnh camera để hiển thị toàn bộ route
     */    private void adjustCameraToRoute() {
        if (routePoints == null || routePoints.isEmpty()) return;
        
        // Build bounds including all route points
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (LatLng point : routePoints) {
            boundsBuilder.include(point);
        }
        
        try {
            // Add extra padding for better visibility
            LatLngBounds bounds = boundsBuilder.build();
            
            // Calculate appropriate padding based on screen density
            int padding = (int) (100 * context.getResources().getDisplayMetrics().density);
            
            // Animate camera smoothly
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), 1000, null);
            
            // After showing the overview, focus on the first part of the route
            if (routePoints.size() > 1) {
                final LatLng startPoint = routePoints.get(0);
                final LatLng nextPoint = routePoints.get(1);
                
                // Calculate initial bearing (direction) of the route
                float bearing = calculateBearing(startPoint, nextPoint);
                
                // After a delay, tilt and rotate the camera for navigation-like view
                new android.os.Handler().postDelayed(() -> {
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            new com.google.android.gms.maps.model.CameraPosition.Builder()
                                .target(startPoint)
                                .zoom(18f)
                                .tilt(45f)  // Tilt for 3D-like view
                                .bearing(bearing)  // Point in direction of travel
                                .build()
                        ), 
                        1000, 
                        null
                    );
                }, 2000);
            }
        } catch (Exception e) {
            // Fallback in case of error
            Log.e(TAG, "Error adjusting camera to route", e);
            if (routePoints.size() > 0) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(routePoints.get(0), 15f));
            }
        }
    }
    
    /**
     * Calculate bearing between two points for camera orientation
     */
    private float calculateBearing(LatLng start, LatLng end) {
        double startLat = Math.toRadians(start.latitude);
        double startLng = Math.toRadians(start.longitude);
        double endLat = Math.toRadians(end.latitude);
        double endLng = Math.toRadians(end.longitude);
        
        double dLng = endLng - startLng;
        double y = Math.sin(dLng) * Math.cos(endLat);
        double x = Math.cos(startLat) * Math.sin(endLat) - 
                   Math.sin(startLat) * Math.cos(endLat) * Math.cos(dLng);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (float) ((bearing + 360) % 360);
    }
    
    /**
     * Đọc instruction đầu tiên
     */
    private void announceFirstInstruction() {
        if (currentRoute == null) return;
        
        String duration = currentRoute.getDuration();
        String distance = currentRoute.getDistance();
        
        String instruction = String.format("Bắt đầu navigation. Tổng thời gian %s, quãng đường %s", 
                duration != null ? duration : "không xác định",
                distance != null ? distance : "không xác định");
        
        speakInstruction(instruction);
        
        // Thông báo cho UI
        if (navigationListener != null) {
            navigationListener.onInstructionChanged(instruction, distance);
        }
    }
    
    /**
     * Đọc instruction bằng TTS
     */
    private void speakInstruction(String instruction) {
        if (tts != null && instruction != null && !instruction.isEmpty()) {
            tts.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null);
            Log.d(TAG, "🔊 TTS: " + instruction);
        }
    }
    
    /**
     * Cập nhật vị trí hiện tại trong navigation
     */
    public void updateCurrentLocation(LatLng newLocation) {
        if (!isNavigating || newLocation == null) return;
        
        // Cập nhật marker vị trí hiện tại
        if (currentLocationMarker != null) {
            currentLocationMarker.setPosition(newLocation);
        }
        
        // Logic để kiểm tra xem có gần đích hay không
        checkIfNearDestination(newLocation);
        
        // Có thể thêm logic turn-by-turn navigation thực tế ở đây
    }
    
    /**
     * Kiểm tra xem có gần đến đích hay không
     */
    private void checkIfNearDestination(LatLng currentLocation) {
        if (routePoints == null || routePoints.isEmpty()) return;
        
        LatLng destination = routePoints.get(routePoints.size() - 1);
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                destination.latitude, destination.longitude,
                results);
        
        // Nếu ở trong bán kính 50m của đích
        if (results[0] <= 50) {
            reachDestination();
        }
    }
    
    /**
     * Xử lý khi đến đích
     */
    private void reachDestination() {
        speakInstruction("Bạn đã đến đích!");
        stopNavigation();
        
        if (navigationListener != null) {
            navigationListener.onDestinationReached();
        }
    }
    
    /**
     * Dừng navigation
     */
    public void stopNavigation() {
        Log.d(TAG, "🛑 Stopping in-app navigation");
        
        isNavigating = false;
        currentRoute = null;
        routePoints = null;
        currentInstructionIndex = 0;
        
        clearNavigationElements();
        
        if (navigationListener != null) {
            navigationListener.onNavigationStopped();
        }
    }
    
    /**
     * Xóa tất cả elements navigation
     */
    private void clearNavigationElements() {
        if (navigationPolyline != null) {
            navigationPolyline.remove();
            navigationPolyline = null;
        }
        
        if (currentLocationMarker != null) {
            currentLocationMarker.remove();
            currentLocationMarker = null;
        }
        
        if (destinationMarker != null) {
            destinationMarker.remove();
            destinationMarker = null;
        }
    }
    
    /**
     * Set navigation listener
     */
    public void setNavigationListener(NavigationListener listener) {
        this.navigationListener = listener;
    }
    
    /**
     * Kiểm tra xem có đang navigation hay không
     */
    public boolean isNavigating() {
        return isNavigating;
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        stopNavigation();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
