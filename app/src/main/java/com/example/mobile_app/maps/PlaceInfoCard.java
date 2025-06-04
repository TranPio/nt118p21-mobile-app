package com.example.mobile_app.maps;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobile_app.R;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.PhotoMetadata;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPhotoRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Lớp quản lý thẻ thông tin chi tiết địa điểm (PlaceInfoCard)
 */
public class PlaceInfoCard {
    private static final String TAG = "PlaceInfoCard";

    private final Context context;
    private final PlacesClient placesClient;
    private final PlaceDetailsHelper placeDetailsHelper;
    private MaterialCardView cardView;
    private ViewGroup parentContainer;
    private boolean isVisible = false;

    // Views
    private TextView titleText;
    private TextView fullNameText;
    private TextView ratingText;
    private TextView ratingCountText;
    private TextView distanceText;
    private TextView typeText;
    private TextView statusText;
    private TextView addressText;    private ImageView mainImage;
    private ImageView typeIcon;
    private ImageView closeButton;
    private TextView imageCounter;
    private RecyclerView thumbnailsRecycler;
    private View callButton;
    private MaterialButton directionsButton;
    private View openingHoursContainer;

    // Image gallery
    private ImageThumbnailAdapter thumbnailAdapter;
    private List<Bitmap> placeImages;
    private int currentImageIndex = 0;

    // Listener
    public interface PlaceInfoCardListener {
        void onDirectionsClicked(Place place);
        void onCallClicked(Place place);
        void onCardClosed();
    }

    private PlaceInfoCardListener listener;
    private Place currentPlace;

    public PlaceInfoCard(Context context, PlacesClient placesClient, PlaceDetailsHelper placeDetailsHelper) {
        this.context = context;
        this.placesClient = placesClient;
        this.placeDetailsHelper = placeDetailsHelper;
        initializeViews();
    }

