package com.example.mobile_app.maps;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import java.util.List;
import java.util.Locale;

/**
 * InAppNavigationManager_New handles in-app navigation with Vietnamese TTS instructions.
 */
public class InAppNavigationManager_New implements TextToSpeech.OnInitListener {
    private static final String TAG = "InAppNavigationManager_New";

    private final Context context;
    private final GoogleMap googleMap;
    private TextToSpeech tts;
    private NavigationListener navigationListener;

    private List<DirectionsService.Route> currentRoute;
    private int currentStepIndex;
    private boolean isNavigating;

    private Handler handler;
    private Runnable navigationRunnable;

    public interface NavigationListener {
        void onNavigationStopped();
        void onInstructionChanged(String instruction, String distance);
        void onDestinationReached();
    }

    public InAppNavigationManager_New(@NonNull Context context, @NonNull GoogleMap googleMap) {
        this.context = context;
        this.googleMap = googleMap;
        this.tts = new TextToSpeech(context, this);
        this.isNavigating = false;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void setNavigationListener(NavigationListener listener) {
        this.navigationListener = listener;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("vi", "VN"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Vietnamese TTS language not supported");
            } else {
                Log.d(TAG, "Vietnamese TTS initialized");
            }
        } else {
            Log.e(TAG, "TTS initialization failed");
        }
    }

    public void startNavigation(DirectionsService.Route route, LatLng startLocation) {
        if (route == null || route.getPoints() == null || route.getPoints().isEmpty()) {
            Log.e(TAG, "Invalid route for navigation");
            return;
        }
        this.currentRoute = List.of(route);
        this.currentStepIndex = 0;
        this.isNavigating = true;

        speakInstruction("Bắt đầu chỉ đường");

        // Start navigation simulation or real tracking here
        startStepNavigation();
    }

    private void startStepNavigation() {
        if (!isNavigating || currentRoute == null || currentStepIndex >= currentRoute.size()) {
            stopNavigation();
            if (navigationListener != null) {
                navigationListener.onDestinationReached();
            }
            return;
        }

        // For demonstration, simulate step instructions with delay
        String instruction = "Đi thẳng 100 mét";
        String distance = "100 mét";

        if (navigationListener != null) {
            navigationListener.onInstructionChanged(instruction, distance);
        }
        speakInstruction(instruction);

        // Schedule next step after delay (simulate)
        navigationRunnable = () -> {
            currentStepIndex++;
            startStepNavigation();
        };
        handler.postDelayed(navigationRunnable, 5000); // 5 seconds per step simulation
    }

    public void stopNavigation() {
        if (!isNavigating) return;
        isNavigating = false;
        if (navigationRunnable != null) {
            handler.removeCallbacks(navigationRunnable);
        }
        speakInstruction("Đã dừng chỉ đường");
        if (navigationListener != null) {
            navigationListener.onNavigationStopped();
        }
    }

    private void speakInstruction(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NAV_INSTRUCTION");
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
