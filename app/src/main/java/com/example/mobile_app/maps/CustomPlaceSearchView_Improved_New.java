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

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.Timer;
import java.util.TimerTask;

/**
 * View tùy chỉnh để xử lý và hiển thị tìm kiếm địa điểm với giao diện cải tiến
 * Cải thiện logic hiển thị danh sách gợi ý để đảm bảo trải nghiệm mượt mà
 */
public class CustomPlaceSearchView_Improved_New extends androidx.constraintlayout.widget.ConstraintLayout {

    private static final String TAG = "CustomPlaceSearchView_Improved_New";
    private static final long SEARCH_DELAY_MILLIS = 250; // Debounce time for search

    private com.google.android.material.textfield.TextInputLayout searchInputLayout;
    private com.google.android.material.textfield.MaterialAutoCompleteTextView searchInput;
    private RecyclerView placesRecyclerView;
    private PlaceAutocompleteAdapter_Material3 adapter;
    private PlacesClient placesClient;
    private LatLng currentLocation;
    private AutocompleteSessionToken sessionToken;
    private Timer searchTimer;
    private OnPlaceSelectedListener onPlaceSelectedListener;
    private OnPlacesSearchResultListener onPlacesSearchResultListener;

    public CustomPlaceSearchView_Improved_New(Context context) {
        super(context);
        init(context);
    }

    public CustomPlaceSearchView_Improved_New(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CustomPlaceSearchView_Improved_New(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.custom_place_search_material3, this, true);

        searchInputLayout = findViewById(R.id.search_input_layout);
        searchInput = findViewById(R.id.search_input);
        placesRecyclerView = findViewById(R.id.places_recycler_view);

        adapter = new PlaceAutocompleteAdapter_Material3(new ArrayList<>(), currentLocation);
        adapter.setListener(prediction -> {
            if (onPlaceSelectedListener != null) {
                onPlaceSelectedListener.onPlaceSelected(prediction);
            }
            hideSuggestionsList();
            hideKeyboard();
        });

        placesRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        placesRecyclerView.setAdapter(adapter);

        sessionToken = AutocompleteSessionToken.newInstance();

        setupListeners();
    }

    private void setupListeners() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No op
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
                if (s.length() < 2) {
                    hideSuggestionsList();
                    if (onPlacesSearchResultListener != null) {
                        onPlacesSearchResultListener.onSearchCleared();
                    }
                    return;
                }
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        searchPlaces(s.toString());
                    }
                }, SEARCH_DELAY_MILLIS);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No op
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = searchInput.getText().toString().trim();
                if (query.length() >= 2) {
                    hideSuggestionsList();
                    hideKeyboard();
                    if (onPlacesSearchResultListener != null) {
                        // Could add onDirectSearch callback here if needed
                    }
                }
                return true;
            }
            return false;
        });
    }

    private void searchPlaces(String query) {
        if (placesClient == null) {
            Log.e(TAG, "PlacesClient not set");
            return;
        }

        FindAutocompletePredictionsRequest.Builder requestBuilder = FindAutocompletePredictionsRequest.builder()
                .setTypeFilter(TypeFilter.ESTABLISHMENT)
                .setSessionToken(sessionToken)
                .setQuery(query);

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
                    if (!predictions.isEmpty()) {
                        showSuggestionsList();
                        if (onPlacesSearchResultListener != null) {
                            onPlacesSearchResultListener.onPlacesSearchResult(predictions);
                        }
                    } else {
                        hideSuggestionsList();
                        if (onPlacesSearchResultListener != null) {
                            onPlacesSearchResultListener.onNoResults();
                        }
                    }
                })
                .addOnFailureListener(exception -> {
                    if (exception instanceof ApiException) {
                        ApiException apiException = (ApiException) exception;
                        Log.e(TAG, "Places API error: " + apiException.getStatusCode() + ", " + apiException.getMessage());
                    } else {
                        Log.e(TAG, "Unknown error: " + exception.getMessage());
                    }
                    hideSuggestionsList();
                    if (onPlacesSearchResultListener != null) {
                        onPlacesSearchResultListener.onError(exception.getMessage());
                    }
                });
    }

    private void updateAdapter(List<AutocompletePrediction> predictions) {
        adapter.updateData(predictions, currentLocation);
    }

    public void hideSuggestionsList() {
        placesRecyclerView.setVisibility(View.GONE);
    }

    public void showSuggestionsList() {
        placesRecyclerView.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    public void setPlacesClient(PlacesClient placesClient) {
        this.placesClient = placesClient;
    }

    public void setCurrentLocation(LatLng currentLocation) {
        this.currentLocation = currentLocation;
        adapter.updateData(adapter.getCurrentItems(), currentLocation);
    }

    public void setOnPlaceSelectedListener(OnPlaceSelectedListener listener) {
        this.onPlaceSelectedListener = listener;
    }

    public void setOnPlacesSearchResultListener(OnPlacesSearchResultListener listener) {
        this.onPlacesSearchResultListener = listener;
    }

    public interface OnPlaceSelectedListener {
        void onPlaceSelected(AutocompletePrediction place);
    }

    public interface OnPlacesSearchResultListener {
        void onPlacesSearchResult(List<AutocompletePrediction> predictions);
        void onNoResults();
        void onError(String errorMessage);
        void onSearchCleared();
    }
}
</create_file>
