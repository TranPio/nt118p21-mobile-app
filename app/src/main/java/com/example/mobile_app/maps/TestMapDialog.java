package com.example.mobile_app.maps;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.gms.maps.GoogleMap;

/**
 * Test dialog
 */
public class TestMapDialog extends BottomSheetDialog {
    private final GoogleMap googleMap;
    private final SharedPreferences prefs;

    public TestMapDialog(@NonNull Context context, GoogleMap map, SharedPreferences prefs) {
        super(context);
        this.googleMap = map;
        this.prefs = prefs;
    }
}
