package com.example.mobile_app.maps;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.mobile_app.R;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.List;

/**
 * View tùy chỉnh để xử lý và hiển thị tìm kiếm địa điểm với giao diện cải tiến
 * Cải thiện logic hiển thị danh sách gợi ý để đảm bảo trải nghiệm mượt mà
 */
public class CustomPlaceSearchView_Improved extends LinearLayout {

    private static final String TAG = "CustomPlaceSearchView";
    private static final long SEARCH_DELAY_MILLIS = 500; // Tăng delay để ổn định hơn

    private EditText searchInput;
    private ImageView clearSearch;
    private ListView placesList;
    private PlaceAutocompleteAdapter adapter;
    private PlacesClient placesClient;
    private LatLng currentLocation;
    private AutocompleteSessionToken sessionToken;
    private Runnable searchRunnable;
    private OnPlaceSelectedListener onPlaceSelectedListener;
    private OnPlacesSearchResultListener onPlacesSearchResultListener;
    
    // Flags để kiểm soát hiển thị gợi ý
    private boolean shouldKeepSuggestions = false;
    private boolean isUserTyping = false;
    private boolean hasSuggestions = false;

    public CustomPlaceSearchView_Improved(Context context) {
        super(context);
        init(context);
    }

    public CustomPlaceSearchView_Improved(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CustomPlaceSearchView_Improved(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Inflate layout
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.custom_place_search, this, true);

        // Khởi tạo views
        searchInput = findViewById(R.id.search_input);
        clearSearch = findViewById(R.id.clear_search);
        placesList = findViewById(R.id.places_list);

        // Khởi tạo adapter với danh sách trống
        adapter = new PlaceAutocompleteAdapter(context, new ArrayList<>(), null);
        placesList.setAdapter(adapter);

        // Khởi tạo session token
        sessionToken = AutocompleteSessionToken.newInstance();

        // Thiết lập listeners
        setupListeners();
    }

    /**
     * Thiết lập các listeners cho các thành phần UI
     */
    private void setupListeners() {
        // Text change listener với debounce để tránh gọi API quá nhiều
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isUserTyping = true;
                
                // Hiển thị/ẩn nút xóa
                clearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);

                // Xóa tìm kiếm đang chờ
                removeCallbacks(searchRunnable);

                // Nếu chuỗi tìm kiếm trống, ẩn kết quả và thông báo xóa
                if (s.length() == 0) {
                    hideSuggestionsList();
                    hasSuggestions = false;
                    if (onPlacesSearchResultListener != null) {
                        onPlacesSearchResultListener.onSearchCleared();
                    }
                    return;
                }

                // Nếu chuỗi tìm kiếm quá ngắn, ẩn kết quả nhưng không thông báo xóa
                if (s.length() < 2) {
                    hideSuggestionsList();
                    hasSuggestions = false;
                    return;
                }

