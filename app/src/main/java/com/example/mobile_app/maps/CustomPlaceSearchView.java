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
public class CustomPlaceSearchView extends LinearLayout {

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
      // Flags to control suggestion visibility and user interaction
    private boolean shouldKeepSuggestions = false; // Flag để kiểm soát việc hiển thị gợi ý
    private boolean isUserTyping = false; // Flag để theo dõi người dùng đang gõ
    private boolean hasSuggestions = false; // Flag để theo dõi có gợi ý hay không
    private boolean justPerformedSearch = false; // Flag để tránh hiển thị suggestions sau search

    public CustomPlaceSearchView(Context context) {
        super(context);
        init(context);
    }

    public CustomPlaceSearchView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CustomPlaceSearchView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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
        placesList = findViewById(R.id.places_list);        // Khởi tạo adapter với danh sách trống
        adapter = new PlaceAutocompleteAdapter(context, new ArrayList<>(), null);
        // Thiết lập listener cho adapter - context phải implement PlaceAutocompleteAdapterListener
        try {
            adapter.setListener((PlaceAutocompleteAdapter.PlaceAutocompleteAdapterListener) context);
        } catch (ClassCastException e) {
            Log.w(TAG, "Context does not implement PlaceAutocompleteAdapterListener");
        }
        placesList.setAdapter(adapter);

        // Khởi tạo session token
        sessionToken = AutocompleteSessionToken.newInstance();

