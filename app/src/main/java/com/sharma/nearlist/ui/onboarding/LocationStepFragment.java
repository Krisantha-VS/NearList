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

import com.sharma.nearlist.R;
import com.sharma.nearlist.databinding.FragmentOnboardingLocationBinding;
import com.sharma.nearlist.util.PermissionHelper;

import java.util.Map;

/**
 * Step 2 (screen 02): fine + coarse location, requested together. Precise location is required,
 * because geofencing needs ACCESS_FINE_LOCATION. On denial the step explains and offers a retry;
 * when the system will no longer show the dialog it offers app settings instead.
 */
public class LocationStepFragment extends Fragment {

    private static final String STATE_STATUS = "status";

    private static final int STATUS_NONE = 0;
    private static final int STATUS_DENIED = 1;
    private static final int STATUS_APPROXIMATE = 2;
    private static final int STATUS_BLOCKED = 3;

    private FragmentOnboardingLocationBinding binding;
    private int status = STATUS_NONE;

    private final ActivityResultLauncher<String[]> requestLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingLocationBinding.inflate(inflater, container, false);
        if (savedInstanceState != null) {
            status = savedInstanceState.getInt(STATE_STATUS, STATUS_NONE);
        }
        binding.allowLocation.setOnClickListener(v -> {
            if (status == STATUS_BLOCKED) {
                startActivity(PermissionHelper.appDetailsSettingsIntent(requireContext()));
            } else {
                requestLocation.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
        render();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Also covers returning from app settings after unblocking the permission.
        if (PermissionHelper.hasFineLocation(requireContext())) {
            ((OnboardingActivity) requireActivity()).onStepDone(OnboardingActivity.Step.LOCATION);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STATUS, status);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void onPermissionResult(Map<String, Boolean> result) {
        if (PermissionHelper.hasFineLocation(requireContext())) {
            ((OnboardingActivity) requireActivity()).onStepDone(OnboardingActivity.Step.LOCATION);
            return;
        }
        if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Denied with "don't ask again" (or twice on API 30+): the dialog won't appear any more.
            status = STATUS_BLOCKED;
        } else if (PermissionHelper.hasCoarseLocation(requireContext())) {
            status = STATUS_APPROXIMATE;
        } else {
            status = STATUS_DENIED;
        }
        render();
    }

    private void render() {
        if (binding == null) {
            return;
        }
        int message;
        switch (status) {
            case STATUS_DENIED:
                message = R.string.onboarding_location_denied;
                break;
            case STATUS_APPROXIMATE:
                message = R.string.onboarding_location_approximate;
                break;
            case STATUS_BLOCKED:
                message = R.string.onboarding_location_blocked;
                break;
            default:
                message = 0;
        }
        binding.deniedMessage.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) {
            binding.deniedMessage.setText(message);
        }
        boolean blocked = status == STATUS_BLOCKED;
        binding.allowLocation.setText(blocked ? R.string.onboarding_open_settings : R.string.onboarding_location_action);
        binding.allowLocation.setIconResource(blocked ? R.drawable.ic_settings : R.drawable.ic_my_location);
    }
}
