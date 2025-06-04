package com.example.mobile_app.maps;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobile_app.R;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * Quản lý thẻ hiển thị tóm tắt tuyến đường
 */
public class RouteSummaryCard {
    private static final String TAG = "RouteSummaryCard";
    
    private final Context context;
    private MaterialCardView cardView;
    private ViewGroup parentContainer;
    private boolean isVisible = false;
    
    // Views
    private TextView routeDuration;
    private TextView routeDistance;
    private TextView routeVia;
    private MaterialButton startNavigationButton;
    private ImageView closeButton;
    private TextView alternativeRoutesLabel;
    private RecyclerView alternativeRoutesRecycler;
    
    // Data
    private List<DirectionsService.Route> routes;
    private int selectedRouteIndex = 0;
    private LatLng origin;
    private LatLng destination;
    
    // Listeners
    public interface RouteSummaryListener {
        void onRouteSelected(int routeIndex);
        void onNavigationStarted(DirectionsService.Route route);
        void onCardClosed();
    }
    
    private RouteSummaryListener listener;
    
    public RouteSummaryCard(Context context) {
        this.context = context;
        initializeViews();
    }
    
    /**
     * Khởi tạo views
     */
    private void initializeViews() {
        LayoutInflater inflater = LayoutInflater.from(context);
        cardView = (MaterialCardView) inflater.inflate(R.layout.route_summary_card, null);
        
        // Khởi tạo các views
        routeDuration = cardView.findViewById(R.id.route_duration);
        routeDistance = cardView.findViewById(R.id.route_distance);
        routeVia = cardView.findViewById(R.id.route_via);
        startNavigationButton = cardView.findViewById(R.id.btn_start_navigation);
        closeButton = cardView.findViewById(R.id.btn_close_route);
        alternativeRoutesLabel = cardView.findViewById(R.id.alternative_routes_label);
        alternativeRoutesRecycler = cardView.findViewById(R.id.alternative_routes_recycler);
        
        // Setup RecyclerView
        alternativeRoutesRecycler.setLayoutManager(new LinearLayoutManager(context));
        
        setupClickListeners();
    }
    
    /**
     * Setup click listeners
     */
    private void setupClickListeners() {
        closeButton.setOnClickListener(v -> {
            hide();
            if (listener != null) {
                listener.onCardClosed();
            }
        });
        
        startNavigationButton.setOnClickListener(v -> {
            if (routes != null && selectedRouteIndex < routes.size() && listener != null) {
                DirectionsService.Route selectedRoute = routes.get(selectedRouteIndex);
                listener.onNavigationStarted(selectedRoute);
                
                // Mở Google Maps để navigation
                openGoogleMapsNavigation();
            }
        });
    }
    