        // Thiết lập listeners
        setupListeners();
    }    /**
     * Thiết lập các listeners cho các thành phần UI
     */
    private void setupListeners() {
        // Text change listener - hiển thị gợi ý theo thời gian thực
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Hiển thị/ẩn nút xóa
                clearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                  // Set typing flag
                isUserTyping = true;
                justPerformedSearch = false; // Reset search flag when user types

                // Xóa tìm kiếm đang chờ
                removeCallbacks(searchRunnable);

                // Nếu chuỗi tìm kiếm trống, ẩn gợi ý và thông báo clear
                if (s.length() == 0) {
                    hideSuggestionsList();
                    hasSuggestions = false;
                    isUserTyping = false;
                    // Thông báo xóa tìm kiếm để clear pins trên map
                    if (onPlacesSearchResultListener != null) {
                        onPlacesSearchResultListener.onSearchCleared();
                    }
                    return;
                }
                
                // Nếu chuỗi tìm kiếm quá ngắn, chỉ ẩn gợi ý
                if (s.length() < 2) {
                    hideSuggestionsList();
                    hasSuggestions = false;
                    return;
                }

                // Tìm kiếm gợi ý ngay lập tức với delay ngắn (200ms)
                searchRunnable = () -> {
                    if (isUserTyping) {
                        searchSuggestions(s.toString()); // Chỉ tìm gợi ý, không hiển thị pins
                    }
                };
                postDelayed(searchRunnable, 200); // Giảm delay để responsive hơn
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Reset typing flag sau một khoảng thời gian ngắn
                postDelayed(() -> isUserTyping = false, 300);
            }
        });        // Focus change listener - hiển thị gợi ý khi focus, nhưng không sau khi search
        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && hasSuggestions && searchInput.getText().length() >= 2 && !isAfterSearch()) {
                showSuggestionsList();
            } else if (!hasFocus) {
                // Ẩn gợi ý khi mất focus, trừ khi shouldKeepSuggestions = true
                if (!shouldKeepSuggestions) {
                    hideSuggestionsList();
                }
            }
        });// Enter key listener - thực hiện tìm kiếm thật sự và hiển thị pins
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String query = searchInput.getText().toString().trim();
                if (!query.isEmpty()) {                    // Ẩn gợi ý NGAY LẬP TỨC
                    hideSuggestionsList();
                    shouldKeepSuggestions = false;
                    hasSuggestions = false;
                    justPerformedSearch = true; // Set flag để ngăn hiển thị suggestions
                    
                    // Ẩn bàn phím
                    hideKeyboard();
                    
                    // Clear focus để tránh hiển thị lại gợi ý
                    searchInput.clearFocus();
                    
                    // Thực hiện tìm kiếm thật sự
                    performRealSearch(query);
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
            // Thông báo xóa tìm kiếm để clear pins
            if (onPlacesSearchResultListener != null) {
                onPlacesSearchResultListener.onSearchCleared();
            }
        });        // Item click listener cho danh sách gợi ý
        placesList.setOnItemClickListener((parent, view, position, id) -> {
            Log.d(TAG, "===== SUGGESTION TAP DEBUG =====");
            Log.d(TAG, "ListView item clicked at position: " + position);
            
            AutocompletePrediction prediction = adapter.getItem(position);
            Log.d(TAG, "Adapter getItem returned: " + (prediction != null ? prediction.getPrimaryText(null) : "null"));
            Log.d(TAG, "onPlaceSelectedListener is: " + (onPlaceSelectedListener != null ? "not null" : "null"));
            
            if (prediction != null && onPlaceSelectedListener != null) {
                Log.d(TAG, "✅ Processing suggestion tap: " + prediction.getPrimaryText(null));
                Log.d(TAG, "Place ID: " + prediction.getPlaceId());
                
                // Cập nhật text input với tên địa điểm được chọn
                String placeName = prediction.getPrimaryText(null).toString();
                searchInput.setText(placeName);
                Log.d(TAG, "Updated search input to: " + placeName);
                
                // Ẩn danh sách gợi ý ngay lập tức
                hideSuggestionsList();
                Log.d(TAG, "Hidden suggestions list");
                
                // Ẩn bàn phím
                hideKeyboard();
                Log.d(TAG, "Hidden keyboard");
                
                // Clear focus để tránh hiển thị lại gợi ý
                searchInput.clearFocus();
                Log.d(TAG, "Cleared search input focus");
                
                // Thông báo về việc chọn địa điểm (sẽ hiển thị place info card)
                Log.d(TAG, "🚀 Calling onPlaceSelected for: " + placeName);
                onPlaceSelectedListener.onPlaceSelected(prediction);
                Log.d(TAG, "✅ onPlaceSelected call completed");
                Log.d(TAG, "===== END SUGGESTION TAP DEBUG =====");
            } else {
                Log.e(TAG, "❌ Cannot process tap - Prediction is null: " + (prediction == null) + 
                      ", onPlaceSelectedListener is null: " + (onPlaceSelectedListener == null));
                if (prediction == null) {
                    Log.e(TAG, "Adapter item count: " + (adapter != null ? adapter.getCount() : "adapter null"));
                }
            }
        });
    }

    /**
     * Tìm kiếm gợi ý (chỉ hiển thị dropdown, không hiển thị pins)
     */
    private void searchSuggestions(String query) {
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
            double latDelta = 0.5;
            double lngDelta = 0.5;
            RectangularBounds bounds = RectangularBounds.newInstance(
                    new LatLng(currentLocation.latitude - latDelta, currentLocation.longitude - lngDelta),
                    new LatLng(currentLocation.latitude + latDelta, currentLocation.longitude + lngDelta)
            );
            requestBuilder.setLocationBias(bounds);
        }

        placesClient.findAutocompletePredictions(requestBuilder.build())
                .addOnSuccessListener(response -> {
                    List<AutocompletePrediction> predictions = response.getAutocompletePredictions();
                    updateAdapter(predictions);

                    // Chỉ hiển thị gợi ý dropdown, KHÔNG hiển thị pins trên map
                    if (!predictions.isEmpty()) {
                        hasSuggestions = true;
                        if (searchInput.hasFocus()) {
                            showSuggestionsList();
                        }
                    } else {
                        hasSuggestions = false;
                        hideSuggestionsList();
                    }
                })
                .addOnFailureListener(exception -> {
                    Log.e(TAG, "Lỗi tìm kiếm gợi ý: " + exception.getMessage());
                    hasSuggestions = false;
                    hideSuggestionsList();
                });
    }    /**
     * Thực hiện tìm kiếm thật sự (hiển thị pins trên map) với smart matching
     */
    private void performRealSearch(String query) {
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
            double latDelta = 0.5;
            double lngDelta = 0.5;
            RectangularBounds bounds = RectangularBounds.newInstance(
                    new LatLng(currentLocation.latitude - latDelta, currentLocation.longitude - lngDelta),
                    new LatLng(currentLocation.latitude + latDelta, currentLocation.longitude + lngDelta)
            );
            requestBuilder.setLocationBias(bounds);
        }

        placesClient.findAutocompletePredictions(requestBuilder.build())
                .addOnSuccessListener(response -> {
                    List<AutocompletePrediction> predictions = response.getAutocompletePredictions();
                    
                    if (!predictions.isEmpty()) {
                        // Tìm kết quả khớp nhất
                        AutocompletePrediction bestMatch = findBestMatch(query, predictions);
                        
                        if (bestMatch != null && isBestMatchConfident(query, bestMatch)) {
                            // Nếu có kết quả rất khớp -> auto zoom và hiển thị place info
                            if (onPlacesSearchResultListener != null) {
                                onPlacesSearchResultListener.onBestMatchFound(bestMatch, predictions);
                            }
                        } else {
                            // Hiển thị tất cả kết quả cho người dùng chọn
                            if (onPlacesSearchResultListener != null) {
                                onPlacesSearchResultListener.onPlacesSearchResult(predictions);
                            }
                        }
                    } else {
                        if (onPlacesSearchResultListener != null) {
                            onPlacesSearchResultListener.onNoResults();
                        }
                    }
                })
                .addOnFailureListener(exception -> {
                    Log.e(TAG, "Lỗi tìm kiếm: " + exception.getMessage());
                    if (onPlacesSearchResultListener != null) {
                        onPlacesSearchResultListener.onError(exception.getMessage());
                    }
                });
    }

    /**
     * Tìm kết quả khớp nhất dựa trên tên và độ liên quan
     */
    private AutocompletePrediction findBestMatch(String query, List<AutocompletePrediction> predictions) {
        if (predictions.isEmpty()) return null;
        
        String queryLower = query.toLowerCase().trim();
        AutocompletePrediction bestMatch = null;
        double bestScore = 0.0;
        
        for (AutocompletePrediction prediction : predictions) {
            String primaryText = prediction.getPrimaryText(null).toString().toLowerCase();
            String fullText = prediction.getFullText(null).toString().toLowerCase();
            
            double score = calculateMatchScore(queryLower, primaryText, fullText);
            
            if (score > bestScore) {
                bestScore = score;
                bestMatch = prediction;
            }
        }
        
        return bestMatch;
    }

    /**
     * Tính điểm khớp cho một prediction
     */
    private double calculateMatchScore(String query, String primaryText, String fullText) {
        double score = 0.0;
        
        // Exact match cho primary text = điểm cao nhất
        if (primaryText.equals(query)) {
            score = 1.0;
        }
        // Primary text chứa query hoàn toàn
        else if (primaryText.contains(query)) {
            score = 0.9;
        }
        // Query chứa primary text (user gõ đầy đủ hơn)
        else if (query.contains(primaryText)) {
            score = 0.85;
        }
        // Full text chứa query
        else if (fullText.contains(query)) {
            score = 0.7;
        }
        // Tương tự từng từ
        else {
            String[] queryWords = query.split("\\s+");
            String[] primaryWords = primaryText.split("\\s+");
            
            int matchingWords = 0;
            for (String queryWord : queryWords) {
                for (String primaryWord : primaryWords) {
                    if (primaryWord.contains(queryWord) || queryWord.contains(primaryWord)) {
                        matchingWords++;
                        break;
                    }
                }
            }
            
            if (queryWords.length > 0) {
                score = (double) matchingWords / queryWords.length * 0.6;
            }
        }
        
        return score;
    }

    /**
     * Kiểm tra xem best match có đủ confident để auto zoom không
     */
    private boolean isBestMatchConfident(String query, AutocompletePrediction bestMatch) {
        String queryLower = query.toLowerCase().trim();
        String primaryText = bestMatch.getPrimaryText(null).toString().toLowerCase();
        String fullText = bestMatch.getFullText(null).toString().toLowerCase();
        
        // High confidence conditions
        return primaryText.equals(queryLower) || 
               primaryText.contains(queryLower) || 
               queryLower.contains(primaryText) ||
               calculateMatchScore(queryLower, primaryText, fullText) >= 0.8;
    }

    /**
     * Cập nhật adapter với các kết quả tìm kiếm mới
     */
    private void updateAdapter(List<AutocompletePrediction> predictions) {
        adapter = new PlaceAutocompleteAdapter(getContext(), predictions, currentLocation);
        adapter.setListener((PlaceAutocompleteAdapter.PlaceAutocompleteAdapterListener) getContext());
        placesList.setAdapter(adapter);
    }    /**
     * Hiển thị danh sách gợi ý tìm kiếm
     */
    private void showSuggestionsList() {
        if (hasSuggestions) {
            placesList.setVisibility(View.VISIBLE);
        }
    }    /**
     * Ẩn danh sách gợi ý tìm kiếm
     */
    private void hideSuggestionsList() {
        placesList.setVisibility(View.GONE);
        shouldKeepSuggestions = false;
    }

    /**
     * Force ẩn danh sách gợi ý tìm kiếm (để dùng sau search)
     */
    public void forceHideSuggestionsList() {
        placesList.setVisibility(View.GONE);
        shouldKeepSuggestions = false;
        hasSuggestions = false;
        justPerformedSearch = true;
    }/**
     * Ẩn danh sách gợi ý tìm kiếm (phương thức public)
     */
    public void hideSuggestionsListPublic() {
        hideSuggestionsList();
    }

    /**
     * Hiển thị danh sách gợi ý (phương thức public) - để activity có thể điều khiển
     */
    public void showSuggestionsListPublic() {
        shouldKeepSuggestions = true;
        if (hasSuggestions) {
            showSuggestionsList();
        }
    }    /**
     * Kiểm tra xem có phải vừa thực hiện search hay không
     */
    private boolean isAfterSearch() {
        return justPerformedSearch;
    }

    /**
     * Reset search state khi user bắt đầu typing again
     */
    private void resetSearchState() {
        justPerformedSearch = false;
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
            try {
                adapter.setListener((PlaceAutocompleteAdapter.PlaceAutocompleteAdapterListener) getContext());
            } catch (ClassCastException e) {
                Log.w(TAG, "Context does not implement PlaceAutocompleteAdapterListener");
            }
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
     * Lấy query hiện tại
     */
    public String getCurrentQuery() {
        return searchInput.getText().toString();
    }

    /**
     * Set query cho ô tìm kiếm
     */
    public void setQuery(String query) {
        searchInput.setText(query);
    }

    /**
     * Interface để xử lý sự kiện khi người dùng chọn một địa điểm
     */
    public interface OnPlaceSelectedListener {
        void onPlaceSelected(AutocompletePrediction place);
    }    /**
     * Interface để xử lý sự kiện khi có kết quả tìm kiếm
     */
    public interface OnPlacesSearchResultListener {
        void onPlacesSearchResult(List<AutocompletePrediction> predictions);
        void onBestMatchFound(AutocompletePrediction bestMatch, List<AutocompletePrediction> allPredictions);
        void onNoResults();
        void onError(String errorMessage);
        void onSearchCleared();
    }
}
