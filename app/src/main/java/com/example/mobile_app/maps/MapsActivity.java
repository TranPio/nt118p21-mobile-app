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

// Import các components khác
import java.util.Arrays;
import java.util.List;
import com.example.mobile_app.data.MapRepositoryImpl;
import com.google.android.gms.maps.model.LatLng;
// Inline MapSettings implementation instead of separate class

import java.util.Arrays;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        OptimizedPlaceAutocompleteAdapter.PlaceAutocompleteAdapterListener,
        OptimizedPlaceSearchView.OnPlaceSelectedListener,
        OptimizedPlaceSearchView.OnPlacesSearchResultListener,
        GoogleMap.OnMarkerClickListener,
        PlaceInfoCard.PlaceInfoCardListener,
        CompactRoutePanel.CompactRoutePanelListener,
        RouteManager.RouteSelectionListener,
        InAppNavigationManager.NavigationListener {    private static final String TAG = "MapsActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GoogleMap mMap;
    private MapController mapController;
    private MapViewModel viewModel;
    private ActivityMapsBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesClient placesClient;    private FloatingActionButton fabMyLocation;
    private FloatingActionButton fabMapSettings;
    private InAppNavigationManager inAppNavigationManager;
    // MapSettings dialog will be created inline    // UI mới
    private OptimizedPlaceSearchView customPlaceSearch;

    // Biến lưu trữ vị trí hiện tại
    private LatLng currentLocation;

    // Helper để tính khoảng cách và lấy thông tin chi tiết địa điểm
    private PlaceDetailsHelper placeDetailsHelper;

    // Helper để lấy thông tin chi tiết nhiều địa điểm
    private MultiPlaceDetailsHelper multiPlaceDetailsHelper;

    // Manager quản lý các marker trên bản đồ
    private MapMarkersManager mapMarkersManager;

    // Giữ lại danh sách kết quả tìm kiếm hiện tại
    private List<AutocompletePrediction> currentSearchResults;

    // PlaceInfoCard để hiển thị thông tin chi tiết địa điểm
    private PlaceInfoCard placeInfoCard;

    // Directions và Route management
    private DirectionsService directionsService;
    private RouteManager routeManager;
    private CompactRoutePanel compactRoutePanel;

    // Biến lưu trữ destination cuối cùng được chọn
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

        // Khởi tạo FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Khởi tạo Places API
        if (!Places.isInitialized()) {
            String apiKey = getString(R.string.google_maps_key);
            Log.d(TAG, "Khởi tạo Places API với key");
            Places.initialize(getApplicationContext(), apiKey);
        }
        placesClient = Places.createClient(this);

        // Khởi tạo CustomPlaceSearchView
        customPlaceSearch = findViewById(R.id.custom_place_search);
        customPlaceSearch.setPlacesClient(placesClient);
        customPlaceSearch.setOnPlaceSelectedListener(this);
        customPlaceSearch.setOnPlacesSearchResultListener(this); // Đăng ký listener mới
        customPlaceSearch.setHint("Tìm kiếm địa điểm");

        // Thiết lập nút vị trí của tôi
        fabMyLocation = findViewById(R.id.fab_my_location);
        fabMyLocation.setOnClickListener(view -> getDeviceLocation());        fabMapSettings = findViewById(R.id.fab_map_settings);
        fabMapSettings.setOnClickListener(v -> {
            if (mMap != null) {
                showMapSettingsDialog();
            }
        });

        // Lấy SupportMapFragment và nhận thông báo khi bản đồ sẵn sàng để sử dụng
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        viewModel.getRoute().observe(this, points -> {
            if (mapController != null && points != null && points.size() > 1) {
                mapController.addMarker(points.get(0), "Start");
                mapController.addMarker(points.get(points.size()-1), "End");
            }
        });

        // Khởi tạo PlaceInfoCard
        initializePlaceInfoCard();

        // Khởi tạo Directions services
        initializeDirectionsServices();

        // Thiết lập touch listener cho root view để ẩn suggestions khi tap bên ngoài
        setupTouchOutsideListener();
    }

    /**
     * Khởi tạo PlaceInfoCard
     */
    private void initializePlaceInfoCard() {
        // Tạo PlaceInfoCard với PlaceDetailsHelper tạm thời (sẽ được cập nhật sau khi có location)
        placeInfoCard = new PlaceInfoCard(this, placesClient, null);

        // Thiết lập container cho PlaceInfoCard
        ViewGroup container = findViewById(R.id.place_info_container);
        placeInfoCard.setParentContainer(container);

        // Thiết lập listener
        placeInfoCard.setListener(this);
    }

    /**
     * Khởi tạo các services cho Directions
     */
    private void initializeDirectionsServices() {
        String apiKey = getString(R.string.google_maps_key);
        directionsService = new DirectionsService(apiKey);

        // Khởi tạo CompactRoutePanel
        compactRoutePanel = new CompactRoutePanel(this);
        ViewGroup routeContainer = findViewById(R.id.compact_route_panel_container);
        compactRoutePanel.setParentContainer(routeContainer);
        compactRoutePanel.setListener(this);
    }

    /**
     * Xử lý bản đồ khi sẵn sàng.
     * Callback này được kích hoạt khi bản đồ đã sẵn sàng để sử dụng.
     */    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mapController = new MapController(googleMap);

        SharedPreferences prefs = getSharedPreferences("map_prefs", MODE_PRIVATE);
        int type = prefs.getInt("map_type", GoogleMap.MAP_TYPE_NORMAL);
        boolean traffic = prefs.getBoolean("traffic", false);
        mMap.setMapType(type);
        mMap.setTrafficEnabled(traffic);

        // Bật nút Vị trí của tôi (nếu đã được cấp quyền)
        enableMyLocation();

        // Khởi tạo InAppNavigationManager với callback listener
        inAppNavigationManager = new InAppNavigationManager(this, mMap);
        inAppNavigationManager.setNavigationListener(this);

        // Khởi tạo MapMarkersManager
        mapMarkersManager = new MapMarkersManager(this, mMap);

        // Khởi tạo RouteManager
        routeManager = new RouteManager(this, mMap);
        routeManager.setRouteSelectionListener(this);

        // Thiết lập OnMapClickListener để ẩn gợi ý khi click vào map
        mMap.setOnMapClickListener(latLng -> {
            // Ẩn danh sách gợi ý khi người dùng click vào bản đồ
            if (customPlaceSearch != null) {
                customPlaceSearch.hideSuggestionsListPublic();
            }

            // Ẩn Place Info Card nếu đang hiển thị
            if (placeInfoCard != null) {
                placeInfoCard.hide();
            }

            // Ẩn Compact Route Panel nếu đang hiển thị
            if (compactRoutePanel != null) {
                compactRoutePanel.hide();
            }

            // Xóa highlight của các marker
            if (mapMarkersManager != null) {
                mapMarkersManager.clearHighlight();
            }

            // Xóa routes và ẩn CompactRoutePanel
            clearDirections();
        });

        // Thiết lập listener khi click vào marker
        mMap.setOnMarkerClickListener(this);        // Vị trí mặc định: Thành phố Hồ Chí Minh
        LatLng hoChiMinhCity = new LatLng(10.762622, 106.660172);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(hoChiMinhCity, 12));
    }

    /**
     * Bật lớp Vị trí của tôi nếu được cấp quyền vị trí chính xác.
     */
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
                // Lấy vị trí hiện tại của người dùng khi bản đồ tải
                getDeviceLocation();
            }
        } else {
            // Quyền truy cập vị trí bị thiếu. Yêu cầu nó.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }    /**
     * Lấy vị trí hiện tại của thiết bị và định vị camera của bản đồ.
     */
    // Interface for location callback
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
                                // Convert Android Location to Google Maps LatLng
                                currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                                // Khởi tạo PlaceDetailsHelper với vị trí hiện tại
                                placeDetailsHelper = new PlaceDetailsHelper(this, placesClient, currentLocation);

                                // Khởi tạo MultiPlaceDetailsHelper với vị trí hiện tại
                                multiPlaceDetailsHelper = new MultiPlaceDetailsHelper(this, placesClient, currentLocation);

                                // Cập nhật PlaceInfoCard với PlaceDetailsHelper mới
                                if (placeInfoCard != null) {
                                    placeInfoCard = new PlaceInfoCard(this, placesClient, placeDetailsHelper);
                                    ViewGroup container = findViewById(R.id.place_info_container);
                                    placeInfoCard.setParentContainer(container);
                                    placeInfoCard.setListener(this);
                                }

                                // Cập nhật giao diện tìm kiếm với vị trí mới
                                setupImprovedPlaceAutocomplete();

                                // Di chuyển camera đến vị trí hiện tại
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                        currentLocation, 15));
                                
                                // Callback nếu được cung cấp
                                if (callback != null) {
                                    callback.onLocationResult(currentLocation);
                                }
                            } else {
                                Log.e(TAG, "Location là null, không thể lấy vị trí hiện tại");
                                // Sử dụng vị trí mặc định nếu không có vị trí hiện tại
                                useDefaultLocation();
                                if (callback != null) {
                                    callback.onLocationResult(currentLocation);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Lỗi lấy vị trí: " + e.getMessage());
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
            Log.e(TAG, "Ngoại lệ khi lấy vị trí thiết bị: " + e.getMessage());
            useDefaultLocation();
            if (callback != null) {
                callback.onLocationResult(currentLocation);
            }
        }
    }
    
    private void useDefaultLocation() {
        // Vị trí mặc định - Trung tâm TP.HCM
        currentLocation = new LatLng(10.7756587, 106.7004238);
        
        // Khởi tạo các helper với vị trí mặc định
        placeDetailsHelper = new PlaceDetailsHelper(this, placesClient, currentLocation);
        multiPlaceDetailsHelper = new MultiPlaceDetailsHelper(this, placesClient, currentLocation);
        
        // Cập nhật PlaceInfoCard
        if (placeInfoCard != null) {
            placeInfoCard = new PlaceInfoCard(this, placesClient, placeDetailsHelper);
            ViewGroup container = findViewById(R.id.place_info_container);
            placeInfoCard.setParentContainer(container);
            placeInfoCard.setListener(this);
        }
        
        // Cập nhật giao diện tìm kiếm
        setupImprovedPlaceAutocomplete();
    }
    
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }
    
    /**
     * Thiết lập giao diện tìm kiếm cải tiến với hiển thị khoảng cách và nút chỉ đường
     */    private void setupImprovedPlaceAutocomplete() {
        // Cập nhật CustomPlaceSearchView với vị trí hiện tại
        if (customPlaceSearch != null) {
            customPlaceSearch.setCurrentLocation(currentLocation);
        }
    }

    /**
     * Xử lý khi người dùng chọn một địa điểm từ kết quả tìm kiếm
     */
    @Override
    public void onPlaceSelected(AutocompletePrediction prediction) {
        Log.d(TAG, "===== PLACE SELECTED DEBUG =====");
        Log.d(TAG, "onPlaceSelected called with: " + prediction.getPrimaryText(null));
        Log.d(TAG, "Place ID: " + prediction.getPlaceId());
        Log.d(TAG, "placeDetailsHelper is: " + (placeDetailsHelper != null ? "not null" : "null"));

        // Lấy thông tin chi tiết về địa điểm đã chọn
        if (placeDetailsHelper != null) {
            Log.d(TAG, "🚀 Fetching place details for: " + prediction.getPlaceId());
            placeDetailsHelper.fetchPlaceDetails(prediction.getPlaceId(), new PlaceDetailsHelper.PlaceDetailsCallback() {                @Override
            public void onPlaceDetailsFetched(Place place) {
                Log.d(TAG, "✅ Place details fetched successfully");
                Log.d(TAG, "Place name: " + (place.getName() != null ? place.getName() : "null"));
                Log.d(TAG, "Place location: " + (place.getLatLng() != null ? place.getLatLng().toString() : "null"));

                LatLng latLng = place.getLatLng();
                if (latLng != null) {
                    Log.d(TAG, "Processing place location: " + latLng.toString());

                    // Xóa các marker hiện tại
                    if (mapMarkersManager != null) {
                        Log.d(TAG, "Clearing existing markers");
                        mapMarkersManager.clearPlaces();
                    }

                    // Thêm marker mới và làm nổi bật
                    if (mapMarkersManager != null) {
                        Log.d(TAG, "Adding new marker for place");
                        mapMarkersManager.addPlace(place);
                        mapMarkersManager.highlightPlace(place.getId());
                    }

                    // Hiển thị thẻ thông tin chi tiết
                    if (placeInfoCard != null) {
                        Log.d(TAG, "🎯 Showing Place Info Card");
                        placeInfoCard.showPlaceInfo(place);
                    } else {
                        Log.e(TAG, "❌ placeInfoCard is null!");
                    }

                    // Ẩn danh sách gợi ý tìm kiếm sau khi chọn
                    if (customPlaceSearch != null) {
                        Log.d(TAG, "Hiding suggestions list");
                        customPlaceSearch.hideSuggestionsListPublic();
                    }

                    // Di chuyển camera đến địa điểm
                    Log.d(TAG, "Moving camera to place location");
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

                    Log.d(TAG, "✅ Place selection processing completed successfully");
                } else {
                    Log.e(TAG, "❌ Place location is null");
                }
                Log.d(TAG, "===== END PLACE SELECTED DEBUG =====");
            }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "❌ Error fetching place details: " + message);
                    Toast.makeText(MapsActivity.this,
                            "Lỗi lấy thông tin địa điểm: " + message,
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "===== END PLACE SELECTED DEBUG (ERROR) =====");
                }
            });
        } else {
            Log.e(TAG, "❌ placeDetailsHelper is null, cannot fetch place details");
            Toast.makeText(this, "Lỗi: Không thể lấy thông tin địa điểm", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "===== END PLACE SELECTED DEBUG (NO HELPER) =====");
        }
    }

    /**
     * Xử lý khi có kết quả tìm kiếm địa điểm
     */
    @Override
    public void onPlacesSearchResult(List<AutocompletePrediction> predictions) {
        // Lưu lại kết quả tìm kiếm hiện tại
        currentSearchResults = predictions;

        // Lấy thông tin chi tiết về tất cả các địa điểm trong kết quả tìm kiếm
        if (multiPlaceDetailsHelper != null) {
            // Xóa các marker hiện tại
            if (mapMarkersManager != null) {
                mapMarkersManager.clearPlaces();
            }

            // Lấy thông tin chi tiết của tối đa 5 địa điểm đầu tiên để tránh gọi API quá nhiều
            int maxResults = Math.min(predictions.size(), 5);
            List<AutocompletePrediction> topPredictions = predictions.subList(0, maxResults);

            multiPlaceDetailsHelper.fetchMultiplePlaceDetails(topPredictions,
                    new MultiPlaceDetailsHelper.MultiPlaceDetailsCallback() {
                        @Override
                        public void onPlacesDetailsFetched(List<Place> places) {
                            // Thêm các marker cho tất cả các địa điểm
                            if (mapMarkersManager != null && !places.isEmpty()) {
                                mapMarkersManager.addPlaces(places);

                                // Tự động hiển thị Place Info Card cho địa điểm đầu tiên nếu chỉ có 1 kết quả
                                if (places.size() == 1 && placeInfoCard != null) {
                                    Place firstPlace = places.get(0);
                                    placeInfoCard.showPlaceInfo(firstPlace);

                                    // Di chuyển camera đến địa điểm đầu tiên
                                    if (firstPlace.getLatLng() != null) {
                                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstPlace.getLatLng(), 15));
                                    }
                                }

                                // KHÔNG hiển thị suggestions sau khi search - để bản đồ rõ ràng
                                // Đã bỏ: customPlaceSearch.showSuggestionsListPublic();
                            }
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(MapsActivity.this,
                                    "Lỗi lấy thông tin địa điểm: " + message,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    /**
     * Xử lý khi tìm thấy kết quả best match - auto zoom và hiển thị place info card
     */
    @Override
    public void onBestMatchFound(AutocompletePrediction bestMatch, List<AutocompletePrediction> allPredictions) {
        // Lưu lại tất cả kết quả để backup
        currentSearchResults = allPredictions;

        // Đảm bảo suggestions list bị ẩn hoàn toàn
        if (customPlaceSearch != null) {
            customPlaceSearch.forceHideSuggestionsList();
        }

        // Lấy thông tin chi tiết về best match place
        if (multiPlaceDetailsHelper != null) {
            // Clear existing markers
            if (mapMarkersManager != null) {
                mapMarkersManager.clearPlaces();
            }

            // Fetch details cho best match
            List<AutocompletePrediction> singlePrediction = Arrays.asList(bestMatch);
            multiPlaceDetailsHelper.fetchMultiplePlaceDetails(singlePrediction,
                    new MultiPlaceDetailsHelper.MultiPlaceDetailsCallback() {
                        @Override
                        public void onPlacesDetailsFetched(List<Place> places) {
                            if (!places.isEmpty() && mapMarkersManager != null) {
                                Place bestPlace = places.get(0);

                                // Add marker cho best match
                                mapMarkersManager.addPlaces(places);

                                // Auto zoom vào địa điểm này
                                if (bestPlace.getLatLng() != null) {
                                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(bestPlace.getLatLng(), 16));
                                }

                                // Tự động hiển thị Place Info Card
                                if (placeInfoCard != null) {
                                    placeInfoCard.showPlaceInfo(bestPlace);
                                }
                            }
                        }

                        @Override
                        public void onError(String message) {
                            // Fallback to normal search result
                            onPlacesSearchResult(allPredictions);
                            Toast.makeText(MapsActivity.this,
                                    "Không thể lấy thông tin chi tiết, hiển thị tất cả kết quả",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    /**
     * Xử lý khi không có kết quả tìm kiếm
     */
    @Override
    public void onNoResults() {
        // Xóa các marker hiện tại
        if (mapMarkersManager != null) {
            mapMarkersManager.clearPlaces();
        }

        Toast.makeText(this, "Không tìm thấy kết quả nào", Toast.LENGTH_SHORT).show();
    }

    /**
     * Xử lý khi có lỗi trong quá trình tìm kiếm
     */
    @Override
    public void onError(String errorMessage) {
        Toast.makeText(this, "Lỗi tìm kiếm: " + errorMessage, Toast.LENGTH_SHORT).show();
    }

    /**
     * Xử lý khi người dùng xóa tìm kiếm
     */
    @Override
    public void onSearchCleared() {
        // Xóa các marker hiện tại
        if (mapMarkersManager != null) {
            mapMarkersManager.clearPlaces();
        }

        // Xóa danh sách kết quả tìm kiếm hiện tại
        currentSearchResults = null;
    }

    /**
     * Xử lý khi người dùng nhấp vào marker trên bản đồ
     */
    @Override
    public boolean onMarkerClick(Marker marker) {
        // Lấy placeId từ tag của marker
        String placeId = (String) marker.getTag();
        if (placeId != null && mapMarkersManager != null) {
            // Làm nổi bật địa điểm được chọn
            mapMarkersManager.highlightPlace(placeId);

            // Hiển thị thông tin địa điểm
            Place place = mapMarkersManager.getPlace(placeId);
            if (place != null && placeInfoCard != null) {
                // Hiển thị thẻ thông tin chi tiết
                placeInfoCard.showPlaceInfo(place);
            }
            return true;
        }
        return false;
    }

    /**
     * Xử lý khi người dùng nhấp vào nút chỉ đường
     */
    @Override
    public void onGetDirectionsClicked(AutocompletePrediction place) {
        // Lấy thông tin chi tiết về địa điểm
        if (placeDetailsHelper != null) {
            placeDetailsHelper.fetchPlaceDetails(place.getPlaceId(), new PlaceDetailsHelper.PlaceDetailsCallback() {                @Override
            public void onPlaceDetailsFetched(Place placeDetails) {
                LatLng destination = placeDetails.getLatLng();
                if (destination != null) {
                    // Mở Google Maps để chỉ đường từ vị trí hiện tại đến địa điểm
                    Uri gmmIntentUri;
                    if (currentLocation != null) {
                        // Sử dụng vị trí hiện tại làm điểm bắt đầu
                        gmmIntentUri = Uri.parse(String.format("google.navigation:q=%f,%f&mode=d",
                                destination.latitude, destination.longitude));
                    } else {
                        // Nếu không có vị trí hiện tại, chỉ hiển thị địa điểm trên Google Maps
                        gmmIntentUri = Uri.parse(String.format("geo:%f,%f?q=%s",
                                destination.latitude, destination.longitude,
                                placeDetails.getName()));
                    }

                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");

                    if (mapIntent.resolveActivity(getPackageManager()) != null) {
                        startActivity(mapIntent);
                    } else {
                        Toast.makeText(MapsActivity.this,
                                "Google Maps chưa được cài đặt",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }

                @Override
                public void onError(String message) {
                    Toast.makeText(MapsActivity.this,
                            "Lỗi lấy thông tin địa điểm: " + message,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Lấy chỉ đường từ điểm bắt đầu đến điểm đích
     */
    private void getDirections(LatLng origin, LatLng destination) {
        if (directionsService == null) {
            Toast.makeText(this, "Dịch vụ chỉ đường chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hiển thị thông báo đang tải
        Toast.makeText(this, "Đang tìm tuyến đường...", Toast.LENGTH_SHORT).show();

        // No need to convert - use Google Maps LatLng directly with DirectionsService
        directionsService.getDirections(origin, destination, new DirectionsService.DirectionsCallback() {
            @Override
            public void onDirectionsReceived(List<DirectionsService.Route> routes) {
                runOnUiThread(() -> {
                    if (routes != null && !routes.isEmpty()) {
                        displayRoutes(routes, origin, destination);
                    } else {
                        Toast.makeText(MapsActivity.this,
                                "Không tìm thấy tuyến đường",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Directions error: " + error);
                    Toast.makeText(MapsActivity.this,
                            "Lỗi khi tìm tuyến đường: " + error,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Hiển thị các tuyến đường trên bản đồ
     */
    private void displayRoutes(List<DirectionsService.Route> routes, LatLng origin, LatLng destination) {
        if (routeManager == null || compactRoutePanel == null) {
            return;
        }

        // Hiển thị routes trên bản đồ với nhãn thời gian
        routeManager.displayRoutes(routes, origin, destination);

        // Hiển thị Compact Route Panel thay vì RouteSummaryCard lớn
        compactRoutePanel.showRoutes(routes, origin, destination);
    }

    /**
     * Xử lý kết quả của yêu cầu quyền vị trí.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // Nếu yêu cầu bị hủy, mảng kết quả sẽ trống.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Quyền truy cập vị trí bị từ chối", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Implement PlaceInfoCardListener methods    @Override
    public void onDirectionsClicked(Place place) {
        LatLng destination = place.getLatLng();
        if (destination != null) {
            // Save the selected destination for reuse with different travel modes
            setLastSelectedDestination(destination);

            if (currentLocation != null) {
                // Proceed with directions using internal system
                getDirections(currentLocation, destination);

                // Hide PlaceInfoCard to show CompactRoutePanel
                if (placeInfoCard != null) {
                    placeInfoCard.hide();
                }
            } else {
                // Try to get the current location if not available
                Toast.makeText(this, "Đang xác định vị trí của bạn...", Toast.LENGTH_SHORT).show();

                getDeviceLocation(location -> {
                    if (location != null) {
                        // Update currentLocation and proceed with directions
                        currentLocation = location; // location is already a LatLng
                        getDirections(currentLocation, destination);

                        // Hide PlaceInfoCard to show CompactRoutePanel
                        if (placeInfoCard != null) {
                            placeInfoCard.hide();
                        }
                    } else {
                        // Fallback to a general area or offer alternative
                        Toast.makeText(this,
                                "Không thể xác định vị trí hiện tại. Vui lòng bật GPS hoặc chọn điểm xuất phát khác.",
                                Toast.LENGTH_LONG).show();

                        // Potentially show a location selection dialog here in the future
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
                Toast.makeText(this, "Không thể thực hiện cuộc gọi", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Không có số điện thoại", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCardClosed() {
        // Có thể thực hiện cleanup hoặc các hành động khác khi card được đóng
        if (mapMarkersManager != null) {
            mapMarkersManager.clearHighlight();
        }

        // Xóa routes khỏi bản đồ khi đóng route summary card
        if (routeManager != null) {
            routeManager.clearRoutes();
        }
    }

    // CompactRoutePanel.CompactRoutePanelListener methods
    @Override
    public void onRouteSelected(int routeIndex) {
        if (routeManager != null) {
            routeManager.selectRoute(routeIndex);
        }
    }

    @Override
    public void onTravelModeChanged(String travelMode) {
        // Khi người dùng thay đổi travel mode, tính toán lại routes
        if (currentLocation != null) {
            // Tìm destination từ PlaceInfoCard hiện tại hoặc từ marker đã chọn
            LatLng destination = getLastSelectedDestination();
            if (destination != null) {
                getDirectionsForTravelMode(currentLocation, destination, travelMode);
            }
        }
    }    @Override
    public void onNavigationStarted(DirectionsService.Route route) {
        Log.d(TAG, "🚀 Starting in-app navigation instead of external Google Maps");

        if (route != null && route.getPoints() != null && !route.getPoints().isEmpty()) {
            if (routeManager != null) {
                routeManager.clearRoutes();
            }
            // Ensure UI is properly set up for navigation mode
            if (customPlaceSearch != null) {
                customPlaceSearch.hideSuggestionsListPublic();
            }

            // Make sure place info card is hidden
            if (placeInfoCard != null) {
                placeInfoCard.hide();
            }

            // Hide compact route panel
            if (compactRoutePanel != null) {
                compactRoutePanel.hide();
            }

            // Use InAppNavigationManager to begin navigation
            if (inAppNavigationManager != null) {
                // We need current location to start navigation
                if (currentLocation != null) {
                    // Clear any previous navigation state
                    inAppNavigationManager.stopNavigation();

                    // Start new navigation with the selected route
                    inAppNavigationManager.startNavigation(route, currentLocation);

                    // Inform user
                    Toast.makeText(this, "Bắt đầu chỉ đường", Toast.LENGTH_SHORT).show();
                } else {
                    // Try to get current location one more time with a progress dialog
                    Toast.makeText(this, "Đang xác định vị trí hiện tại...", Toast.LENGTH_SHORT).show();                    getDeviceLocation(location -> {
                        if (location != null) {
                            currentLocation = location; // location is already a LatLng

                            // Now start navigation with the updated location
                            inAppNavigationManager.stopNavigation();
                            inAppNavigationManager.startNavigation(route, currentLocation);

                            Toast.makeText(this, "Bắt đầu chỉ đường", Toast.LENGTH_SHORT).show();
                        } else {
                            // If we still can't get location, use the first point of the route as a fallback
                            LatLng fallbackLocation = route.getPoints().get(0);
                            Toast.makeText(this, "Không thể xác định vị trí chính xác, sử dụng vị trí gần nhất", Toast.LENGTH_SHORT).show();

                            inAppNavigationManager.stopNavigation();
                            inAppNavigationManager.startNavigation(route, fallbackLocation);
                        }
                    });
                }
            } else {
                Log.e(TAG, "❌ InAppNavigationManager chưa được khởi tạo");
                Toast.makeText(this, "Lỗi: Dịch vụ chỉ đường chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e(TAG, "❌ Route is null or empty");
            Toast.makeText(this, "Lỗi: Không có tuyến đường hợp lệ", Toast.LENGTH_SHORT).show();
        }
    }

@Override
public void onPanelClosed() {
    // Ẩn panel và xóa routes khỏi bản đồ
    if (routeManager != null) {
        routeManager.clearRoutes();
    }
}

// RouteManager.RouteSelectionListener methods
@Override
public void onRouteSelected(DirectionsService.Route route, int index) {
    // Cập nhật CompactRoutePanel khi route được chọn
    if (compactRoutePanel != null) {
        compactRoutePanel.selectRoute(index);
    }
}

/**
 * Xóa tất cả routes và ẩn CompactRoutePanel
 */
public void clearDirections() {
    if (routeManager != null) {
        routeManager.clearRoutes();
    }
    if (compactRoutePanel != null) {
        compactRoutePanel.hide();
    }
}

/**
 * Thiết lập touch listener để ẩn suggestions khi tap bên ngoài
 */
private void setupTouchOutsideListener() {
    View rootView = findViewById(android.R.id.content);
    if (rootView != null) {
        rootView.setOnTouchListener((v, event) -> {
            if (customPlaceSearch != null) {
                // Kiểm tra nếu touch không phải trong search view
                View searchCard = findViewById(R.id.custom_place_search);
                if (searchCard != null && !isTouchInsideView(searchCard, event)) {
                    customPlaceSearch.hideSuggestionsListPublic();
                }
            }
            return false;
        });
    }
}

/**
 * Kiểm tra xem touch event có nằm trong view không
 */
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

/**
 * Lưu destination cuối cùng được chọn
 */
private void setLastSelectedDestination(LatLng destination) {
    this.lastSelectedDestination = destination;
}

/**
 * Lấy destination cuối cùng được chọn
 */
private LatLng getLastSelectedDestination() {
    return this.lastSelectedDestination;
}

/**
 * Lấy chỉ đường cho travel mode cụ thể
 */
private void getDirectionsForTravelMode(LatLng origin, LatLng destination, String travelMode) {
    if (directionsService == null) {
        Toast.makeText(this, "Dịch vụ chỉ đường chưa sẵn sàng", Toast.LENGTH_SHORT).show();
        return;
    }

    // Hiển thị thông báo đang tải
    Toast.makeText(this, "Đang tìm tuyến đường...", Toast.LENGTH_SHORT).show();

    // Gọi DirectionsService với travel mode cụ thể
    directionsService.getDirections(origin, destination, travelMode, true, new DirectionsService.DirectionsCallback() {
        @Override
        public void onDirectionsReceived(List<DirectionsService.Route> routes) {
            runOnUiThread(() -> {
                if (routes != null && !routes.isEmpty()) {
                    displayRoutes(routes, origin, destination);
                } else {
                    Toast.makeText(MapsActivity.this,
                            "Không tìm thấy tuyến đường cho phương tiện này",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onError(String error) {
            runOnUiThread(() -> {
                Log.e(TAG, "Directions error: " + error);
                Toast.makeText(MapsActivity.this,
                        "Lỗi khi tìm tuyến đường: " + error,
                        Toast.LENGTH_LONG).show();
            });
        }
    });
}

/**
 * Display the map settings bottom sheet
 */
private void showMapSettingsDialog() {
    if (mMap == null) return;

    SharedPreferences prefs = getSharedPreferences("map_prefs", MODE_PRIVATE);
    MapSettingsDialog dialog = new MapSettingsDialog(this, mMap, prefs);
    dialog.show();
}

// ==================== NAVIGATION LISTENER IMPLEMENTATIONS ====================

@Override
public void onNavigationStopped() {
    Log.d(TAG, "⏹️ Navigation stopped");
    runOnUiThread(() -> {
        Toast.makeText(this, "Đã dừng chỉ đường", Toast.LENGTH_SHORT).show();    });
}

@Override
public void onInstructionChanged(String instruction, String distance) {
    Log.d(TAG, "🗣️ Instruction changed: " + instruction + " (" + distance + ")");
    runOnUiThread(() -> {
        // TODO: Update UI với instruction mới
        // Có thể hiển thị instruction trong notification hoặc overlay
    });
}

@Override
public void onDestinationReached() {
    Log.d(TAG, "🏁 Destination reached!");
    runOnUiThread(() -> {
        Toast.makeText(this, "Đã đến đích!", Toast.LENGTH_LONG).show();
    });
}
}