    /**
     * Mở Google Maps để bắt đầu navigation
     */
    private void openGoogleMapsNavigation() {
        if (destination == null) return;
        
        Uri gmmIntentUri;
        if (origin != null) {
            // Navigation từ vị trí hiện tại đến đích
            gmmIntentUri = Uri.parse(String.format("google.navigation:q=%f,%f&mode=d",
                    destination.latitude, destination.longitude));
        } else {
            // Chỉ hiển thị đích trên bản đồ
            gmmIntentUri = Uri.parse(String.format("geo:%f,%f?q=%f,%f",
                    destination.latitude, destination.longitude,
                    destination.latitude, destination.longitude));
        }
        
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        
        if (mapIntent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(mapIntent);
        } else {
            Toast.makeText(context, "Google Maps chưa được cài đặt", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Hiển thị thông tin routes
     */
    public void showRoutes(List<DirectionsService.Route> routes, LatLng origin, LatLng destination) {
        if (routes == null || routes.isEmpty()) return;
        
        this.routes = routes;
        this.origin = origin;
        this.destination = destination;
        this.selectedRouteIndex = 0;
        
        updateSelectedRouteInfo();
        updateAlternativeRoutes();
        show();
    }
    
    /**
     * Cập nhật thông tin route được chọn
     */
    private void updateSelectedRouteInfo() {
        if (routes == null || selectedRouteIndex >= routes.size()) return;
        
        DirectionsService.Route selectedRoute = routes.get(selectedRouteIndex);
        
        // Cập nhật thông tin cơ bản
        routeDuration.setText(selectedRoute.getDuration());
        routeDistance.setText(selectedRoute.getDistance());
        
        // Cập nhật summary (via)
        String summary = selectedRoute.getSummary();
        if (summary != null && !summary.trim().isEmpty()) {
            routeVia.setText("qua " + summary);
            routeVia.setVisibility(View.VISIBLE);
        } else {
            routeVia.setVisibility(View.GONE);
        }
    }
    
    /**
     * Cập nhật danh sách routes thay thế
     */
    private void updateAlternativeRoutes() {
        if (routes == null || routes.size() <= 1) {
            alternativeRoutesLabel.setVisibility(View.GONE);
            alternativeRoutesRecycler.setVisibility(View.GONE);
            return;
        }
        
        // Hiển thị các routes thay thế
        alternativeRoutesLabel.setVisibility(View.VISIBLE);
        alternativeRoutesRecycler.setVisibility(View.VISIBLE);
        
        AlternativeRoutesAdapter adapter = new AlternativeRoutesAdapter(routes, selectedRouteIndex);
        adapter.setOnRouteClickListener(routeIndex -> {
            if (routeIndex != selectedRouteIndex) {
                selectedRouteIndex = routeIndex;
                updateSelectedRouteInfo();
                adapter.setSelectedRouteIndex(routeIndex);
                
                if (listener != null) {
                    listener.onRouteSelected(routeIndex);
                }
            }
        });
        
        alternativeRoutesRecycler.setAdapter(adapter);
    }
    
    /**
     * Chọn route khác
     */
    public void selectRoute(int routeIndex) {
        if (routes == null || routeIndex < 0 || routeIndex >= routes.size()) return;
        
        this.selectedRouteIndex = routeIndex;
        updateSelectedRouteInfo();
        
        // Cập nhật adapter
        RecyclerView.Adapter adapter = alternativeRoutesRecycler.getAdapter();
        if (adapter instanceof AlternativeRoutesAdapter) {
            ((AlternativeRoutesAdapter) adapter).setSelectedRouteIndex(routeIndex);
        }
    }
    
    /**
     * Hiển thị card
     */
    public void show() {
        if (parentContainer == null || isVisible) return;
        
        if (cardView.getParent() == null) {
            parentContainer.addView(cardView);
        }
        
        cardView.setVisibility(View.VISIBLE);
        cardView.setTranslationY(-cardView.getHeight());
        cardView.animate()
                .translationY(0)
                .setDuration(300)
                .withEndAction(() -> isVisible = true)
                .start();
    }
    
    /**
     * Ẩn card
     */
    public void hide() {
        if (!isVisible || cardView == null) return;
        
        cardView.animate()
                .translationY(-cardView.getHeight())
                .setDuration(300)
                .withEndAction(() -> {
                    cardView.setVisibility(View.GONE);
                    if (parentContainer != null) {
                        parentContainer.removeView(cardView);
                    }
                    isVisible = false;
                })
                .start();
    }
    
    /**
     * Set parent container
     */
    public void setParentContainer(ViewGroup container) {
        this.parentContainer = container;
    }
    
    /**
     * Set listener
     */
    public void setListener(RouteSummaryListener listener) {
        this.listener = listener;
    }
    
    /**
     * Kiểm tra card có đang hiển thị không
     */
    public boolean isVisible() {
        return isVisible;
    }
    
    /**
     * Adapter cho danh sách routes thay thế
     */
    private static class AlternativeRoutesAdapter extends RecyclerView.Adapter<AlternativeRoutesAdapter.RouteViewHolder> {
        private final List<DirectionsService.Route> routes;
        private int selectedRouteIndex;
        private OnRouteClickListener onRouteClickListener;
        
        interface OnRouteClickListener {
            void onRouteClick(int routeIndex);
        }
        
        public AlternativeRoutesAdapter(List<DirectionsService.Route> routes, int selectedRouteIndex) {
            this.routes = routes;
            this.selectedRouteIndex = selectedRouteIndex;
        }
        
        @Override
        public RouteViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_alternative_route, parent, false);
            return new RouteViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(RouteViewHolder holder, int position) {
            // Bỏ qua route đã được chọn
            int actualRouteIndex = position >= selectedRouteIndex ? position + 1 : position;
            
            if (actualRouteIndex < routes.size()) {
                DirectionsService.Route route = routes.get(actualRouteIndex);
                holder.bind(route, actualRouteIndex);
                
                holder.itemView.setOnClickListener(v -> {
                    if (onRouteClickListener != null) {
                        onRouteClickListener.onRouteClick(actualRouteIndex);
                    }
                });
            }
        }
        
        @Override
        public int getItemCount() {
            return Math.max(0, routes.size() - 1); // Trừ đi route đã chọn
        }
        
        public void setSelectedRouteIndex(int selectedRouteIndex) {
            this.selectedRouteIndex = selectedRouteIndex;
            notifyDataSetChanged();
        }
        
        public void setOnRouteClickListener(OnRouteClickListener listener) {
            this.onRouteClickListener = listener;
        }
        
        static class RouteViewHolder extends RecyclerView.ViewHolder {
            private final TextView duration;
            private final TextView distance;
            private final TextView via;
            
            public RouteViewHolder(View itemView) {
                super(itemView);
                duration = itemView.findViewById(R.id.alt_route_duration);
                distance = itemView.findViewById(R.id.alt_route_distance);
                via = itemView.findViewById(R.id.alt_route_via);
            }
            
            public void bind(DirectionsService.Route route, int routeIndex) {
                duration.setText(route.getDuration());
                distance.setText(route.getDistance());
                
                String summary = route.getSummary();
                if (summary != null && !summary.trim().isEmpty()) {
                    via.setText("qua " + summary);
                    via.setVisibility(View.VISIBLE);
                } else {
                    via.setVisibility(View.GONE);
                }
            }
        }
    }
}
