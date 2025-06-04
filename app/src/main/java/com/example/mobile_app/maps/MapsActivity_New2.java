package com.example.mobile_app.maps;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mobile_app.R;
import com.example.mobile_app.databinding.ActivityMapsBinding;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.gms.maps.model.MapStyleOptions;

import com.example.mobile_app.presentation.MapController;
import com.example.mobile_app.presentation.MapViewModel;
import com.example.mobile_app.domain.GetDirectionsUseCase;

import java.util.Arrays;
import java.util.List;
import com.example.mobile_app.data.MapRepositoryImpl;
import com.google.android.gms.maps.model.LatLng;

public class MapsActivity_New2 extends FragmentActivity implements OnMapReadyCallback,
        PlaceAutocompleteAdapter_Material3.OnPlaceClickListener,
        CustomPlaceSearchView_Improved_New.OnPlaceSelectedListener,
        CustomPlaceSearchView_Improved_New.OnPlacesSearchResultListener,
        GoogleMap.OnMarkerClickListener,
        PlaceInfoCard.PlaceInfoCardListener,
        CompactRoutePanel.CompactRoutePanelListener,
        RouteManager.RouteSelectionListener,
        InAppNavigationManager_New.NavigationListener {
    private static final String TAG = "MapsActivity_New2";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GoogleMap mMap;
    private MapController mapController;
    private MapViewModel viewModel;
    private ActivityMapsBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesClient placesClient;
    private FloatingActionButton fabMyLocation;
    private FloatingActionButton fabMapSettings;
    private InAppNavigationManager_New inAppNavigationManager;
    private CustomPlaceSearchView_Improved_New customPlaceSearch;
    private LatLng currentLocation;
    private PlaceDetailsHelper placeDetailsHelper;
    private MultiPlaceDetailsHelper multiPlaceDetailsHelper;
    private MapMarkersManager mapMarkersManager;
    private List<AutocompletePrediction> currentSearchResults;
    private PlaceInfoCard placeInfoCard;
    private DirectionsService directionsService;
    private RouteManager routeManager;
    private CompactRoutePanel compactRoutePanel;
    private LatLng lastSelectedDestination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this,
                new ViewModelProvider.Factory() {
                    @NonNull
                    @Override
                    public <T extends androidx.lifecycle.ViewModel> T create(@NonNull Class<T> modelClass) {
                        GetDirectionsUseCase useCase = new GetDirectionsUseCase(new MapRepositoryImpl());
                        return (T) new MapViewModel(useCase);
                    }
                }).get(MapViewModel.class);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (!Places.isInitialized()) {
            String apiKey = getString(R.string.google_maps_key);
            Log.d(TAG, "Initializing Places API with key");
            Places.initialize(getApplicationContext(), apiKey);
        }
        placesClient = Places.createClient(this);

        customPlaceSearch = findViewById(R.id.custom_place_search);
        customPlaceSearch.setPlacesClient(placesClient);
        customPlaceSearch.setOnPlaceSelectedListener(this);
        customPlaceSearch.setOnPlacesSearchResultListener(this);
        customPlaceSearch.setHint("Tìm kiếm địa điểm");

        fabMyLocation = findViewById(R.id.fab_my_location);
        fabMyLocation.setOnClickListener(view -> getDeviceLocation());

        fabMapSettings = findViewById(R.id.fab_map_settings);
        fabMapSettings.setOnClickListener(v -> {
            if (mMap != null) {
                showMapSettingsDialog();
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        viewModel.getRoute().observe(this, points -> {
            if (mapController != null && points != null && points.size() > 1) {
                mapController.addMarker(points.get(0), "Start");
                mapController.addMarker(points.get(points.size() - 1), "End");
            }
        });

        initializePlaceInfoCard();
        initializeDirectionsServices();
        setupTouchOutsideListener();
    }

    private void initializePlaceInfoCard() {
        placeInfoCard = new PlaceInfoCard(this, placesClient, null);
        ViewGroup container = findViewById(R.id.place_info_container);
        placeInfoCard.setParentContainer(container);
        placeInfoCard.setListener(this);
    }

    private void initializeDirectionsServices() {
        String apiKey = getString(R.string.google_maps_key);
        directionsService = new DirectionsService(apiKey);

        compactRoutePanel = new CompactRoutePanel(this);
        ViewGroup routeContainer = findViewById(R.id.compact_route_panel_container);
        compactRoutePanel.setParentContainer(routeContainer);
        compactRoutePanel.setListener(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mapController = new MapController(googleMap);

        SharedPreferences prefs = getSharedPreferences("map_prefs", MODE_PRIVATE);
        int type = prefs.getInt("map_type", GoogleMap.MAP_TYPE_NORMAL);
        boolean traffic = prefs.getBoolean("traffic", false);
        mMap.setMapType(type);
        mMap.setTrafficEnabled(traffic);

        enableMyLocation();

        inAppNavigationManager = new InAppNavigationManager_New(this, mMap);
        inAppNavigationManager.setNavigationListener(this);

        mapMarkersManager = new MapMarkersManager(this, mMap);

        routeManager = new RouteManager(this, mMap);
        routeManager.setRouteSelectionListener(this);

        mMap.setOnMapClickListener(latLng -> {
            if (customPlaceSearch != null) {
                customPlaceSearch.hideSuggestionsListPublic();
            }
            if (placeInfoCard != null) {
                placeInfoCard.hide();
            }
            if (compactRoutePanel != null) {
                compactRoutePanel.hide();
            }
            if (mapMarkersManager != null) {
                mapMarkersManager.clearHighlight();
            }
            clearDirections();
        });

        mMap.setOnMarkerClickListener(this);

        LatLng hoChiMinhCity = new LatLng(10.762622, 106.660172);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(hoChiMinhCity, 12));
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
                getDeviceLocation();
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    public interface LocationCallback {
        void onLocationResult(LatLng location);
    }

    private void getDeviceLocation() {
        getDeviceLocation(null);
    }

    private void getDeviceLocation(LocationCallback callback) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, location -> {
                            if (location != null) {
                                currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                                placeDetailsHelper = new PlaceDetailsHelper(this, placesClient, currentLocation);
                                multiPlaceDetailsHelper = new MultiPlaceDetailsHelper(this, placesClient, currentLocation);

                                if (placeInfoCard != null) {
                                    placeInfoCard = new PlaceInfoCard(this, placesClient, placeDetailsHelper);
                                    ViewGroup container = findViewById(R.id.place_info_container);
                                    placeInfoCard.setParentContainer(container);
                                    placeInfoCard.setListener(this);
                                }

                                setupImprovedPlaceAutocomplete();

                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                        currentLocation, 15));

                                if (callback != null) {
                                    callback.onLocationResult(currentLocation);
                                }
                            } else {
                                useDefaultLocation();
                                if (callback != null) {
                                    callback.onLocationResult(currentLocation);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            useDefaultLocation();
                            if (callback != null) {
                                callback.onLocationResult(currentLocation);
                            }
                        });
            } else {
                requestLocationPermission();
                useDefaultLocation();
                if (callback != null) {
                    callback.onLocationResult(currentLocation);
                }
            }
        } catch (Exception e) {
            useDefaultLocation();
            if (callback != null) {
                callback.onLocationResult(currentLocation);
            }
        }
    }

    private void useDefaultLocation() {
        currentLocation = new LatLng(10.7756587, 106.7004238);

        placeDetailsHelper = new PlaceDetailsHelper(this, placesClient, currentLocation);
        multiPlaceDetailsHelper = new MultiPlaceDetailsHelper(this, placesClient, currentLocation);

        if (placeInfoCard != null) {
            placeInfoCard = new PlaceInfoCard(this, placesClient, placeDetailsHelper);
            ViewGroup container = findViewById(R.id.place_info_container);
            placeInfoCard.setParentContainer(container);
            placeInfoCard.setListener(this);
        }

        setupImprovedPlaceAutocomplete();
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    private void setupImprovedPlaceAutocomplete() {
        if (customPlaceSearch != null) {
            customPlaceSearch.setCurrentLocation(currentLocation);
        }
    }

    @Override
    public void onPlaceSelected(AutocompletePrediction prediction) {
        if (placeDetailsHelper != null) {
            placeDetailsHelper.fetchPlaceDetails(prediction.getPlaceId(), new PlaceDetailsHelper.PlaceDetailsCallback() {
                @Override
                public void onPlaceDetailsFetched(Place place) {
                    LatLng latLng = place.getLatLng();
                    if (latLng != null) {
                        if (mapMarkersManager != null) {
                            mapMarkersManager.clearPlaces();
                            mapMarkersManager.addPlace(place);
                            mapMarkersManager.highlightPlace(place.getId());
                        }
                        if (placeInfoCard != null) {
                            placeInfoCard.showPlaceInfo(place);
                        }
                        if (customPlaceSearch != null) {
                            customPlaceSearch.hideSuggestionsListPublic();
                        }
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                    }
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MapsActivity_New2.this,
                            "Lỗi lấy thông tin địa điểm: " + message,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onPlacesSearchResult(List<AutocompletePrediction> predictions) {
        currentSearchResults = predictions;

        if (multiPlaceDetailsHelper != null) {
            if (mapMarkersManager != null) {
                mapMarkersManager.clearPlaces();
            }

            int maxResults = Math.min(predictions.size(), 5);
            List<AutocompletePrediction> topPredictions = predictions.subList(0, maxResults);

            multiPlaceDetailsHelper.fetchMultiplePlaceDetails(topPredictions,
                    new MultiPlaceDetailsHelper.MultiPlaceDetailsCallback() {
                        @Override
                        public void onPlacesDetailsFetched(List<Place> places) {
                            if (mapMarkersManager != null && !places.isEmpty()) {
                                mapMarkersManager.addPlaces(places);

                                if (places.size() == 1 && placeInfoCard != null) {
                                    Place firstPlace = places.get(0);
                                    placeInfoCard.showPlaceInfo(firstPlace);

                                    if (firstPlace.getLatLng() != null) {
                                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstPlace.getLatLng(), 15));
                                    }
                                }
                            }
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(MapsActivity_New2.this,
                                    "Lỗi lấy thông tin địa điểm: " + message,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Override
    public void onBestMatchFound(AutocompletePrediction bestMatch, List<AutocompletePrediction> allPredictions) {
        currentSearchResults = allPredictions;

        if (customPlaceSearch != null) {
            customPlaceSearch.forceHideSuggestionsList();
        }

        if (multiPlaceDetailsHelper != null) {
            if (mapMarkersManager != null) {
                mapMarkersManager.clearPlaces();
            }

            List<AutocompletePrediction> singlePrediction = Arrays.asList(bestMatch);
            multiPlaceDetailsHelper.fetchMultiplePlaceDetails(singlePrediction,
                    new MultiPlaceDetailsHelper.MultiPlaceDetailsCallback() {
                        @Override
                        public void onPlacesDetailsFetched(List<Place> places) {
                            if (!places.isEmpty()) {
                                Place bestPlace = places.get(0);

                                mapMarkersManager.addPlaces(places);

                                if (bestPlace.getLatLng() != null) {
                                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(bestPlace.getLatLng(), 16));
                                }

                                if (placeInfoCard != null) {
                                    placeInfoCard.showPlaceInfo(bestPlace);
                                }
                            }
                        }

                        @Override
                        public void onError(String message) {
                            onPlacesSearchResult(allPredictions);
                            Toast.makeText(MapsActivity_New2.this,
                                    "Không thể lấy thông tin chi tiết, hiển thị tất cả kết quả",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Override
    public void onNoResults() {
        if (mapMarkersManager != null) {
            mapMarkersManager.clearPlaces();
        }

        Toast.makeText(this, "Không tìm thấy kết quả nào", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onError(String errorMessage) {
        Toast.makeText(this, "Lỗi tìm kiếm: " + errorMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSearchCleared() {
        if (mapMarkersManager != null) {
            mapMarkersManager.clearPlaces();
        }
        currentSearchResults = null;
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        String placeId = (String) marker.getTag();
        if (placeId != null && mapMarkersManager != null) {
            mapMarkersManager.highlightPlace(placeId);

            Place place = mapMarkersManager.getPlace(placeId);
            if (place != null && placeInfoCard != null) {
                placeInfoCard.showPlaceInfo(place);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onGetDirectionsClicked(AutocompletePrediction place) {
        if (placeDetailsHelper != null) {
            placeDetailsHelper.fetchPlaceDetails(place.getPlaceId(), new PlaceDetailsHelper.PlaceDetailsCallback() {
                @Override
                public void onPlaceDetailsFetched(Place placeDetails) {
                    LatLng destination = placeDetails.getLatLng();
                    if (destination != null) {
                        Uri gmmIntentUri;
                        if (currentLocation != null) {
                            gmmIntentUri = Uri.parse(String.format("google.navigation:q=%f,%f&mode=d",
                                    destination.latitude, destination.longitude));
                        } else {
                            gmmIntentUri = Uri.parse(String.format("geo:%f,%f?q=%s",
                                    destination.latitude, destination.longitude,
                                    placeDetails.getName()));
                        }

                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                        mapIntent.setPackage("com.google.android.apps.maps");

                        if (mapIntent.resolveActivity(getPackageManager()) != null) {
                            startActivity(mapIntent);
                        } else {
                            Toast.makeText(MapsActivity_New2.this,
                                    "Google Maps chưa được cài đặt",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MapsActivity_New2.this,
                            "Lỗi lấy thông tin địa điểm: " + message,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void getDirections(LatLng origin, LatLng destination) {
        if (directionsService == null) {
            Toast.makeText(this, "Dịch vụ chỉ đường chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Đang tìm tuyến đường...", Toast.LENGTH_SHORT).show();

        directionsService.getDirections(origin, destination, new DirectionsService.DirectionsCallback() {
            @Override
            public void onDirectionsReceived(List<DirectionsService.Route> routes) {
                runOnUiThread(() -> {
                    if (routes != null && !routes.isEmpty()) {
                        displayRoutes(routes, origin, destination);
                    } else {
                        Toast.makeText(MapsActivity_New2.this,
                                "Không tìm thấy tuyến đường",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Directions error: " + error);
                    Toast.makeText(MapsActivity_New2.this,
                            "Lỗi khi tìm tuyến đường: " + error,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void displayRoutes(List<DirectionsService.Route> routes, LatLng origin, LatLng destination) {
        if (routeManager == null || compactRoutePanel == null) {
            return;
        }

        routeManager.displayRoutes(routes, origin, destination);
        compactRoutePanel.showRoutes(routes, origin, destination);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Quyền truy cập vị trí bị từ chối", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDirectionsClicked(Place place) {
        LatLng destination = place.getLatLng();
        if (destination != null) {
            setLastSelectedDestination(destination);

            if (currentLocation != null) {
                getDirections(currentLocation, destination);

                if (placeInfoCard != null) {
                    placeInfoCard.hide();
                }
            } else {
                Toast.makeText(this, "Đang xác định vị trí của bạn...", Toast.LENGTH_SHORT).show();

                getDeviceLocation(location -> {
                    if (location != null) {
                        currentLocation = location;
                        getDirections(currentLocation, destination);

                        if (placeInfoCard != null) {
                            placeInfoCard.hide();
                        }
                    } else {
                        Toast.makeText(this,
                                "Không thể xác định vị trí hiện tại. Vui lòng bật GPS hoặc chọn điểm xuất phát khác.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        } else {
            Toast.makeText(this, "Không thể xác định vị trí địa điểm", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCallClicked(Place place) {
        if (place.getPhoneNumber() != null && !place.getPhoneNumber().isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.setData(Uri.parse("tel:" + place.getPhoneNumber()));
            if (callIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(callIntent);
            } else {
                Toast.makeText(MapsActivity_New2.this, "Không thể thực hiện cuộc gọi", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(MapsActivity_New2.this, "Không có số điện thoại", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCardClosed() {
        if (mapMarkersManager != null) {
            mapMarkersManager.clearHighlight();
        }
        if (routeManager != null) {
            routeManager.clearRoutes();
        }
    }

    @Override
    public void onRouteSelected(int routeIndex) {
        if (routeManager != null) {
            routeManager.selectRoute(routeIndex);
        }
    }

    @Override
    public void onTravelModeChanged(String travelMode) {
        if (currentLocation != null) {
            LatLng destination = getLastSelectedDestination();
            if (destination != null) {
                getDirectionsForTravelMode(currentLocation, destination, travelMode);
            }
        }
    }

    @Override
    public void onNavigationStarted(DirectionsService.Route route) {
        Log.d(TAG, "Starting in-app navigation");

        if (route != null && route.getPoints() != null && !route.getPoints().isEmpty()) {
            if (routeManager != null) {
                routeManager.clearRoutes();
            }
            if (customPlaceSearch != null) {
                customPlaceSearch.hideSuggestionsListPublic();
            }
            if (placeInfoCard != null) {
                placeInfoCard.hide();
            }
            if (compactRoutePanel != null) {
                compactRoutePanel.hide();
            }
            if (inAppNavigationManager != null) {
                if (currentLocation != null) {
                    inAppNavigationManager.stopNavigation();
                    inAppNavigationManager.startNavigation(route, currentLocation);
                    Toast.makeText(this, "Bắt đầu chỉ đường", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Đang xác định vị trí hiện tại...", Toast.LENGTH_SHORT).show();
                    getDeviceLocation(location -> {
                        if (location != null) {
                            currentLocation = location;
                            inAppNavigationManager.stopNavigation();
                            inAppNavigationManager.startNavigation(route, currentLocation);
                            Toast.makeText(this, "Bắt đầu chỉ đường", Toast.LENGTH_SHORT).show();
                        } else {
                            LatLng fallbackLocation = route.getPoints().get(0);
                            Toast.makeText(this, "Không thể xác định vị trí chính xác, sử dụng vị trí gần nhất", Toast.LENGTH_SHORT).show();
                            inAppNavigationManager.stopNavigation();
                            inAppNavigationManager.startNavigation(route, fallbackLocation);
                        }
                    });
                }
            } else {
                Log.e(TAG, "InAppNavigationManager_New not initialized");
                Toast.makeText(this, "Lỗi: Dịch vụ chỉ đường chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e(TAG, "Route is null or empty");
            Toast.makeText(this, "Lỗi: Không có tuyến đường hợp lệ", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPanelClosed() {
        if (routeManager != null) {
            routeManager.clearRoutes();
        }
    }

    public void clearDirections() {
        if (routeManager != null) {
            routeManager.clearRoutes();
        }
        if (compactRoutePanel != null) {
            compactRoutePanel.hide();
        }
    }

    private void setupTouchOutsideListener() {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setOnTouchListener((v, event) -> {
                if (customPlaceSearch != null) {
                    View searchCard = findViewById(R.id.custom_place_search);
                    if (searchCard != null && !isTouchInsideView(searchCard, event)) {
                        customPlaceSearch.hideSuggestionsListPublic();
                    }
                }
                return false;
            });
        }
    }

    private boolean isTouchInsideView(View view, MotionEvent event) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        int width = view.getWidth();
        int height = view.getHeight();

        float touchX = event.getRawX();
        float touchY = event.getRawY();

        return touchX >= x && touchX <= (x + width) && touchY >= y && touchY <= (y + height);
    }

    private void setLastSelectedDestination(LatLng destination) {
        this.lastSelectedDestination = destination;
    }

    private LatLng getLastSelectedDestination() {
        return this.lastSelectedDestination;
    }

    private void getDirectionsForTravelMode(LatLng origin, LatLng destination, String travelMode) {
        if (directionsService == null) {
            Toast.makeText(this, "Dịch vụ chỉ đường chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Đang tìm tuyến đường...", Toast.LENGTH_SHORT).show();

        directionsService.getDirections(origin, destination, travelMode, true, new DirectionsService.DirectionsCallback() {
            @Override
            public void onDirectionsReceived(List<DirectionsService.Route> routes) {
                runOnUiThread(() -> {
                    if (routes != null && !routes.isEmpty()) {
                        displayRoutes(routes, origin, destination);
                    } else {
                        Toast.makeText(MapsActivity_New2.this,
                                "Không tìm thấy tuyến đường cho phương tiện này",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Directions error: " + error);
                    Toast.makeText(MapsActivity_New2.this,
                            "Lỗi khi tìm tuyến đường: " + error,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showMapSettingsDialog() {
        if (mMap == null) return;

        SharedPreferences prefs = getSharedPreferences("map_prefs", MODE_PRIVATE);
        MapSettingsDialog_New dialog = new MapSettingsDialog_New(this, mMap, prefs);
        dialog.show();
    }
}
</create_file>
