package com.example.mobile_app.maps;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.libraries.places.api.model.Place;
import com.example.mobile_app.R;

/**
 * Bottom sheet dialog to show place details including photo, name, address, and rating.
 */
public class PlaceDetailBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_PLACE_NAME = "place_name";
    private static final String ARG_PLACE_ADDRESS = "place_address";
    private static final String ARG_PLACE_RATING = "place_rating";
    private static final String ARG_PLACE_PHOTO_URL = "place_photo_url";

    private String placeName;
    private String placeAddress;
    private String placeRating;
    private String placePhotoUrl;

    public static PlaceDetailBottomSheet newInstance(Place place) {
        PlaceDetailBottomSheet fragment = new PlaceDetailBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PLACE_NAME, place.getName());
        args.putString(ARG_PLACE_ADDRESS, place.getAddress());
        args.putString(ARG_PLACE_RATING, place.getRating() != null ? String.valueOf(place.getRating()) : "N/A");
        // Use a placeholder photo URL or fetch from place if available
        args.putString(ARG_PLACE_PHOTO_URL, "https://images.pexels.com/photos/356378/pexels-photo-356378.jpeg");
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            placeName = getArguments().getString(ARG_PLACE_NAME);
            placeAddress = getArguments().getString(ARG_PLACE_ADDRESS);
            placeRating = getArguments().getString(ARG_PLACE_RATING);
            placePhotoUrl = getArguments().getString(ARG_PLACE_PHOTO_URL);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.place_detail_bottom_sheet, container, false);

        ImageView photoView = view.findViewById(R.id.place_photo);
        TextView nameView = view.findViewById(R.id.place_name);
        TextView addressView = view.findViewById(R.id.place_address);
        TextView ratingView = view.findViewById(R.id.place_rating);

        Glide.with(this)
                .load(placePhotoUrl)
                .centerCrop()
                .placeholder(R.drawable.placeholder)
                .into(photoView);

        nameView.setText(placeName != null ? placeName : "Unknown");
        addressView.setText(placeAddress != null ? placeAddress : "No address available");
        ratingView.setText("Rating: " + (placeRating != null ? placeRating : "N/A"));

        return view;
    }
}
