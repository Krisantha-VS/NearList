package com.sharma.nearlist.ui.onboarding;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.color.MaterialColors;
import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.R;
import com.sharma.nearlist.databinding.ActivityOnboardingBinding;
import com.sharma.nearlist.location.GeofenceManager;
import com.sharma.nearlist.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * First-run flow: Welcome → Location → Background (API 29+) →
 * Notifications (API 33+) → Home. Steps that don't apply to the device's API level are left out,
 * so the indicator reads "of 3" or "of 2" on older devices. The pager is not swipeable; each step
 * advances by calling {@link #onStepDone(Step)}.
 */
public class OnboardingActivity extends AppCompatActivity {

    /** Onboarding steps in display order. */
    public enum Step {
        WELCOME(R.string.onboarding_welcome_label),
        LOCATION(R.string.onboarding_location_label),
        BACKGROUND(R.string.onboarding_background_label),
        NOTIFICATIONS(R.string.onboarding_notifications_label);

        @StringRes
        final int label;

        Step(@StringRes int label) {
            this.label = label;
        }
    }

    private ActivityOnboardingBinding binding;
    private List<Step> steps;
    private final List<View> segments = new ArrayList<>();
    private boolean finishing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        steps = visibleSteps();
        buildProgressSegments();

        ViewPager2 pager = binding.pager;
        pager.setUserInputEnabled(false);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new StepAdapter(this, steps));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showIndicator(position);
            }
        });
        showIndicator(pager.getCurrentItem());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                int current = binding.pager.getCurrentItem();
                if (current > 0) {
                    binding.pager.setCurrentItem(current - 1, true);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // ViewPager2 has restored its page by now (e.g. after rotation).
        showIndicator(binding.pager.getCurrentItem());
    }

    /**
     * Called by a step when it is complete. Ignored unless that step is the one on screen, so a
     * permission callback and an onResume re-check can't advance twice.
     */
    public void onStepDone(Step step) {
        int position = steps.indexOf(step);
        if (position < 0 || position != binding.pager.getCurrentItem() || finishing) {
            return;
        }
        if (position == steps.size() - 1) {
            finishOnboarding();
        } else {
            binding.pager.setCurrentItem(position + 1, true);
        }
    }

    private void finishOnboarding() {
        finishing = true;
        NearlistApp.from(this).getSettingsRepository().setOnboardingDone(true);
        GeofenceManager.syncAll(this);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private static List<Step> visibleSteps() {
        List<Step> list = new ArrayList<>();
        list.add(Step.WELCOME);
        list.add(Step.LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(Step.BACKGROUND);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Step.NOTIFICATIONS);
        }
        return list;
    }

    private void buildProgressSegments() {
        int gap = getResources().getDimensionPixelSize(R.dimen.space_xs);
        for (int i = 0; i < steps.size(); i++) {
            View segment = new View(this);
            segment.setBackgroundResource(R.drawable.shape_pill);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) {
                params.setMarginStart(gap);
            }
            binding.progressSegments.addView(segment, params);
            segments.add(segment);
        }
    }

    private void showIndicator(int position) {
        int number = position + 1;
        binding.stepCounter.setText(getString(R.string.onboarding_step_counter, number, steps.size()));
        binding.stepName.setText(steps.get(position).label);
        binding.progressSegments.setContentDescription(
                getString(R.string.onboarding_progress_description, number, steps.size()));
        View root = binding.getRoot();
        ColorStateList done = ColorStateList.valueOf(
                MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary));
        ColorStateList todo = ColorStateList.valueOf(
                MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceContainerHighest));
        for (int i = 0; i < segments.size(); i++) {
            ViewCompat.setBackgroundTintList(segments.get(i), i <= position ? done : todo);
        }
    }

    private static final class StepAdapter extends FragmentStateAdapter {

        private final List<Step> steps;

        StepAdapter(@NonNull AppCompatActivity activity, List<Step> steps) {
            super(activity);
            this.steps = steps;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (steps.get(position)) {
                case LOCATION:
                    return new LocationStepFragment();
                case BACKGROUND:
                    return new BackgroundStepFragment();
                case NOTIFICATIONS:
                    return new NotificationsStepFragment();
                default:
                    return new WelcomeFragment();
            }
        }

        @Override
        public int getItemCount() {
            return steps.size();
        }
    }
}
