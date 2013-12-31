/*
 * Copyright (C) 2013, Antonio Mendes Silva
 */

package com.google.android.apps.dashclock.configuration;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.OpacityBar;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

import net.nurik.roman.dashclock.R;

/**
 * Dialog fragment that presents a color
 *
 * To use this interface the calling activity must implement the interface
 * SelectColorDialog.ColorPickerDialogListener
 */
public class ColorPickerDialog
        extends DialogFragment {
    private static final String TAG = ColorPickerDialog.class.getSimpleName();
    private static final String TITLE_KEY = "TITLE", INITIAL_COLOR_KEY = "INITIAL_COLOR";

    private ColorPickerPreference mPreference;

    /**
     * Create a new instance of the dialog, providing arguments.

     * @param title
     *        Title of the dialog
     * @param initialColor
     *        Initial color
     * @return
     *        New dialog
     */
    public static ColorPickerDialog newInstance(String title, int initialColor) {
        ColorPickerDialog dialog = new ColorPickerDialog();


        Bundle args = new Bundle();
        args.putString(TITLE_KEY, title);
        args.putInt(INITIAL_COLOR_KEY, initialColor);
        dialog.setArguments(args);

        return dialog;
    }

    public void setPreference(ColorPickerPreference preference) {
        mPreference = preference;
    }

    // Control which panel is being displayed
    private static final int PANEL_COLOR_CONTROLS = 0,
            PANEL_COLOR_VALUE = 1;
    private int mDisplayedPanel = PANEL_COLOR_CONTROLS;
    /**
     * Build the dialog
     *
     * @param savedInstanceState
     *        State
     *
     * @return
     *        Dialog to select color
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final String title = getArguments().getString(TITLE_KEY);
        final int initialColor = getArguments().getInt(INITIAL_COLOR_KEY);

        // Create the color picker views and connect them
        View mainColorPicker = getActivity().getLayoutInflater().inflate(R.layout.color_picker, null);

        final ColorPicker picker = (ColorPicker)mainColorPicker.findViewById(R.id.picker);
        SaturationBar saturationBar = (SaturationBar)mainColorPicker.findViewById(R.id.saturationbar);
        ValueBar valueBar = (ValueBar)mainColorPicker.findViewById(R.id.valuebar);
        OpacityBar opacityBar = (OpacityBar)mainColorPicker.findViewById(R.id.opacitybar);

        picker.addSaturationBar(saturationBar);
        picker.addValueBar(valueBar);
        picker.addOpacityBar(opacityBar);

        picker.setColor(initialColor);
        picker.setOldCenterColor(initialColor);
        picker.setNewCenterColor(initialColor);

        // Display the controls and hide the color value textbox
        final LinearLayout colorControlsWrapper = (LinearLayout)mainColorPicker.findViewById(R.id.color_controls_wrapper);
        colorControlsWrapper.setVisibility(View.VISIBLE);
        final LinearLayout colorValueWrapper = (LinearLayout)mainColorPicker.findViewById(R.id.color_value_wrapper);
        colorValueWrapper.setVisibility(View.INVISIBLE);

        final EditText colorValueText = (EditText)mainColorPicker.findViewById(R.id.color_value);
        colorValueText.setText(toHexString(initialColor).toUpperCase());

        // Setup copy and paste buttons
        ImageButton copyView = (ImageButton)mainColorPicker.findViewById(R.id.color_value_copy);
        copyView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager)getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("color_value", colorValueText.getText().toString()));
                //Toast.makeText(getActivity(), R.string.cfg_text_copied_clipboard, Toast.LENGTH_SHORT).show();
            }
        });

        ClipboardManager clipboard = (ClipboardManager)getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        ImageButton pasteView = (ImageButton)mainColorPicker.findViewById(R.id.color_value_paste);
        if (!clipboard.hasPrimaryClip()) {
            pasteView.setEnabled(false);
        } else {
            pasteView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ClipboardManager clipboard = (ClipboardManager)getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                    colorValueText.setText(item.getText());
                }
            });
        }

        // Setup color value textbox change listener

        final TextWatcher colorValueChangeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                String colorString = "#" + s.toString().trim();
                int color = 0;
                boolean validColor = true;
                try {
                    color = Color.parseColor(colorString);
                } catch (IllegalArgumentException exc) {
                    validColor = false;
                }

                if (!validColor) {
                    colorValueText.setTextColor(Color.RED);
                } else {
                    colorValueText.setTextColor(Color.BLACK);
                    // Notify picker. Remove colorChangeListener first so we don't go into a loop
                    ColorPicker.OnColorChangedListener listener = picker.getOnColorChangedListener();
                    picker.setOnColorChangedListener(null);
                    picker.setColor(color);
                    picker.setOnColorChangedListener(listener);
                }
            }
        };
        colorValueText.addTextChangedListener(colorValueChangeWatcher);

        // Switch between controls view and textbox view
        final long animDuration = 200;
        final float scale = 0.6f;
        picker.setOnCenterClickedListener(new ColorPicker.OnCenterClickedListener() {
            @Override
            public void onCenterClicked(int color) {
                //if (colorControlsWrapper.getVisibility() == View.VISIBLE) {
                if (mDisplayedPanel == PANEL_COLOR_CONTROLS) {
                    mDisplayedPanel = PANEL_COLOR_VALUE;

                    float val = colorControlsWrapper.getWidth();
                    colorControlsWrapper.animate().xBy(-val).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            colorControlsWrapper.setVisibility(View.INVISIBLE);
                        }
                    }).start();

                    colorValueWrapper.setX(val);
                    colorValueWrapper.setVisibility(View.VISIBLE);
                    colorValueWrapper.animate().xBy(-val).start();

                    //colorControlsWrapper.animate().scaleX(0.8f).scaleY(0.8f).alpha(0f).withEndAction(new Runnable() {
                    //    @Override
                    //    public void run() {
                    //        colorControlsWrapper.setVisibility(View.INVISIBLE);
                    //        colorValueWrapper.setScaleX(scale);
                    //        colorValueWrapper.setScaleY(scale);
                    //        colorValueWrapper.setAlpha(0f);
                    //        colorValueWrapper.setVisibility(View.VISIBLE);
                    //        colorValueWrapper.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(animDuration).start();
                    //    }
                    //}).setDuration(animDuration).start();

                    // Show color value editText
                    colorValueText.setText(toHexString(picker.getColor()).toUpperCase());
                    picker.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {
                        @Override
                        public void onColorChanged(int color) {
                            colorValueText.setText(toHexString(color).toUpperCase());
                        }
                    });
                } else {
                    mDisplayedPanel = PANEL_COLOR_CONTROLS;

                    float val = colorValueWrapper.getWidth();
                    colorValueWrapper.animate().xBy(val).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            colorValueWrapper.setVisibility(View.INVISIBLE);
                        }
                    }).start();

                    colorControlsWrapper.setX(-val);
                    colorControlsWrapper.setVisibility(View.VISIBLE);
                    colorControlsWrapper.animate().xBy(val).start();


                    //colorValueWrapper.animate().scaleX(scale).scaleY(scale).alpha(0f).withEndAction(new Runnable() {
                    //    @Override
                    //    public void run() {
                    //        colorValueWrapper.setVisibility(View.INVISIBLE);
                    //        colorControlsWrapper.setScaleX(scale);
                    //        colorControlsWrapper.setScaleY(scale);
                    //        colorControlsWrapper.setAlpha(0f);
                    //        colorControlsWrapper.setVisibility(View.VISIBLE);
                    //        colorControlsWrapper.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(animDuration).start();
                    //    }
                    //}).setDuration(animDuration).start();

                    // Force a color update
                    //picker.setColor(picker.getColor());
                    picker.setOnColorChangedListener(null);
                }
            }
        });


        // Create the dialog
        builder.setTitle(title)
               .setView(mainColorPicker)
               .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int which) {
                       mPreference.setValue(picker.getColor());
                   }
               })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

        return builder.create();
    }

    private String toHexString(int color) {
        String hexString = Integer.toHexString(color);
        if (hexString.length() < 8) {
            StringBuilder sb = new StringBuilder("00000000");
            sb.replace(8 - hexString.length(), 8, hexString);
            hexString = sb.toString();
        }
        return hexString;
    }
}
