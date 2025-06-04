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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobile_app.R;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

/**
 * Compact bottom panel hiển thị tùy chọn tuyến đường
 */
public class CompactRoutePanel {
    private static final String TAG = "CompactRoutePanel";
    
    private final Context context;
    private MaterialCardView panelView;
    private ViewGroup parentContainer;
    private boolean isVisible = false;
    
    // Views
    private TabLayout travelModeTabs;
    private RecyclerView routeOptionsRecycler;
    private MaterialButton startNavigationButton;
    private MaterialButton closeButton;
    
    // Data
    private List<DirectionsService.Route> routes;
    private int selectedRouteIndex = 0;
    private String selectedTravelMode = "driving";
    private LatLng origin;
    private LatLng destination;
    
    // Adapter
    private RouteOptionAdapter routeAdapter;
    
    // Listeners
    public interface CompactRoutePanelListener {
        void onRouteSelected(int routeIndex);
        void onTravelModeChanged(String travelMode);
        void onNavigationStarted(DirectionsService.Route route);
        void onPanelClosed();
    }
    
    private CompactRoutePanelListener listener;
    
    public CompactRoutePanel(Context context) {
        this.context = context;
        initializeViews();
        setupTravelModeTabs();
    }
    
    /**
     * Khởi tạo views
     */
    private void initializeViews() {
        LayoutInflater inflater = LayoutInflater.from(context);
        panelView = (MaterialCardView) inflater.inflate(R.layout.compact_route_panel, null);
        
        // Khởi tạo các views
        travelModeTabs = panelView.findViewById(R.id.travel_mode_tabs);
        routeOptionsRecycler = panelView.findViewById(R.id.route_options_recycler);
        startNavigationButton = panelView.findViewById(R.id.btn_start_navigation);
        closeButton = panelView.findViewById(R.id.btn_close_routes);
        
        // Setup RecyclerView
        routeOptionsRecycler.setLayoutManager(new LinearLayoutManager(context));
        routeAdapter = new RouteOptionAdapter();
        routeOptionsRecycler.setAdapter(routeAdapter);
        
        setupClickListeners();
    }
    
