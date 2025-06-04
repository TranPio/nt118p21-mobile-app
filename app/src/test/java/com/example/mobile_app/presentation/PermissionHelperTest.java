package com.example.mobile_app.presentation;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import org.junit.Test;
import org.mockito.Mockito;

/** Simple test for PermissionHelper. */
public class PermissionHelperTest {
    @Test
    public void requestPermission_returnsTrueWhenGranted() {
        Activity activity = Mockito.mock(Activity.class);
        Mockito.when(ContextCompat.checkSelfPermission(activity, "perm"))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        assertTrue(PermissionHelper.requestPermission(activity, "perm", 1));
    }
}
