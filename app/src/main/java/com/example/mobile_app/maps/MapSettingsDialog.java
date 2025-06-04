package com.example.mobile_app.maps;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.MapStyleOptions;

import com.example.mobile_app.R;

public class MapSettingsDialog extends BottomSheetDialog {
    private static final String TAG = "MapSettingsDialog";
    private final GoogleMap googleMap;
    private final SharedPreferences prefs;

    public MapSettingsDialog(@NonNull Context context, GoogleMap map, SharedPreferences prefs) {
        super(context);
        this.googleMap = map;
        this.prefs = prefs;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setupDialog();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up dialog", e);
            dismiss();
        }
    }    private void setupDialog() {
        View view = null;
        RadioGroup group = null;
        RadioButton darkModeButton = null;
        Switch trafficSwitch = null;
        Switch threeDSwitch = null;
        
        try {
            view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_map_settings, null);
            setContentView(view);
            
            // Prevent dialog from causing the app to freeze
            setCancelable(true);
            setCanceledOnTouchOutside(true);

            group = view.findViewById(R.id.map_type_group);
            darkModeButton = view.findViewById(R.id.map_type_dark);
            trafficSwitch = view.findViewById(R.id.switch_traffic);
            threeDSwitch = view.findViewById(R.id.switch_3d);

            if (group == null || darkModeButton == null || trafficSwitch == null || threeDSwitch == null) {
                Log.e(TAG, "Could not find required views in dialog layout");
                dismiss();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error inflating dialog layout", e);
            dismiss();
            return;
        }

        int savedMapType = prefs.getInt("map_type", GoogleMap.MAP_TYPE_NORMAL);
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        boolean isTrafficEnabled = prefs.getBoolean("traffic", false);
        boolean is3D = prefs.getBoolean("map_3d", false);

        applyMapSettings(savedMapType, isDarkMode, isTrafficEnabled, is3D);
        setUIState(group, darkModeButton, trafficSwitch, threeDSwitch, savedMapType, isDarkMode, isTrafficEnabled, is3D);
        setupListeners(group, trafficSwitch, threeDSwitch);
    }

    private void applyMapSettings(int mapType, boolean darkMode, boolean trafficEnabled, boolean threeD) {
        if (googleMap == null) return;

        try {
            googleMap.setMapType(mapType);
            googleMap.setTrafficEnabled(trafficEnabled);

            if (darkMode) {
                MapStyleOptions darkStyle = MapStyleOptions.loadRawResourceStyle(getContext(), R.raw.map_style_dark);
                googleMap.setMapStyle(darkStyle);
            } else {
                googleMap.setMapStyle(null);
            }

            float tilt = threeD ? 45f : 0f;
            com.google.android.gms.maps.model.CameraPosition current = googleMap.getCameraPosition();
            googleMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(
                    new com.google.android.gms.maps.model.CameraPosition.Builder(current)
                            .tilt(tilt)
                            .build()
            ));
        } catch (Exception e) {
            Log.e(TAG, "Error applying map settings", e);
        }
    }

    private void setUIState(RadioGroup group, RadioButton darkModeButton, Switch trafficSwitch,
                           Switch threeDSwitch, int mapType, boolean darkMode, boolean trafficEnabled, boolean threeD) {
        if (darkMode) {
            darkModeButton.setChecked(true);
        } else {
            switch (mapType) {
                case GoogleMap.MAP_TYPE_SATELLITE:
                    group.check(R.id.map_type_satellite);
                    break;
                case GoogleMap.MAP_TYPE_TERRAIN:
                    group.check(R.id.map_type_terrain);
                    break;
                case GoogleMap.MAP_TYPE_HYBRID:
                    group.check(R.id.map_type_hybrid);
                    break;
                default:
                    group.check(R.id.map_type_normal);
            }
        }

        trafficSwitch.setChecked(trafficEnabled);
        threeDSwitch.setChecked(threeD);
    }

    private void setupListeners(RadioGroup group, Switch trafficSwitch, Switch threeDSwitch) {
        trafficSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                if (googleMap != null) {
                    googleMap.setTrafficEnabled(isChecked);
                }
                prefs.edit().putBoolean("traffic", isChecked).apply();
            } catch (Exception e) {
                Log.e(TAG, "Error toggling traffic", e);
            }
        });

        threeDSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("map_3d", isChecked).apply();
            applyMapSettings(prefs.getInt("map_type", GoogleMap.MAP_TYPE_NORMAL),
                             prefs.getBoolean("dark_mode", false),
                             trafficSwitch.isChecked(),
                             isChecked);
        });

        group.setOnCheckedChangeListener((g, checkedId) -> {
            try {
                int mapType = GoogleMap.MAP_TYPE_NORMAL;
                boolean darkMode = false;

                if (checkedId == R.id.map_type_satellite) {
                    mapType = GoogleMap.MAP_TYPE_SATELLITE;
                } else if (checkedId == R.id.map_type_terrain) {
                    mapType = GoogleMap.MAP_TYPE_TERRAIN;
                } else if (checkedId == R.id.map_type_hybrid) {
                    mapType = GoogleMap.MAP_TYPE_HYBRID;
                } else if (checkedId == R.id.map_type_dark) {
                    mapType = GoogleMap.MAP_TYPE_NORMAL;
                    darkMode = true;
                }

                applyMapSettings(mapType, darkMode, trafficSwitch.isChecked(), threeDSwitch.isChecked());

                prefs.edit()
                     .putInt("map_type", mapType)
                     .putBoolean("dark_mode", darkMode)
                     .apply();

            } catch (Exception e) {
                Log.e(TAG, "Error changing map type", e);
            }
        });
    }
}