    /**
     * Setup travel mode tabs
     */
    private void setupTravelModeTabs() {
        travelModeTabs.addTab(travelModeTabs.newTab().setText("Lái xe").setTag("driving"));
        travelModeTabs.addTab(travelModeTabs.newTab().setText("Đi bộ").setTag("walking"));
        travelModeTabs.addTab(travelModeTabs.newTab().setText("Xe đạp").setTag("bicycling"));
        travelModeTabs.addTab(travelModeTabs.newTab().setText("Giao thông công cộng").setTag("transit"));
        
        travelModeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String travelMode = (String) tab.getTag();
                if (travelMode != null && !travelMode.equals(selectedTravelMode)) {
                    selectedTravelMode = travelMode;
                    if (listener != null) {
                        listener.onTravelModeChanged(travelMode);
                    }
                }
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
    
    /**
     * Setup click listeners
     */
    private void setupClickListeners() {
        closeButton.setOnClickListener(v -> {
            hide();
            if (listener != null) {
                listener.onPanelClosed();
            }
        });
        
        startNavigationButton.setOnClickListener(v -> {
            if (routes != null && selectedRouteIndex < routes.size() && listener != null) {            DirectionsService.Route selectedRoute = routes.get(selectedRouteIndex);
                listener.onNavigationStarted(selectedRoute);
                
                // Navigation được xử lý bởi listener (MapsActivity) với InAppNavigationManager
                // Không còn cần mở external Google Maps
            }
        });    }
    
    /**
     * Hiển thị panel với danh sách routes
     */
    public void showRoutes(List<DirectionsService.Route> routes, LatLng origin, LatLng destination) {
        this.routes = routes;
        this.origin = origin;
        this.destination = destination;
        this.selectedRouteIndex = 0;
        
        routeAdapter.updateRoutes(routes, selectedRouteIndex);
        show();
    }
    
    /**
     * Cập nhật route được chọn
     */
    public void updateSelectedRoute(int routeIndex) {
        if (routes != null && routeIndex >= 0 && routeIndex < routes.size()) {
            this.selectedRouteIndex = routeIndex;
            routeAdapter.updateSelectedRoute(routeIndex);
        }
    }
      /**
     * Hiển thị panel với animation
     */
    public void show() {
        if (parentContainer != null && panelView != null) {
            if (panelView.getParent() == null) {
                parentContainer.addView(panelView);
                
                // Set initial state (hidden below screen)
                panelView.setTranslationY(panelView.getHeight());
                panelView.setAlpha(0f);
            }
            
            panelView.setVisibility(View.VISIBLE);
            
            // Animate panel sliding up from bottom
            panelView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(250)
                    .withEndAction(() -> {
                        // Once animation completes, ensure it's fully visible
                        panelView.setTranslationY(0f);
                        panelView.setAlpha(1f);
                    })
                    .start();
                    
            isVisible = true;
        }
    }
    
    /**
     * Ẩn panel
     */
    public void hide() {
        if (panelView != null) {
            panelView.setVisibility(View.GONE);
            isVisible = false;
        }
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
    public void setListener(CompactRoutePanelListener listener) {
        this.listener = listener;
    }
    
    /**
     * Kiểm tra panel có đang hiển thị không
     */
    public boolean isVisible() {
        return isVisible;
    }
    
    /**
     * Adapter cho RecyclerView hiển thị route options
     */
    private class RouteOptionAdapter extends RecyclerView.Adapter<RouteOptionAdapter.RouteViewHolder> {
        private List<DirectionsService.Route> routes;
        private int selectedIndex = 0;
        
        public void updateRoutes(List<DirectionsService.Route> routes, int selectedIndex) {
            this.routes = routes;
            this.selectedIndex = selectedIndex;
            notifyDataSetChanged();
        }
        
        public void updateSelectedRoute(int selectedIndex) {
            int oldSelected = this.selectedIndex;
            this.selectedIndex = selectedIndex;
            
            notifyItemChanged(oldSelected);
            notifyItemChanged(selectedIndex);
        }
        
        @NonNull
        @Override
        public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_route_option, parent, false);
            return new RouteViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
            if (routes != null && position < routes.size()) {
                DirectionsService.Route route = routes.get(position);
                holder.bind(route, position, position == selectedIndex);
            }
        }
        
        @Override
        public int getItemCount() {
            return routes != null ? routes.size() : 0;
        }
        
        class RouteViewHolder extends RecyclerView.ViewHolder {
            private final MaterialCardView cardView;
            private final TextView routeBadge;
            private final TextView routeDuration;
            private final TextView routeDistance;
            private final TextView routeDescription;
            private final ImageView selectionIndicator;
            
            public RouteViewHolder(@NonNull View itemView) {
                super(itemView);
                cardView = (MaterialCardView) itemView;
                routeBadge = itemView.findViewById(R.id.route_badge);
                routeDuration = itemView.findViewById(R.id.route_duration);
                routeDistance = itemView.findViewById(R.id.route_distance);
                routeDescription = itemView.findViewById(R.id.route_description);
                selectionIndicator = itemView.findViewById(R.id.selection_indicator);
                
                itemView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && position != selectedIndex) {
                        updateSelectedRoute(position);
                        if (listener != null) {
                            listener.onRouteSelected(position);
                        }
                    }
                });
            }
            
            public void bind(DirectionsService.Route route, int position, boolean isSelected) {
                routeBadge.setText(String.valueOf(position + 1));
                routeDuration.setText(route.getDuration() != null ? route.getDuration() : "N/A");
                routeDistance.setText(route.getDistance() != null ? route.getDistance() : "N/A");
                
                // Route description
                String description = route.getSummary();
                if (description == null || description.isEmpty()) {
                    if (position == 0) {
                        description = "Tuyến đường nhanh nhất";
                    } else {
                        description = "Tuyến đường thay thế " + (position);
                    }
                }
                routeDescription.setText(description);
                
                // Selection state
                selectionIndicator.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                
                // Card styling for selection
                if (isSelected) {
                    cardView.setStrokeColor(context.getColor(R.color.blue_500));
                    cardView.setStrokeWidth(2);
                } else {
                    cardView.setStrokeColor(context.getColor(R.color.gray_300));
                    cardView.setStrokeWidth(1);
                }            }
        }
    }
    
    /**
     * Chọn route theo index
     */
    public void selectRoute(int routeIndex) {
        if (routeAdapter != null && routeIndex >= 0 && routes != null && routeIndex < routes.size()) {
            selectedRouteIndex = routeIndex;
            routeAdapter.updateSelectedRoute(routeIndex);
        }
    }
}
