package com.sharma.nearlist.ui.onboarding;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.sharma.nearlist.databinding.FragmentOnboardingWelcomeBinding;

/** Step 1: welcome. */
public class WelcomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FragmentOnboardingWelcomeBinding binding =
                FragmentOnboardingWelcomeBinding.inflate(inflater, container, false);
        binding.sampleDone.setPaintFlags(binding.sampleDone.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        binding.getStarted.setOnClickListener(v ->
                ((OnboardingActivity) requireActivity()).onStepDone(OnboardingActivity.Step.WELCOME));
        return binding.getRoot();
    }
}
