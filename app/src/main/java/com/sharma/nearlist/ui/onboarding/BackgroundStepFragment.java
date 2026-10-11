package com.sharma.nearlist.ui.onboarding;

import android.Manifest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.sharma.nearlist.databinding.FragmentOnboardingBackgroundBinding;
import com.sharma.nearlist.util.PermissionHelper;

/**
 * Step 3 (screen 03, API 29+ only): "Allow all the time", required for geofence reminders.
 * The first tap requests ACCESS_BACKGROUND_LOCATION (on API 30+ the system opens the settings
 * page itself). If that was denied or showed nothing, later taps open the app's settings page.
 * Returning with the permission granted advances automatically; "Not now" continues without it.
 */
public class BackgroundStepFragment extends Fragment {

    private static final String STATE_REQUESTED = "requested";

    private boolean requested;

    private final ActivityResultLauncher<String> requestBackground = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                requested = true;
                if (granted) {
                    done();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FragmentOnboardingBackgroundBinding binding =
                FragmentOnboardingBackgroundBinding.inflate(inflater, container, false);
        if (savedInstanceState != null) {
            requested = savedInstanceState.getBoolean(STATE_REQUESTED, false);
        }
        binding.openSettings.setOnClickListener(v -> {
            if (requested) {
                startActivity(PermissionHelper.appDetailsSettingsIntent(requireContext()));
            } else {
                requestBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
        });
        binding.notNow.setOnClickListener(v -> done());
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (PermissionHelper.hasBackgroundLocation(requireContext())) {
            done();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_REQUESTED, requested);
    }

    private void done() {
        ((OnboardingActivity) requireActivity()).onStepDone(OnboardingActivity.Step.BACKGROUND);
    }
}
