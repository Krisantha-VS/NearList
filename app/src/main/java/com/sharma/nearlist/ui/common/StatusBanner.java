package com.sharma.nearlist.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.sharma.nearlist.R;
import com.sharma.nearlist.databinding.ViewStatusBannerBinding;

/**
 * Reusable banner: icon, title, body, one action and an optional close button.
 * {@link Tone#ERROR} uses the error container with a filled error button (screens 05, 16);
 * {@link Tone#INFO} uses the secondary container with a text button (screen 09).
 */
public class StatusBanner extends LinearLayout {

    public enum Tone { ERROR, INFO }

    private final ViewStatusBannerBinding binding;
    private Tone tone = Tone.ERROR;

    public StatusBanner(@NonNull Context context) {
        this(context, null);
    }

    public StatusBanner(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_m);
        setPaddingRelative(padding, getResources().getDimensionPixelSize(R.dimen.space_xs),
                getResources().getDimensionPixelSize(R.dimen.space_xs),
                getResources().getDimensionPixelSize(R.dimen.space_xs));
        setBackgroundResource(R.drawable.shape_rounded_12);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        binding = ViewStatusBannerBinding.inflate(LayoutInflater.from(context), this);
        setTone(Tone.ERROR);
        setCloseVisible(false);
    }

    public void setTone(Tone tone) {
        this.tone = tone;
        boolean error = tone == Tone.ERROR;
        int background = color(error ? com.google.android.material.R.attr.colorErrorContainer
                : com.google.android.material.R.attr.colorSecondaryContainer);
        int content = color(error ? com.google.android.material.R.attr.colorOnErrorContainer
                : com.google.android.material.R.attr.colorOnSecondaryContainer);
        ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(background));
        ColorStateList contentList = ColorStateList.valueOf(content);
        binding.bannerIcon.setImageTintList(contentList);
        binding.bannerTitle.setTextColor(content);
        binding.bannerBody.setTextColor(content);
        binding.bannerClose.setIconTint(contentList);

        MaterialButton filled = binding.bannerActionFilled;
        filled.setBackgroundTintList(ColorStateList.valueOf(color(androidx.appcompat.R.attr.colorError)));
        ColorStateList onError = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnError));
        filled.setTextColor(onError);
        filled.setIconTint(onError);
        swapActionButtons();
    }

    public void setIcon(@DrawableRes int icon) {
        binding.bannerIcon.setImageResource(icon);
    }

    public void setTitle(CharSequence title) {
        binding.bannerTitle.setText(title);
    }

    public void setTitle(@StringRes int title) {
        binding.bannerTitle.setText(title);
    }

    public void setBody(CharSequence body) {
        binding.bannerBody.setText(body);
    }

    public void setBody(@StringRes int body) {
        binding.bannerBody.setText(body);
    }

    /** @param icon drawable shown before the label, or 0 for none */
    public void setAction(@StringRes int label, @DrawableRes int icon, @Nullable View.OnClickListener listener) {
        for (MaterialButton button : new MaterialButton[]{binding.bannerActionFilled, binding.bannerActionText}) {
            button.setText(label);
            if (icon != 0) {
                button.setIconResource(icon);
            } else {
                button.setIcon(null);
            }
            button.setOnClickListener(listener);
        }
    }

    public void setCloseVisible(boolean visible) {
        binding.bannerClose.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setOnCloseClickListener(@Nullable View.OnClickListener listener) {
        binding.bannerClose.setOnClickListener(listener);
    }

    private void swapActionButtons() {
        boolean error = tone == Tone.ERROR;
        binding.bannerActionFilled.setVisibility(error ? VISIBLE : GONE);
        binding.bannerActionText.setVisibility(error ? GONE : VISIBLE);
    }

    private int color(@AttrRes int attr) {
        return MaterialColors.getColor(this, attr);
    }
}
