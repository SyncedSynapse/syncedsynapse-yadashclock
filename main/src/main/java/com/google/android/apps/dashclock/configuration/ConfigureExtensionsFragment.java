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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.apps.dashclock.ExtensionHost;
import com.google.android.apps.dashclock.ExtensionManager;
import com.google.android.apps.dashclock.LogUtils;
import com.google.android.apps.dashclock.Utils;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.ui.SwipeDismissListViewTouchListener;
import com.google.android.apps.dashclock.ui.UndoBarController;

import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;

import net.nurik.roman.dashclock.R;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.android.apps.dashclock.ExtensionManager.ExtensionListing;
import static com.google.android.apps.dashclock.LogUtils.LOGD;
import static com.google.android.apps.dashclock.LogUtils.LOGE;
import static com.google.android.apps.dashclock.LogUtils.LOGW;

/**
 * Fragment for allowing the user to configure active extensions, shown within a {@link
 * ConfigurationActivity}.
 */
public class ConfigureExtensionsFragment extends Fragment implements
        ExtensionManager.OnChangeListener,
        AdapterView.OnItemClickListener,
        UndoBarController.UndoListener {
    private static final String TAG = LogUtils.makeLogTag(ConfigureExtensionsFragment.class);

    private static final String SAVE_KEY_SELECTED_EXTENSIONS = "selected_extensions";
    private static final String SAVE_KEY_EXTENSIONS_RENDER_OPTIONS = "extensions_render_options";

    private ExtensionManager mExtensionManager;

    private List<ComponentName> mSelectedExtensions = new ArrayList<ComponentName>();
    private ExtensionListAdapter mSelectedExtensionsAdapter;

    private Map<ComponentName, ExtensionManager.ExtensionRenderOptions> mSelectedExtensionsRenderOptions
            = new HashMap<ComponentName, ExtensionManager.ExtensionRenderOptions>();

    private Map<ComponentName, ExtensionManager.ExtensionListing> mExtensionListings
            = new HashMap<ComponentName, ExtensionManager.ExtensionListing>();
    private List<ComponentName> mAvailableExtensions = new ArrayList<ComponentName>();
    private PopupMenu mAddExtensionPopupMenu;

    private BroadcastReceiver mPackageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            repopulateAvailableExtensions();
        }
    };

    private DragSortListView mListView;
    private SwipeDismissListViewTouchListener mSwipeDismissTouchListener;
    private UndoBarController mUndoBarController;

    public ConfigureExtensionsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up helper components
        mExtensionManager = ExtensionManager.getInstance(getActivity());
        mExtensionManager.addOnChangeListener(this);

        if (savedInstanceState == null) {
            mSelectedExtensions = mExtensionManager.getActiveExtensionNames();
            mSelectedExtensionsRenderOptions = mExtensionManager.getActiveExtensionsRenderOptions();
        } else {
            List<String> selected = savedInstanceState
                    .getStringArrayList(SAVE_KEY_SELECTED_EXTENSIONS);
            List<String> selectedRenderOptions =
                    savedInstanceState.getStringArrayList(SAVE_KEY_EXTENSIONS_RENDER_OPTIONS);
            if (selected != null) {
                for (int i = 0; i < selected.size(); ++i) {
                    ComponentName cn = ComponentName.unflattenFromString(selected.get(i));
                    mSelectedExtensions.add(cn);

                    // Load render options
                    // Caution: we are assuming these were saved in the same order as the extensions
                    ExtensionManager.ExtensionRenderOptions renderOptions =
                            new ExtensionManager.ExtensionRenderOptions();
                    if (selectedRenderOptions != null) {
                        String jsonObj = selectedRenderOptions.get(i);
                        try {
                            renderOptions.deserialize((JSONObject) new JSONTokener(jsonObj).nextValue());
                        } catch (JSONException e) {
                            LOGE(TAG, "Error loading extension options for " + cn + ".", e);
                        }
                    }
                    mSelectedExtensionsRenderOptions.put(cn, renderOptions);
                }
            }

        }

        mSelectedExtensionsAdapter = new ExtensionListAdapter();

        repopulateAvailableExtensions();

        IntentFilter packageChangeIntentFilter = new IntentFilter();
        packageChangeIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageChangeIntentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageChangeIntentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageChangeIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageChangeIntentFilter.addDataScheme("package");
        getActivity().registerReceiver(mPackageChangedReceiver, packageChangeIntentFilter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_configure_extensions, container, false);

        mListView = (DragSortListView) rootView.findViewById(android.R.id.list);
        mListView.setAdapter(mSelectedExtensionsAdapter);
        mListView.setEmptyView(rootView.findViewById(android.R.id.empty));

        final DragSortController dragSortController = new ConfigurationDragSortController();
        mListView.setFloatViewManager(dragSortController);
        mListView.setDropListener(new DragSortListView.DropListener() {
            @Override
            public void drop(int from, int to) {
                ComponentName name = mSelectedExtensions.remove(from);
                mSelectedExtensions.add(to, name);
                mSelectedExtensionsAdapter.notifyDataSetChanged();
            }
        });
        mSwipeDismissTouchListener = new SwipeDismissListViewTouchListener(
                mListView,
                new SwipeDismissListViewTouchListener.DismissCallbacks() {
                    public boolean canDismiss(int position) {
                        return position < mSelectedExtensionsAdapter.getCount() - 1;
                    }

                    public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                        showRemoveUndoBar(reverseSortedPositions);
                        for (int position : reverseSortedPositions) {
                            mSelectedExtensions.remove(position);
                        }
                        repopulateAvailableExtensions();
                        mSelectedExtensionsAdapter.notifyDataSetChanged();
                    }
                });
        mListView.setOnItemClickListener(this);
        mListView.setOnScrollListener(mSwipeDismissTouchListener.makeScrollListener());
        mListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    mUndoBarController.hideUndoBar(false);
                }

                return dragSortController.onTouch(view, motionEvent)
                        || (!dragSortController.isDragging()
                        && mSwipeDismissTouchListener.onTouch(view, motionEvent));

            }
        });

        mListView.setItemsCanFocus(true);

        rootView.findViewById(R.id.empty_add_extension_button).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showAddExtensionMenu(view);
                    }
                });

        mUndoBarController = new UndoBarController(rootView.findViewById(R.id.undobar), this);
        mUndoBarController.onRestoreInstanceState(savedInstanceState);

        return rootView;
    }

    @Override
    public void onPause() {
        super.onPause();
        mExtensionManager.setActiveExtensions(mSelectedExtensions, mSelectedExtensionsRenderOptions);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mPackageChangedReceiver);
        mExtensionManager.removeOnChangeListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> selectedExtensions = new ArrayList<String>();
        ArrayList<String> selectedExtensionsRenderOptions = new ArrayList<String>();
        for (ComponentName cn : mSelectedExtensions) {
            selectedExtensions.add(cn.flattenToString());

            // Save render options in the same order
            ExtensionManager.ExtensionRenderOptions renderOptions = mSelectedExtensionsRenderOptions.get(cn);
            if (renderOptions != null) {
                try {
                    selectedExtensionsRenderOptions.add(renderOptions.serialize().toString());
                } catch (JSONException e) {
                    LOGE(TAG, "Error sanving extension options for " + cn + ".", e);
                }
            }
        }
        outState.putStringArrayList(SAVE_KEY_SELECTED_EXTENSIONS, selectedExtensions);
        outState.putStringArrayList(SAVE_KEY_EXTENSIONS_RENDER_OPTIONS, selectedExtensionsRenderOptions);
        if (mUndoBarController != null) {
            mUndoBarController.onSaveInstanceState(outState);
        }
    }

    private void repopulateAvailableExtensions() {
        Set<ComponentName> selectedExtensions = new HashSet<ComponentName>();
        selectedExtensions.addAll(mSelectedExtensions);
        boolean selectedExtensionsDirty = false;

        for (ExtensionListing listing : mExtensionListings.values()) {
            if (listing.icon != null) {
                //listing.icon.recycle();
                // TODO: recycling causes crashes with the ListView :-(
                listing.icon = null;
            }
        }

        mExtensionListings.clear();
        mAvailableExtensions.clear();

        Resources res = getResources();

        for (ExtensionListing listing : mExtensionManager.getAvailableExtensions()) {
            mExtensionListings.put(listing.componentName, listing);

            if (listing.icon != null) {
                Bitmap icon = Utils.flattenExtensionIcon(listing.icon,
                        res.getColor(R.color.extension_list_item_color));
                listing.icon = (icon != null) ? new BitmapDrawable(res, icon) : null;
            }

            if (selectedExtensions.contains(listing.componentName)) {
                if (!ExtensionHost.supportsProtocolVersion(listing.protocolVersion)) {
                    // If the extension is selected and its protocol isn't supported,
                    // then make it available but remove it from the selection.
                    mSelectedExtensions.remove(listing.componentName);
                    selectedExtensionsDirty = true;
                } else {
                    // If it's selected and supported, don't add it to the list of available
                    // extensions.
                    continue;
                }
            }

            mAvailableExtensions.add(listing.componentName);
        }

        Collections.sort(mAvailableExtensions, new Comparator<ComponentName>() {
            @Override
            public int compare(ComponentName cn1, ComponentName cn2) {
                ExtensionListing listing1 = mExtensionListings.get(cn1);
                ExtensionListing listing2 = mExtensionListings.get(cn2);
                return listing1.title.compareToIgnoreCase(listing2.title);
            }
        });

        if (selectedExtensionsDirty && mSelectedExtensionsAdapter != null) {
            mSelectedExtensionsAdapter.notifyDataSetChanged();
        }

        if (mAddExtensionPopupMenu != null) {
            mAddExtensionPopupMenu.dismiss();
            mAddExtensionPopupMenu = null;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> listView, View view, int position, long id) {
        if (id == -1) {
            showAddExtensionMenu(view.findViewById(R.id.add_extension_label));
        }
    }

    private void showAddExtensionMenu(View anchorView) {
        if (mAddExtensionPopupMenu != null) {
            mAddExtensionPopupMenu.dismiss();
        }

        mAddExtensionPopupMenu = new PopupMenu(getActivity(), anchorView);

        for (int i = 0; i < mAvailableExtensions.size(); i++) {
            ComponentName cn = mAvailableExtensions.get(i);
            ExtensionListing listing = mExtensionListings.get(cn);
            String label = (listing == null) ? null : listing.title;
            if (TextUtils.isEmpty(label)) {
                label = cn.flattenToShortString();
            }

            if (listing != null
                    && !ExtensionHost.supportsProtocolVersion(listing.protocolVersion)) {
                label = getString(R.string.incompatible_extension_menu_template, label);
            }

            mAddExtensionPopupMenu.getMenu().add(Menu.NONE, i, Menu.NONE, label);
        }
        mAddExtensionPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                mAddExtensionPopupMenu.dismiss();
                mAddExtensionPopupMenu = null;

                ComponentName cn = mAvailableExtensions.get(menuItem.getItemId());
                ExtensionListing extensionListing = mExtensionListings.get(cn);
                if (extensionListing == null
                        || !ExtensionHost.supportsProtocolVersion(
                        extensionListing.protocolVersion)) {
                    String title = cn.getShortClassName();
                    if (extensionListing != null) {
                        title = extensionListing.title;
                    }
                    CantAddExtensionDialog
                            .createInstance(cn.getPackageName(), title)
                            .show(getFragmentManager(), "cantaddextension");
                    return true;
                }

                // Item id == position for this popup menu.
                mSelectedExtensions.add(cn);
                repopulateAvailableExtensions();
                mSelectedExtensionsAdapter.notifyDataSetChanged();
                mListView.smoothScrollToPosition(mSelectedExtensionsAdapter.getCount() - 1);
                return true;
            }
        });
        mAddExtensionPopupMenu.show();
    }

    private class ConfigurationDragSortController extends DragSortController {
        private int mPos;

        public ConfigurationDragSortController() {
            super(ConfigureExtensionsFragment.this.mListView, R.id.drag_handle,
                    DragSortController.ON_DOWN, 0);
            setRemoveEnabled(false);
        }

        @Override
        public int startDragPosition(MotionEvent ev) {
            int res = super.dragHandleHitPosition(ev);
            if (res >= mSelectedExtensionsAdapter.getCount() - 1) {
                return DragSortController.MISS;
            }

            return res;
        }

        @Override
        public View onCreateFloatView(int position) {
            Vibrator v = (Vibrator) ConfigureExtensionsFragment.this.mListView
                    .getContext().getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(10);
            mPos = position;
            return mSelectedExtensionsAdapter.getView(position, null, mListView);
        }

        private int origHeight = -1;

        @Override
        public void onDragFloatView(View floatView, Point floatPoint, Point touchPoint) {
            final int addPos = mSelectedExtensionsAdapter.getCount() - 1;
            final int first = mListView.getFirstVisiblePosition();
            final int lvDivHeight = mListView.getDividerHeight();

            if (origHeight == -1) {
                origHeight = floatView.getHeight();
            }

            View div = mListView.getChildAt(addPos - first);

            if (touchPoint.x > mListView.getWidth() / 2) {
                float scale = touchPoint.x - mListView.getWidth() / 2;
                scale /= (float) (mListView.getWidth() / 5);
                ViewGroup.LayoutParams lp = floatView.getLayoutParams();
                lp.height = Math.max(origHeight, (int) (scale * origHeight));
                //Log.d("mobeta", "setting height " + lp.height);
                floatView.setLayoutParams(lp);
            }

            if (div != null) {
                if (mPos > addPos) {
                    // don't allow floating View to go above
                    // section divider
                    final int limit = div.getBottom() + lvDivHeight;
                    if (floatPoint.y < limit) {
                        floatPoint.y = limit;
                    }
                } else {
                    // don't allow floating View to go below
                    // section divider
                    final int limit = div.getTop() - lvDivHeight - floatView.getHeight();
                    if (floatPoint.y > limit) {
                        floatPoint.y = limit;
                    }
                }
            }
        }

        @Override
        public void onDestroyFloatView(View floatView) {
            //do nothing; block super from crashing
        }
    }

    @Override
    public void onExtensionsChanged(ComponentName sourceExtension) {
        repopulateAvailableExtensions();
    }

    private void showRemoveUndoBar(int[] reverseSortedPositions) {
        String undoString;
        if (reverseSortedPositions.length == 1) {
            ExtensionListing listing = mExtensionListings.get(
                    mSelectedExtensions.get(reverseSortedPositions[0]));
            undoString = getString(R.string.extension_removed_template,
                    (listing != null) ? listing.title : "??");
        } else {
            undoString = getString(R.string.extensions_removed_template,
                    reverseSortedPositions.length);
        }

        ComponentName[] extensions = new ComponentName[reverseSortedPositions.length];
        String[] jsonRenderOptions = new String[reverseSortedPositions.length];
        for (int i = 0; i < reverseSortedPositions.length; i++) {
            extensions[i] = mSelectedExtensions.get(reverseSortedPositions[i]);

            // Save render options
            try {
                ExtensionManager.ExtensionRenderOptions renderOptions = mSelectedExtensionsRenderOptions.get(extensions[i]);
                if (renderOptions != null) {
                    jsonRenderOptions[i] = renderOptions.serialize().toString();
                }
            } catch (JSONException e) {
                LOGE(TAG, "Error saving undo options for " + extensions[i] + ".", e);
            }
        }

        Bundle undoBundle = new Bundle();
        undoBundle.putIntArray("positions", reverseSortedPositions);
        undoBundle.putParcelableArray("extensions", extensions);
        undoBundle.putStringArray("render_options", jsonRenderOptions);
        mUndoBarController.showUndoBar(
                false,
                undoString,
                undoBundle);
    }

    @Override
    public void onUndo(Bundle token) {
        if (token == null) {
            return;
        }

        // Perform the undo
        int[] reverseSortedPositions = token.getIntArray("positions");
        ComponentName[] extensions = (ComponentName[]) token.getParcelableArray("extensions");
        String[] jsonRenderOptions = (String[])token.getStringArray("render_options");

        for (int i = 0; i < reverseSortedPositions.length; i++) {
            mSelectedExtensions.add(reverseSortedPositions[i], extensions[i]);

            String jsonObj = jsonRenderOptions[i];
            ExtensionManager.ExtensionRenderOptions renderOptions = new ExtensionManager.ExtensionRenderOptions();
            try {
                renderOptions.deserialize((JSONObject) new JSONTokener(jsonObj).nextValue());
            } catch (JSONException e) {
                LOGE(TAG, "Error loading extension options for " + extensions[i] + ".", e);
            }
            mSelectedExtensionsRenderOptions.put(extensions[i], renderOptions);
        }

        repopulateAvailableExtensions();
        mSelectedExtensionsAdapter.notifyDataSetChanged();
    }

    public class ExtensionListAdapter extends BaseAdapter {
        private static final int VIEW_TYPE_ITEM = 0;
        private static final int VIEW_TYPE_ADD = 1;

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getCount() {
            int numItems = mSelectedExtensions.size();
            // Hide add row to show empty view if there are no items.
            return (numItems == 0) ? 0 : (numItems + 1);
        }

        @Override
        public int getItemViewType(int position) {
            return (position == getCount() - 1)
                    ? VIEW_TYPE_ADD
                    : VIEW_TYPE_ITEM;
        }

        @Override
        public Object getItem(int position) {
            return (getItemViewType(position) == VIEW_TYPE_ADD)
                    ? null
                    : mSelectedExtensions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return (getItemViewType(position) == VIEW_TYPE_ADD)
                    ? -1
                    : mSelectedExtensions.get(position).hashCode();
        }

        @Override
        public boolean isEnabled(int position) {
            return (getItemViewType(position) == VIEW_TYPE_ADD) && mAvailableExtensions.size() > 0;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            switch (getItemViewType(position)) {
                case VIEW_TYPE_ADD: {
                    if (convertView == null) {
                        convertView = getActivity().getLayoutInflater()
                                .inflate(R.layout.list_item_add_extension, parent, false);
                    }
                    convertView.setEnabled(isEnabled(position));
                    return convertView;
                }

                case VIEW_TYPE_ITEM: {
                    final ComponentName cn = (ComponentName) getItem(position);
                    if (convertView == null) {
                        convertView = getActivity().getLayoutInflater()
                                .inflate(R.layout.list_item_extension, parent, false);
                    }

                    TextView titleView = (TextView) convertView.findViewById(android.R.id.text1);
                    TextView descriptionView = (TextView) convertView
                            .findViewById(android.R.id.text2);
                    ImageView iconView = (ImageView) convertView.findViewById(android.R.id.icon1);
                    View overflowButton = convertView.findViewById(R.id.overflow_button);

                    final ExtensionListing listing = mExtensionListings.get(cn);
                    if (listing == null || TextUtils.isEmpty(listing.title)) {
                        iconView.setImageBitmap(null);
                        titleView.setText(cn.flattenToShortString());
                        descriptionView.setVisibility(View.GONE);
                        overflowButton.setVisibility(View.GONE);
                    } else {
                        iconView.setImageDrawable(listing.icon);
                        titleView.setText(listing.title);
                        descriptionView.setVisibility(
                                TextUtils.isEmpty(listing.description) ? View.GONE : View.VISIBLE);
                        descriptionView.setText(listing.description);
                        overflowButton.setVisibility(View.VISIBLE);
                        overflowButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                mUndoBarController.hideUndoBar(false);
                                PopupMenu menu = new PopupMenu(view.getContext(), view);
                                menu.inflate(R.menu.configure_item_overflow);
                                if (listing.settingsActivity == null) {
                                    menu.getMenu().findItem(R.id.action_settings).setVisible(false);
                                }
                                menu.setOnMenuItemClickListener(
                                        new OverflowItemClickListener(position));
                                menu.show();
                            }
                        });
                    }

                    final CheckBox alwaysCollapseView = (CheckBox) convertView.findViewById(android.R.id.checkbox);
                    ExtensionManager.ExtensionRenderOptions renderOptions = mSelectedExtensionsRenderOptions.get(cn);
                    if (renderOptions != null) {
                        alwaysCollapseView.setChecked(renderOptions.alwaysCollapsed);
                    } else {
                        alwaysCollapseView.setChecked(false);
                    }
                    alwaysCollapseView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ExtensionManager.ExtensionRenderOptions renderOptions = mSelectedExtensionsRenderOptions.get(cn);
                            if (renderOptions == null) {
                                renderOptions = new ExtensionManager.ExtensionRenderOptions();
                            }
                            renderOptions.alwaysCollapsed = !renderOptions.alwaysCollapsed;
                            mSelectedExtensionsRenderOptions.put(cn, renderOptions);
                            ((CheckBox)v).setChecked(renderOptions.alwaysCollapsed);
                        }
                    });

                    return convertView;
                }
            }

            return null;
        }

        private class OverflowItemClickListener implements PopupMenu.OnMenuItemClickListener {
            private int mPosition;

            public OverflowItemClickListener(int position) {
                mPosition = position;
            }

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (mPosition > getCount()) {
                    return false;
                }

                switch (item.getItemId()) {
                    case R.id.action_remove:
                        mSwipeDismissTouchListener.dismiss(mPosition);
                        return true;

                    case R.id.action_settings:
                        ComponentName cn = (ComponentName) getItem(mPosition);
                        ExtensionListing listing = mExtensionListings.get(cn);
                        if (listing == null) {
                            return false;
                        }

                        try {
                            startActivity(new Intent()
                                    .setComponent(listing.settingsActivity)
                                    .putExtra(DashClockExtension
                                            .EXTRA_FROM_DASHCLOCK_SETTINGS, true));
                        } catch (ActivityNotFoundException e) {
                            // TODO: show error to user
                        } catch (SecurityException e) {
                            // TODO: show error to user
                        }
                        return true;
                }
                return false;
            }
        }
    }

    public static class CantAddExtensionDialog extends DialogFragment {
        private static final String ARG_EXTENSION_TITLE = "title";
        private static final String ARG_EXTENSION_PACKAGE_NAME = "package_name";

        public static CantAddExtensionDialog createInstance(String extensionPackageName,
                String extensionTitle) {
            CantAddExtensionDialog fragment = new CantAddExtensionDialog();
            Bundle args = new Bundle();
            args.putString(ARG_EXTENSION_TITLE, extensionTitle);
            args.putString(ARG_EXTENSION_PACKAGE_NAME, extensionPackageName);
            fragment.setArguments(args);
            return fragment;
        }

        public CantAddExtensionDialog() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String extensionTitle = getArguments().getString(ARG_EXTENSION_TITLE);
            final String extensionPackageName = getArguments().getString(ARG_EXTENSION_PACKAGE_NAME);
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.incompatible_extension_title)
                    .setMessage(Html.fromHtml(getString(
                            R.string.incompatible_extension_message_search_play_template,
                            extensionTitle)))
                    .setPositiveButton(R.string.search_play,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    Intent playIntent = new Intent(Intent.ACTION_VIEW);
                                    playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    playIntent.setData(Uri.parse(
                                            "https://play.google.com/store/apps/details?id="
                                                    + extensionPackageName));
                                    try {
                                        startActivity(playIntent);
                                    } catch (ActivityNotFoundException ignored) {
                                    }

                                    dialog.dismiss();
                                }
                            }
                    )
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            }
                    )
                    .create();
        }
    }
}
