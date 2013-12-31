/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.dashclock.configuration;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.apps.dashclock.Utils;

import net.nurik.roman.dashclock.R;

/**
 * A preference that allows the user to choose a color
 */
public class ColorPickerPreference extends Preference {
    private int mValue = 0;
    private int mItemLayoutId = R.layout.grid_item_color;
    private View mPreviewView;
    private Context mContext;

    public ColorPickerPreference(Context context) {
        super(context);
        initAttrs(null, 0);
        mContext = context;
    }

    public ColorPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttrs(attrs, 0);
        mContext = context;
    }

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initAttrs(attrs, defStyle);
        mContext = context;
    }

    private void initAttrs(AttributeSet attrs, int defStyle) {
        setWidgetLayoutResource(mItemLayoutId);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mPreviewView = view.findViewById(R.id.color_view);
        setColorViewValue(mPreviewView, mValue, false);
    }

    public void setValue(int value) {
        if (callChangeListener(value)) {
            mValue = value;
            persistInt(value);
            notifyChanged();
        }
    }

    @Override
    protected void onClick() {
        super.onClick();

        String title = (this.getTitle() != null) ? this.getTitle().toString() :
                mContext.getString(this.getTitleRes());

        ColorPickerDialog fragment = ColorPickerDialog.newInstance(title, mValue);
        fragment.setPreference(this);

        Activity activity = (Activity) getContext();
        activity.getFragmentManager().beginTransaction()
                .add(fragment, getFragmentTag())
                .commit();
    }

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();

        Activity activity = (Activity) getContext();
        ColorPickerDialog fragment = (ColorPickerDialog) activity
                .getFragmentManager().findFragmentByTag(getFragmentTag());
        if (fragment != null) {
            // re-bind preference to fragment
            fragment.setPreference(this);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedInt(0) : (Integer) defaultValue);
    }

    public String getFragmentTag() {
        return "color_" + getKey();
    }

    public int getValue() {
        return mValue;
    }

    private static void setColorViewValue(View view, int color, boolean selected) {
        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            Resources res = imageView.getContext().getResources();

            Drawable currentDrawable = imageView.getDrawable();
            GradientDrawable colorChoiceDrawable;
            if (currentDrawable != null && currentDrawable instanceof GradientDrawable) {
                // Reuse drawable
                colorChoiceDrawable = (GradientDrawable) currentDrawable;
            } else {
                colorChoiceDrawable = new GradientDrawable();
                colorChoiceDrawable.setShape(GradientDrawable.OVAL);
            }

            // Set stroke to dark version of color
            int darkenedColor = Color.rgb(
                    Color.red(color) * 192 / 256,
                    Color.green(color) * 192 / 256,
                    Color.blue(color) * 192 / 256);

            colorChoiceDrawable.setColor(color);
            colorChoiceDrawable.setStroke((int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1, res.getDisplayMetrics()), darkenedColor);

            Drawable drawable = colorChoiceDrawable;
            if (selected) {
                drawable = new LayerDrawable(new Drawable[]{
                        colorChoiceDrawable,
                        res.getDrawable(Utils.isColorDark(color)
                                ? R.drawable.checkmark_white
                                : R.drawable.checkmark_black)
                });
            }

            imageView.setImageDrawable(drawable);

        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        }
    }
}
