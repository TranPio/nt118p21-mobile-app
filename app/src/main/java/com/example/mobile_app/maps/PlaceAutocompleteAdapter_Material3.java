package com.example.mobile_app.maps;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobile_app.R;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for displaying place suggestions with Material 3 styling
 * and immediate bottom sheet display on tap
 */
public class PlaceAutocompleteAdapter_Material3 extends RecyclerView.Adapter<PlaceAutocompleteAdapter_Material3.ViewHolder> {

    private List<AutocompletePrediction> predictions;
    private LatLng currentLocation;
    private OnPlaceClickListener listener;

    public PlaceAutocompleteAdapter_Material3(List<AutocompletePrediction> predictions, LatLng currentLocation) {
        this.predictions = new ArrayList<>(predictions);
        this.currentLocation = currentLocation;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_place_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AutocompletePrediction prediction = predictions.get(position);
        
        // Set primary text with highlighted matching text
        SpannableString primaryText = new SpannableString(prediction.getPrimaryText(null));
        highlightMatchedText(primaryText, prediction.getPrimaryTextMatchedSubstrings());
        holder.primaryText.setText(primaryText);
        
        // Set secondary text with highlighted matching text
        SpannableString secondaryText = new SpannableString(prediction.getSecondaryText(null));
        highlightMatchedText(secondaryText, prediction.getSecondaryTextMatchedSubstrings());
        holder.secondaryText.setText(secondaryText);

        // Handle click events - show bottom sheet immediately on tap
        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlaceClick(prediction);
                // Show place detail bottom sheet
                PlaceDetailBottomSheet bottomSheet = PlaceDetailBottomSheet.newInstance(prediction);
                bottomSheet.show(((androidx.fragment.app.FragmentActivity) v.getContext()).getSupportFragmentManager(),
                        "PlaceDetailBottomSheet");
            }
        });
    }

    private void highlightMatchedText(SpannableString text, List<AutocompletePrediction.MatchedSubstring> matches) {
        if (matches != null) {
            for (AutocompletePrediction.MatchedSubstring match : matches) {
                text.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    match.getOffset(),
                    match.getOffset() + match.getLength(),
                    0
                );
            }
        }
    }

    @Override
    public int getItemCount() {
        return predictions.size();
    }

    public void updateData(List<AutocompletePrediction> newPredictions, LatLng newLocation) {
        this.predictions = new ArrayList<>(newPredictions);
        this.currentLocation = newLocation;
        notifyDataSetChanged();
    }

    public List<AutocompletePrediction> getCurrentItems() {
        return new ArrayList<>(predictions);
    }

    public void setListener(OnPlaceClickListener listener) {
        this.listener = listener;
    }

    public interface OnPlaceClickListener {
        void onPlaceClick(AutocompletePrediction place);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView primaryText;
        TextView secondaryText;

        ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.place_card);
            primaryText = itemView.findViewById(R.id.primary_text);
            secondaryText = itemView.findViewById(R.id.secondary_text);
        }
    }
}
