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

import com.sharma.nearlist.databinding.FragmentOnboardingNotificationsBinding;

/**
 * Step 4 (API 33+ only): POST_NOTIFICATIONS. Either answer, or Skip, finishes
 * onboarding; the Home banner and Settings show what is missing.
 */
public class NotificationsStepFragment extends Fragment {

    private final ActivityResultLauncher<String> requestNotifications = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> done());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FragmentOnboardingNotificationsBinding binding =
                FragmentOnboardingNotificationsBinding.inflate(inflater, container, false);
        binding.allowNotifications.setOnClickListener(v ->
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS));
        binding.skip.setOnClickListener(v -> done());
        return binding.getRoot();
    }

    private void done() {
        ((OnboardingActivity) requireActivity()).onStepDone(OnboardingActivity.Step.NOTIFICATIONS);
    }
}
