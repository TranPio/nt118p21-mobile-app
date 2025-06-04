package com.example.mobile_app.maps;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Service để gọi Google Directions API và tính toán đường đi
 */
public class DirectionsService {
    private static final String TAG = "DirectionsService";
    private static final String BASE_URL = "https://maps.googleapis.com/maps/api/directions/json";
    
    private final String apiKey;
    private final Executor executor;
    
    public DirectionsService(String apiKey) {
        this.apiKey = apiKey;
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Interface để callback kết quả tính toán đường đi
     */
    public interface DirectionsCallback {
        void onDirectionsReceived(List<Route> routes);
        void onError(String errorMessage);
    }
    
    /**
     * Lớp biểu diễn một tuyến đường
     */
    public static class Route {
        private List<LatLng> points;
        private String duration;
        private String distance;
        private String summary;
        private List<Step> steps;
        
        public Route() {
            this.points = new ArrayList<>();
            this.steps = new ArrayList<>();
        }
        
        // Getters
        public List<LatLng> getPoints() { return points; }
        public String getDuration() { return duration; }
        public String getDistance() { return distance; }
        public String getSummary() { return summary; }
        public List<Step> getSteps() { return steps; }
        
        // Setters
        public void setPoints(List<LatLng> points) { this.points = points; }
        public void setDuration(String duration) { this.duration = duration; }
        public void setDistance(String distance) { this.distance = distance; }
        public void setSummary(String summary) { this.summary = summary; }
        public void setSteps(List<Step> steps) { this.steps = steps; }
    }
    
    /**
     * Lớp biểu diễn một bước trong hướng dẫn
     */
    public static class Step {
        private String instruction;
        private String distance;
        private String duration;
        private LatLng startLocation;
        private LatLng endLocation;
        
        public Step(String instruction, String distance, String duration, 
                   LatLng startLocation, LatLng endLocation) {
            this.instruction = instruction;
            this.distance = distance;
            this.duration = duration;
            this.startLocation = startLocation;
            this.endLocation = endLocation;
        }
        
        // Getters
        public String getInstruction() { return instruction; }
        public String getDistance() { return distance; }
        public String getDuration() { return duration; }
        public LatLng getStartLocation() { return startLocation; }
        public LatLng getEndLocation() { return endLocation; }
    }
    
    /**
     * Tính toán đường đi từ origin đến destination
     * 
     * @param origin Điểm bắt đầu
     * @param destination Điểm đích
     * @param travelMode Phương thức di chuyển (driving, walking, transit, bicycling)
     * @param alternatives Có lấy các tuyến đường thay thế không
     * @param callback Callback để trả về kết quả
     */
    public void getDirections(LatLng origin, LatLng destination, String travelMode, 
                             boolean alternatives, DirectionsCallback callback) {
        executor.execute(() -> {
            try {
                String urlString = buildDirectionsUrl(origin, destination, travelMode, alternatives);
                Log.d(TAG, "Requesting directions from: " + urlString);
                
                String jsonResponse = makeHttpRequest(urlString);
                List<Route> routes = parseDirectionsResponse(jsonResponse);
                
                if (routes.isEmpty()) {
                    callback.onError("Không tìm thấy đường đi");
                } else {
                    callback.onDirectionsReceived(routes);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting directions", e);
                callback.onError("Lỗi khi tính toán đường đi: " + e.getMessage());
            }
        });
    }
    
    /**
     * Tính toán đường đi với tham số mặc định (driving mode, có alternatives)
     */
    public void getDirections(LatLng origin, LatLng destination, DirectionsCallback callback) {
        getDirections(origin, destination, "driving", true, callback);
    }
    
    /**
     * Xây dựng URL cho Directions API
     */
    private String buildDirectionsUrl(LatLng origin, LatLng destination, 
                                    String travelMode, boolean alternatives) {
        StringBuilder urlBuilder = new StringBuilder(BASE_URL);
        urlBuilder.append("?origin=").append(origin.latitude).append(",").append(origin.longitude);
        urlBuilder.append("&destination=").append(destination.latitude).append(",").append(destination.longitude);
        urlBuilder.append("&mode=").append(travelMode);
        urlBuilder.append("&alternatives=").append(alternatives);
        urlBuilder.append("&language=vi");  // Tiếng Việt
        urlBuilder.append("&region=vn");    // Khu vực Việt Nam
        urlBuilder.append("&key=").append(apiKey);
        
        return urlBuilder.toString();
    }
    
    /**
     * Thực hiện HTTP request
     */
    private String makeHttpRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000); // 10 seconds
        connection.setReadTimeout(15000);    // 15 seconds
        
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                return response.toString();
            } else {
                throw new IOException("HTTP Error: " + responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * Parse JSON response từ Directions API
     */
    private List<Route> parseDirectionsResponse(String jsonResponse) throws Exception {
        List<Route> routes = new ArrayList<>();
        
        JSONObject responseObj = new JSONObject(jsonResponse);
        String status = responseObj.getString("status");
        
        if (!"OK".equals(status)) {
            throw new Exception("Directions API Error: " + status);
        }
        
        JSONArray routesArray = responseObj.getJSONArray("routes");
        
        for (int i = 0; i < routesArray.length(); i++) {
            JSONObject routeObj = routesArray.getJSONObject(i);
            Route route = parseRoute(routeObj);
            routes.add(route);
        }
        
        return routes;
    }
    
    /**
     * Parse một route từ JSON
     */
    private Route parseRoute(JSONObject routeObj) throws Exception {
        Route route = new Route();
        
        // Summary
        route.setSummary(routeObj.optString("summary", ""));
        
        // Legs (thường chỉ có 1 leg cho direct route)
        JSONArray legsArray = routeObj.getJSONArray("legs");
        if (legsArray.length() > 0) {
            JSONObject legObj = legsArray.getJSONObject(0);
            
            // Duration và distance
            JSONObject durationObj = legObj.getJSONObject("duration");
            route.setDuration(durationObj.getString("text"));
            
            JSONObject distanceObj = legObj.getJSONObject("distance");
            route.setDistance(distanceObj.getString("text"));
            
            // Steps
            JSONArray stepsArray = legObj.getJSONArray("steps");
            List<Step> steps = new ArrayList<>();
            
            for (int j = 0; j < stepsArray.length(); j++) {
                JSONObject stepObj = stepsArray.getJSONObject(j);
                Step step = parseStep(stepObj);
                steps.add(step);
            }
            route.setSteps(steps);
        }
        
        // Polyline points
        JSONObject polylineObj = routeObj.getJSONObject("overview_polyline");
        String encodedPolyline = polylineObj.getString("points");
        List<LatLng> points = decodePolyline(encodedPolyline);
        route.setPoints(points);
        
        return route;
    }
    
    /**
     * Parse một step từ JSON
     */
    private Step parseStep(JSONObject stepObj) throws Exception {
        String instruction = stepObj.getString("html_instructions")
                .replaceAll("<[^>]*>", ""); // Remove HTML tags
        
        JSONObject durationObj = stepObj.getJSONObject("duration");
        String duration = durationObj.getString("text");
        
        JSONObject distanceObj = stepObj.getJSONObject("distance");
        String distance = distanceObj.getString("text");
        
        JSONObject startLocationObj = stepObj.getJSONObject("start_location");
        LatLng startLocation = new LatLng(
            startLocationObj.getDouble("lat"),
            startLocationObj.getDouble("lng")
        );
        
        JSONObject endLocationObj = stepObj.getJSONObject("end_location");
        LatLng endLocation = new LatLng(
            endLocationObj.getDouble("lat"),
            endLocationObj.getDouble("lng")
        );
        
        return new Step(instruction, distance, duration, startLocation, endLocation);
    }
    
    /**
     * Decode Google polyline encoding
     * Based on Google's algorithm: https://developers.google.com/maps/documentation/utilities/polylinealgorithm
     */
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> points = new ArrayList<>();
        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;
        
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            
            points.add(new LatLng((lat / 1E5), (lng / 1E5)));
        }
        
        return points;
    }
}