                // Đặt lịch tìm kiếm mới với độ trễ
                searchRunnable = () -> {
                    if (isUserTyping) {
                        searchPlaces(s.toString());
                        isUserTyping = false;
                    }
                };
                postDelayed(searchRunnable, SEARCH_DELAY_MILLIS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Focus change listener để kiểm soát hiển thị gợi ý
        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                shouldKeepSuggestions = true;
                // Nếu có gợi ý và text đủ dài, hiển thị lại danh sách
                String currentText = searchInput.getText().toString().trim();
                if (currentText.length() >= 2 && hasSuggestions && adapter.getCount() > 0) {
                    // Delay nhỏ để đảm bảo gợi ý hiển thị sau khi focus
                    postDelayed(() -> showSuggestionsList(), 100);
                }
            } else {
                // Chỉ ẩn gợi ý khi mất focus và không có flag giữ gợi ý
                if (!shouldKeepSuggestions) {
                    hideSuggestionsList();
                }
            }
        });

        // Xử lý phím Enter để thực hiện tìm kiếm
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                
                String query = searchInput.getText().toString().trim();
                if (query.length() >= 2) {
                    // Ẩn gợi ý và bàn phím
                    hideSuggestionsList();
                    hideKeyboard();
                    shouldKeepSuggestions = false;
                    
                    // Thực hiện tìm kiếm trực tiếp
                    performDirectSearch(query);
                }
                return true;
            }
            return false;
        });

        // Click listener cho nút xóa
        clearSearch.setOnClickListener(v -> {
            searchInput.setText("");
            hideSuggestionsList();
            hideKeyboard();
            shouldKeepSuggestions = false;
            hasSuggestions = false;
            // Thông báo xóa tìm kiếm
            if (onPlacesSearchResultListener != null) {
                onPlacesSearchResultListener.onSearchCleared();
            }
        });

        // Item click listener cho danh sách kết quả
        placesList.setOnItemClickListener((parent, view, position, id) -> {
            AutocompletePrediction prediction = adapter.getItem(position);
            if (prediction != null && onPlaceSelectedListener != null) {
                // Cập nhật text input với tên địa điểm được chọn
                searchInput.setText(prediction.getPrimaryText(null));
                
                // Ẩn danh sách gợi ý ngay lập tức
                hideSuggestionsList();
                shouldKeepSuggestions = false;
                
                // Ẩn bàn phím
                hideKeyboard();
                
                // Thông báo về việc chọn địa điểm
                onPlaceSelectedListener.onPlaceSelected(prediction);
            }
        });
    }

    /**
     * Tìm kiếm địa điểm dựa trên văn bản nhập vào
     */
    private void searchPlaces(String query) {
        if (placesClient == null) {
            Log.e(TAG, "PlacesClient chưa được thiết lập");
            return;
        }

        FindAutocompletePredictionsRequest.Builder requestBuilder = FindAutocompletePredictionsRequest.builder()
                .setTypeFilter(TypeFilter.ESTABLISHMENT)
                .setSessionToken(sessionToken)
                .setQuery(query);

        // Thiết lập vùng tìm kiếm nếu có vị trí hiện tại
        if (currentLocation != null) {
            double latDelta = 0.5; // Khoảng 50km theo chiều dọc
            double lngDelta = 0.5; // Khoảng 50km theo chiều ngang

            RectangularBounds bounds = RectangularBounds.newInstance(
                    new LatLng(currentLocation.latitude - latDelta, currentLocation.longitude - lngDelta),
                    new LatLng(currentLocation.latitude + latDelta, currentLocation.longitude + lngDelta)
            );
            requestBuilder.setLocationBias(bounds);
        }

        placesClient.findAutocompletePredictions(requestBuilder.build())
                .addOnSuccessListener(response -> {
                    // Cập nhật adapter với kết quả mới
                    List<AutocompletePrediction> predictions = response.getAutocompletePredictions();
                    updateAdapter(predictions);

                    // Hiển thị danh sách kết quả nếu có kết quả và đang focus
                    if (!predictions.isEmpty()) {
                        hasSuggestions = true;
                        if (searchInput.hasFocus() || shouldKeepSuggestions) {
                            showSuggestionsList();
                        }
                        // Thông báo cho activity về kết quả tìm kiếm mới
                        if (onPlacesSearchResultListener != null) {
                            onPlacesSearchResultListener.onPlacesSearchResult(predictions);
                        }
                    } else {
                        hasSuggestions = false;
                        hideSuggestionsList();
                        // Thông báo không có kết quả
                        if (onPlacesSearchResultListener != null) {
                            onPlacesSearchResultListener.onNoResults();
                        }
                    }
                })
                .addOnFailureListener(exception -> {
                    if (exception instanceof ApiException) {
                        ApiException apiException = (ApiException) exception;
                        Log.e(TAG, "Lỗi Places API: " + apiException.getStatusCode() + ", " + apiException.getMessage());
                    } else {
                        Log.e(TAG, "Lỗi không xác định: " + exception.getMessage());
                    }
                    hasSuggestions = false;
                    hideSuggestionsList();
                    // Thông báo lỗi
                    if (onPlacesSearchResultListener != null) {
                        onPlacesSearchResultListener.onError(exception.getMessage());
                    }
                });
    }

    /**
     * Thực hiện tìm kiếm trực tiếp khi người dùng nhấn Enter
     */
    private void performDirectSearch(String query) {
        // Thực hiện tìm kiếm như tìm kiếm gợi ý nhưng với logic khác
        searchPlaces(query);
        
        // Thông báo cho activity rằng đây là tìm kiếm trực tiếp
        if (onPlacesSearchResultListener != null) {
            // Có thể thêm một phương thức mới trong interface để xử lý tìm kiếm trực tiếp
            // onPlacesSearchResultListener.onDirectSearch(query);
        }
    }

    /**
     * Cập nhật adapter với các kết quả tìm kiếm mới
     */
    private void updateAdapter(List<AutocompletePrediction> predictions) {
        adapter = new PlaceAutocompleteAdapter(getContext(), predictions, currentLocation);
        adapter.setListener((PlaceAutocompleteAdapter.PlaceAutocompleteAdapterListener) getContext());
        placesList.setAdapter(adapter);
    }

    /**
     * Hiển thị danh sách gợi ý tìm kiếm
     */
    private void showSuggestionsList() {
        placesList.setVisibility(View.VISIBLE);
    }

    /**
     * Ẩn danh sách gợi ý tìm kiếm
     */
    private void hideSuggestionsList() {
        placesList.setVisibility(View.GONE);
    }

    /**
     * Ẩn danh sách gợi ý tìm kiếm (phương thức public để gọi từ bên ngoài)
     */
    public void hideSuggestionsListPublic() {
        hideSuggestionsList();
        shouldKeepSuggestions = false;
    }

    /**
     * Ẩn bàn phím
     */
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    /**
     * Thiết lập PlacesClient
     */
    public void setPlacesClient(PlacesClient placesClient) {
        this.placesClient = placesClient;
    }

    /**
     * Cập nhật vị trí hiện tại
     */
    public void setCurrentLocation(LatLng currentLocation) {
        this.currentLocation = currentLocation;
        if (adapter != null) {
            adapter = new PlaceAutocompleteAdapter(getContext(), adapter.getCurrentItems(), currentLocation);
            adapter.setListener((PlaceAutocompleteAdapter.PlaceAutocompleteAdapterListener) getContext());
            placesList.setAdapter(adapter);
        }
    }

    /**
     * Thiết lập listener cho sự kiện chọn địa điểm
     */
    public void setOnPlaceSelectedListener(OnPlaceSelectedListener listener) {
        this.onPlaceSelectedListener = listener;
    }

    /**
     * Thiết lập listener cho kết quả tìm kiếm địa điểm
     */
    public void setOnPlacesSearchResultListener(OnPlacesSearchResultListener listener) {
        this.onPlacesSearchResultListener = listener;
    }

    /**
     * Set gợi ý cho ô tìm kiếm
     */
    public void setHint(String hint) {
        searchInput.setHint(hint);
    }

    /**
     * Lấy văn bản hiện tại trong ô tìm kiếm
     */
    public String getCurrentQuery() {
        return searchInput.getText().toString().trim();
    }

    /**
     * Đặt văn bản cho ô tìm kiếm
     */
    public void setQuery(String query) {
        searchInput.setText(query);
    }

    /**
     * Interface để xử lý sự kiện khi người dùng chọn một địa điểm
     */
    public interface OnPlaceSelectedListener {
        void onPlaceSelected(AutocompletePrediction place);
    }

    /**
     * Interface để xử lý sự kiện khi có kết quả tìm kiếm
     */
    public interface OnPlacesSearchResultListener {
        void onPlacesSearchResult(List<AutocompletePrediction> predictions);
        void onNoResults();
        void onError(String errorMessage);
        void onSearchCleared();
        // Có thể thêm phương thức này cho tìm kiếm trực tiếp
        // void onDirectSearch(String query);
    }
}