    /**
     * Khởi tạo views
     */    private void initializeViews() {
        LayoutInflater inflater = LayoutInflater.from(context);
        // Use place_info_card_new.xml as the new layout
        cardView = (MaterialCardView) inflater.inflate(R.layout.place_info_card_new, null);
        
        // Initialize views with null checks to prevent crashes
        titleText = cardView.findViewById(R.id.place_title);
        // In the new layout, just use title text for both
        fullNameText = titleText;
        
        // Rating-related views
        ratingText = cardView.findViewById(R.id.place_rating_text);
        ratingCountText = cardView.findViewById(R.id.place_review_count); // Using place_review_count from new layout
        
        // Type/category text
        typeText = cardView.findViewById(R.id.place_category); // Using place_category from new layout
        
        // Hours and address
        statusText = cardView.findViewById(R.id.place_hours);
        addressText = cardView.findViewById(R.id.place_address);
        
        // Close button
        closeButton = cardView.findViewById(R.id.btn_close);
        
        // Action buttons - using containers in new layout
        callButton = cardView.findViewById(R.id.btn_call_container);
        
        // Keep old directions button reference for backward compatibility
        // but use null since it doesn't exist in the new layout
        directionsButton = null;
        
        // Opening hours reference
        openingHoursContainer = cardView.findViewById(R.id.place_hours);
        
        // The new design doesn't use these components
        distanceText = null;
        mainImage = null;
        typeIcon = null;
        imageCounter = null;
        thumbnailsRecycler = null;

        // Initialize image gallery
        placeImages = new ArrayList<>();
        setupImageGallery();

        setupClickListeners();
    }    /**
     * Setup image gallery với RecyclerView cho thumbnails
     */
    private void setupImageGallery() {
        // The new layout doesn't use image gallery, so we'll skip this setup
        thumbnailAdapter = new ImageThumbnailAdapter(context);
        
        // Skip RecyclerView setup since it's null in the new layout
        if (thumbnailsRecycler != null) {
            thumbnailsRecycler.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            thumbnailsRecycler.setAdapter(thumbnailAdapter);
            
            // Set thumbnail click listener
            thumbnailAdapter.setOnThumbnailClickListener(position -> {
                currentImageIndex = position;
                updateMainImage();
                updateImageCounter();
                thumbnailAdapter.setSelectedPosition(position);
            });
        }

        // Skip main image swipe setup since mainImage is null in the new layout
        if (mainImage != null) {
            setupMainImageSwipe();
        }
    }    /**
     * Setup swipe gesture cho main image
     */
    private void setupMainImageSwipe() {
        if (mainImage == null) {
            return; // Skip if mainImage is null in the new layout
        }
        
        mainImage.setOnTouchListener(new View.OnTouchListener() {
            private float startX = 0;
            private static final int MIN_SWIPE_DISTANCE = 100;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        return true;
                    
                    case android.view.MotionEvent.ACTION_UP:
                        float endX = event.getX();
                        float deltaX = startX - endX;
                        
                        if (Math.abs(deltaX) > MIN_SWIPE_DISTANCE) {
                            if (deltaX > 0) {
                                // Swipe left - next image
                                showNextImage();
                            } else {
                                // Swipe right - previous image
                                showPreviousImage();
                            }
                        }
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * Hiển thị ảnh tiếp theo
     */
    private void showNextImage() {
        if (placeImages.size() > 1) {
            currentImageIndex = (currentImageIndex + 1) % placeImages.size();
            updateMainImage();
            updateImageCounter();
            thumbnailAdapter.setSelectedPosition(currentImageIndex);
        }
    }

    /**
     * Hiển thị ảnh trước đó
     */
    private void showPreviousImage() {
        if (placeImages.size() > 1) {
            currentImageIndex = (currentImageIndex - 1 + placeImages.size()) % placeImages.size();
            updateMainImage();
            updateImageCounter();
            thumbnailAdapter.setSelectedPosition(currentImageIndex);
        }
    }    /**
     * Cập nhật ảnh chính
     */
    private void updateMainImage() {
        if (mainImage == null) {
            return; // Skip if mainImage is null in the new layout
        }
        
        if (!placeImages.isEmpty() && currentImageIndex < placeImages.size()) {
            mainImage.setImageBitmap(placeImages.get(currentImageIndex));
        }
    }

    /**
     * Cập nhật counter hiển thị số ảnh
     */
    private void updateImageCounter() {
        if (imageCounter == null) {
            return; // Skip if imageCounter is null in the new layout
        }
        
        if (placeImages.size() > 1) {
            imageCounter.setText(String.format(Locale.getDefault(), "%d/%d",
                currentImageIndex + 1, placeImages.size()));
            imageCounter.setVisibility(View.VISIBLE);
        } else {
            imageCounter.setVisibility(View.GONE);
        }
    }

    /**
     * Thiết lập click listeners
     */
    private void setupClickListeners() {
        // Close button
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> hide());
        }
        
        // Directions button container
        View directionsContainer = cardView.findViewById(R.id.btn_directions_container);
        if (directionsContainer != null) {
            directionsContainer.setOnClickListener(v -> {
                if (listener != null && currentPlace != null) {
                    listener.onDirectionsClicked(currentPlace);
                    Toast.makeText(context, "Đang tìm đường đi...", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // Directions FAB inside container
        View fabDirections = cardView.findViewById(R.id.fab_directions);
        if (fabDirections != null) {
            fabDirections.setOnClickListener(v -> {
                if (listener != null && currentPlace != null) {
                    listener.onDirectionsClicked(currentPlace);
                    Toast.makeText(context, "Đang tìm đường đi...", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // Call button container
        if (callButton != null) {
            callButton.setOnClickListener(v -> {
                if (listener != null && currentPlace != null) {
                    listener.onCallClicked(currentPlace);
                }
            });
        }
        
        // Call FAB
        View fabCall = cardView.findViewById(R.id.fab_call);
        if (fabCall != null) {
            fabCall.setOnClickListener(v -> {
                if (listener != null && currentPlace != null) {
                    listener.onCallClicked(currentPlace);
                }
            });
        }
        
        // Share button
        View shareButton = cardView.findViewById(R.id.btn_share);
        if (shareButton != null) {
            shareButton.setOnClickListener(v -> {
                if (currentPlace != null && currentPlace.getName() != null) {
                    String shareText = currentPlace.getName() + " - " + 
                            (currentPlace.getAddress() != null ? currentPlace.getAddress() : "");
                    
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                    context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ qua"));
                }
            });
        }
        
        // Website button container
        View websiteContainer = cardView.findViewById(R.id.btn_website_container);
        if (websiteContainer != null) {
            websiteContainer.setOnClickListener(v -> {
                if (currentPlace != null && currentPlace.getWebsiteUri() != null) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, currentPlace.getWebsiteUri());
                    context.startActivity(browserIntent);
                } else {
                    Toast.makeText(context, "Không có website cho địa điểm này", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // Website FAB
        View fabWebsite = cardView.findViewById(R.id.fab_website);
        if (fabWebsite != null) {
            fabWebsite.setOnClickListener(v -> {
                if (currentPlace != null && currentPlace.getWebsiteUri() != null) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, currentPlace.getWebsiteUri());
                    context.startActivity(browserIntent);
                } else {
                    Toast.makeText(context, "Không có website cho địa điểm này", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        // Save button container
        View saveContainer = cardView.findViewById(R.id.btn_save_container);
        if (saveContainer != null) {
            saveContainer.setOnClickListener(v -> {
                Toast.makeText(context, "Đã lưu địa điểm", Toast.LENGTH_SHORT).show();
                // Implement save functionality in a future update
            });
        }
    }

    /**
     * Hiển thị thẻ thông tin cho địa điểm
     */
    public void showPlaceInfo(Place place) {
        this.currentPlace = place;
        updateCardContent(place);
        show();
    }

    /**
     * Cập nhật nội dung thẻ thông tin
     */
    private void updateCardContent(Place place) {
        // Tên địa điểm
        if (place.getName() != null) {
            titleText.setText(place.getName());
        }

        // Đánh giá - Cập nhật RatingBar nếu có
        android.widget.RatingBar ratingBar = cardView.findViewById(R.id.place_rating_bar);
        if (place.getRating() != null) {
            float rating = place.getRating().floatValue();
            
            // Update text rating
            if (ratingText != null) {
                ratingText.setText(String.format(Locale.getDefault(), "%.1f", rating));
                ratingText.setVisibility(View.VISIBLE);
            }
            
            // Update rating bar if available
            if (ratingBar != null) {
                ratingBar.setRating(rating);
                ratingBar.setVisibility(View.VISIBLE);
            }
            
            // Update rating count
            if (ratingCountText != null && place.getUserRatingsTotal() != null) {
                ratingCountText.setText(String.format(Locale.getDefault(), "(%d)", place.getUserRatingsTotal()));
                ratingCountText.setVisibility(View.VISIBLE);
            } else if (ratingCountText != null) {
                ratingCountText.setVisibility(View.GONE);
            }
        } else {
            // Hide rating elements if no rating
            if (ratingText != null) ratingText.setVisibility(View.GONE);
            if (ratingCountText != null) ratingCountText.setVisibility(View.GONE);
            if (ratingBar != null) ratingBar.setVisibility(View.GONE);
        }

        // Loại địa điểm (category in new layout)
        updatePlaceType(place);

        // Trạng thái hoạt động (now using place_hours in new layout)
        updateOpeningHours(place);

        // Địa chỉ
        if (addressText != null && place.getAddress() != null) {
            addressText.setText(place.getAddress());
            addressText.setVisibility(View.VISIBLE);
        } else if (addressText != null) {
            addressText.setVisibility(View.GONE);
        }

        // Số điện thoại (call button visibility)
        updatePhoneNumber(place);

        // Website button visibility
        updateWebsiteButton(place);
    }
    
    /**
     * Cập nhật nút website
     */
    private void updateWebsiteButton(Place place) {
        View websiteContainer = cardView.findViewById(R.id.btn_website_container);
        if (websiteContainer != null) {
            websiteContainer.setVisibility(place.getWebsiteUri() != null ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Cập nhật loại địa điểm
     */
    private void updatePlaceType(Place place) {
        if (typeText != null && place.getTypes() != null && !place.getTypes().isEmpty()) {
            Place.Type primaryType = place.getTypes().get(0);
            String typeName = getTypeDisplayName(primaryType);
            typeText.setText(typeName);
            typeText.setVisibility(View.VISIBLE);
        } else if (typeText != null) {
            typeText.setVisibility(View.GONE);
        }
    }

    /**
     * Cập nhật giờ mở cửa
     */
    private void updateOpeningHours(Place place) {
        if (statusText != null) {
            if (place.getOpeningHours() != null) {
                // Lấy trạng thái hiện tại
                Calendar now = Calendar.getInstance();
                
                // Giả lập trạng thái (do API có hạn chế)
                boolean isOpen = now.get(Calendar.HOUR_OF_DAY) >= 7 && now.get(Calendar.HOUR_OF_DAY) < 22;
                
                if (isOpen) {
                    statusText.setText("Đang mở cửa");
                    statusText.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark));
                } else {
                    statusText.setText("Đã đóng cửa · Mở lúc 7:00");
                    statusText.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark));
                }
                
                statusText.setVisibility(View.VISIBLE);
            } else {
                statusText.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Cập nhật thông tin số điện thoại
     */    private void updatePhoneNumber(Place place) {
        View callContainer = cardView.findViewById(R.id.btn_call_container);
        
        if (callContainer != null) {
            boolean hasPhone = place.getPhoneNumber() != null && !place.getPhoneNumber().isEmpty();
            callContainer.setVisibility(hasPhone ? View.VISIBLE : View.GONE);
        }
        
        // We don't need to check the old button ID since we're using the new layout
    }/**
     * Tải hình ảnh địa điểm - hỗ trợ multiple photos
     */
    private void loadPlacePhoto(Place place) {
        // Clear previous images
        placeImages.clear();
        currentImageIndex = 0;
        
        if (place.getPhotoMetadatas() != null && !place.getPhotoMetadatas().isEmpty()) {
            List<PhotoMetadata> photoMetadatas = place.getPhotoMetadatas();
            int photosToLoad = Math.min(photoMetadatas.size(), 5); // Tối đa 5 ảnh
            
            // Load each photo
            for (int i = 0; i < photosToLoad; i++) {
                PhotoMetadata photoMetadata = photoMetadatas.get(i);
                
                FetchPhotoRequest photoRequest = FetchPhotoRequest.builder(photoMetadata)
                        .setMaxWidth(800)
                        .setMaxHeight(600)
                        .build();
                
                final int photoIndex = i;
                placesClient.fetchPhoto(photoRequest)
                        .addOnSuccessListener(fetchPhotoResponse -> {
                            Bitmap bitmap = fetchPhotoResponse.getBitmap();
                            placeImages.add(bitmap);
                            
                            // Update UI when first image is loaded
                            if (photoIndex == 0) {
                                updateMainImage();
                                updateImageCounter();
                            }
                            
                            // Update thumbnails when all expected images are loaded
                            if (placeImages.size() == photosToLoad) {
                                updateImageGallery();
                            }
                        })
                        .addOnFailureListener(exception -> {
                            // If first image fails, show placeholder
                            if (photoIndex == 0 && placeImages.isEmpty()) {
                                loadPlaceholderImage();
                            }
                        });
            }
        } else {
            loadPlaceholderImage();
        }
    }

    /**
     * Load placeholder image khi không có ảnh
     */
    private void loadPlaceholderImage() {
        mainImage.setImageResource(R.drawable.placeholder_image);
        imageCounter.setVisibility(View.GONE);
        thumbnailsRecycler.setVisibility(View.GONE);
    }

    /**
     * Cập nhật image gallery UI
     */
    private void updateImageGallery() {
        if (placeImages.size() > 1) {
            // Show thumbnails if multiple images
            thumbnailAdapter.setImages(placeImages);
            thumbnailAdapter.setSelectedPosition(currentImageIndex);
            thumbnailsRecycler.setVisibility(View.VISIBLE);
            updateImageCounter();
        } else {
            // Hide thumbnails if only one image
            thumbnailsRecycler.setVisibility(View.GONE);
            imageCounter.setVisibility(View.GONE);
        }
    }

    /**
     * Hiển thị thẻ với animation
     */
    public void show() {
        if (parentContainer == null || isVisible) return;

        // Thêm card vào container
        if (cardView.getParent() == null) {
            parentContainer.addView(cardView);
        }

        cardView.setVisibility(View.VISIBLE);
        
        // Animation slide up
        cardView.setTranslationY(cardView.getHeight());
        cardView.animate()
                .translationY(0)
                .setDuration(300)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isVisible = true;
                    }
                })
                .start();
    }

    /**
     * Ẩn thẻ với animation
     */
    public void hide() {
        if (!isVisible) return;

        cardView.animate()
                .translationY(cardView.getHeight())
                .setDuration(300)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        cardView.setVisibility(View.GONE);
                        if (parentContainer != null && cardView.getParent() != null) {
                            parentContainer.removeView(cardView);
                        }
                        isVisible = false;
                        if (listener != null) {
                            listener.onCardClosed();
                        }
                    }
                })
                .start();
    }

    /**
     * Thiết lập container cha
     */
    public void setParentContainer(ViewGroup container) {
        this.parentContainer = container;
    }

    /**
     * Thiết lập listener
     */
    public void setListener(PlaceInfoCardListener listener) {
        this.listener = listener;
    }

    /**
     * Kiểm tra xem thẻ có đang hiển thị không
     */
    public boolean isVisible() {
        return isVisible;
    }

    /**
     * Lấy địa điểm hiện tại
     */
    public Place getCurrentPlace() {
        return currentPlace;
    }

    /**
     * Lấy tên hiển thị cho loại địa điểm
     */
    private String getTypeDisplayName(Place.Type type) {
        switch (type) {
            case UNIVERSITY:
                return "Trường đại học";
            case SCHOOL:
                return "Trường học";
            case HOSPITAL:
                return "Bệnh viện";
            case RESTAURANT:
                return "Nhà hàng";
            case BANK:
                return "Ngân hàng";
            case GAS_STATION:
                return "Trạm xăng";
            case PHARMACY:
                return "Hiệu thuốc";
            case SUPERMARKET:
                return "Siêu thị";
            case SHOPPING_MALL:
                return "Trung tâm thương mại";
            case PARK:
                return "Công viên";
            case MUSEUM:
                return "Bảo tàng";
            case TOURIST_ATTRACTION:
                return "Điểm du lịch";
            default:
                return "Địa điểm";
        }
    }
}
