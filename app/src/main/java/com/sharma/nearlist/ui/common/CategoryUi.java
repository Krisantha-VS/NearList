package com.sharma.nearlist.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.model.Category;

/** Category → icon, accent colour, container colour and label (spec section 11). */
public final class CategoryUi {

    private CategoryUi() {
    }

    @DrawableRes
    public static int icon(Category category) {
        switch (category) {
            case GROCERY:
                return R.drawable.ic_category_grocery;
            case FUEL:
                return R.drawable.ic_category_fuel;
            case PHARMACY:
                return R.drawable.ic_category_pharmacy;
            default:
                return R.drawable.ic_category_other;
        }
    }

    /** Icon tint and notification colour. */
    @ColorRes
    public static int accent(Category category) {
        switch (category) {
            case GROCERY:
                return R.color.category_grocery;
            case FUEL:
                return R.color.category_fuel;
            case PHARMACY:
                return R.color.category_pharmacy;
            default:
                return R.color.category_other;
        }
    }

    /** Icon tile background. */
    @ColorRes
    public static int container(Category category) {
        switch (category) {
            case GROCERY:
                return R.color.category_grocery_container;
            case FUEL:
                return R.color.category_fuel_container;
            case PHARMACY:
                return R.color.category_pharmacy_container;
            default:
                return R.color.category_other_container;
        }
    }

    /** Text and icon on the container colour (selected category chip, screen 08). */
    @ColorRes
    public static int onContainer(Category category) {
        switch (category) {
            case GROCERY:
                return R.color.category_grocery_on_container;
            case FUEL:
                return R.color.category_fuel_on_container;
            case PHARMACY:
                return R.color.category_pharmacy_on_container;
            default:
                return R.color.category_other_on_container;
        }
    }

    @StringRes
    public static int label(Category category) {
        switch (category) {
            case GROCERY:
                return R.string.category_grocery;
            case FUEL:
                return R.string.category_fuel;
            case PHARMACY:
                return R.string.category_pharmacy;
            default:
                return R.string.category_other;
        }
    }

    /**
     * Single-choice category chip (task and place forms, style Widget.Nearlist.CategoryChip):
     * category-coloured icon when unselected, check mark on the category container when selected.
     */
    public static void styleChoiceChip(Chip chip, Category category) {
        Context context = chip.getContext();
        int[][] states = {{android.R.attr.state_checked}, {}};
        int container = ContextCompat.getColor(context, container(category));
        int onContainer = ContextCompat.getColor(context, onContainer(category));
        int unselectedText = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant);
        chip.setChipBackgroundColor(new ColorStateList(states, new int[]{container, Color.TRANSPARENT}));
        chip.setTextColor(new ColorStateList(states, new int[]{onContainer, unselectedText}));
        chip.setChipIconTint(ContextCompat.getColorStateList(context, accent(category)));
        chip.setCheckedIconTint(ColorStateList.valueOf(onContainer));
    }
}
