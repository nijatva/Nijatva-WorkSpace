package com.nijatva.workspace.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nijatva.workspace.R;
import com.nijatva.workspace.util.ThemeBridge;
import com.nijatva.workspace.services.ILoadIconCallback;
import com.nijatva.workspace.services.IShelterService;
import com.nijatva.workspace.util.AppSortHelper;
import com.nijatva.workspace.util.ApplicationInfoWrapper;
import com.nijatva.workspace.util.NijatvaSuite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView mIcon;
        private TextView mTitle;
        private TextView mPackage;
        // This text view shows the order of all selected items
        private TextView mSelectOrder;
        private TextView mStatus;
        private ImageView mFrozenBadge;
        private View mSuiteDot;
        int mIndex = -1;
        ViewHolder(View view) {
            super(view);
            mIcon = view.findViewById(R.id.list_app_icon);
            mTitle = view.findViewById(R.id.list_app_title);
            mPackage = view.findViewById(R.id.list_app_package);
            mSelectOrder = view.findViewById(R.id.list_app_select_order);
            mStatus = view.findViewById(R.id.list_app_status);
            mFrozenBadge = view.findViewById(R.id.list_app_frozen_badge);
            mSuiteDot = view.findViewById(R.id.list_app_suite_dot);
            view.setOnClickListener((v) -> onClick());
            if (mAllowMultiSelect) {
                view.setOnLongClickListener((v) -> onLongClick());
            }
        }

        void onClick() {
            if (mIndex == -1) return;

            if (!mMultiSelectMode) {
                // Show available operations via the Fragment
                // pass the full info to it, since we can't be sure
                // the index won't change
                if (mContextMenuHandler != null) {
                    mContextMenuHandler.showContextMenu(mList.get(mIndex), itemView);
                }
            } else {
                // In multi-select mode, single clicks just adds to the selection
                // or cancels the selection if already selected
                if (!mSelectedIndices.contains(mIndex)) {
                    select();
                } else {
                    deselect();
                }
            }
        }

        boolean onLongClick() {
            if (mIndex == -1) return false;

            // If we have an action mode handler, we notify it to enter
            // action mode on long click, and register this adapter
            // to be in multi-select mode
            if (!mMultiSelectMode && mActionModeHandler != null && mActionModeHandler.createActionMode()) {
                mMultiSelectMode = true;
                select();
                return true;
            } else {
                return false;
            }
        }

        // When the user selects the item
        // we need to play the animation of the "select order" appearing
        // on the right side of the item view
        void select() {
            mSelectedIndices.add(mIndex);
            if (mSelectionChangedHandler != null) mSelectionChangedHandler.run();
            mSelectOrder.clearAnimation();
            mSelectOrder.startAnimation(AnimationUtils.loadAnimation(itemView.getContext(), R.anim.scale_appear));
            showSelectOrder();
        }

        // When the user deselects the item
        void deselect() {
            mSelectedIndices.remove((Integer) mIndex);
            if (mSelectionChangedHandler != null) mSelectionChangedHandler.run();
            mSelectOrder.clearAnimation();
            setUnselectedBackground();
            Animation anim = AnimationUtils.loadAnimation(itemView.getContext(), R.anim.scale_hide);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (mActionModeCancelHandler != null && mSelectedIndices.size() == 0) {
                        // If there is no selection left, tell parent to cancel the action mode.
                        mActionModeCancelHandler.cancelActionMode();
                    }
                    // The selection index of items other than this one
                    // can be changed because of the removal of the current one
                    // Thus, we just notify that the data set has been changed,
                    // to force redraw all of them.
                    notifyDataSetChanged();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            mSelectOrder.startAnimation(anim);
        }

        // When an item should be displayed in selected state
        // (not necessarily when the user clicked on it; the view might have been recycled)
        void showSelectOrder() {
            if (!mList.get(mIndex).isHidden()) {
                itemView.setBackgroundColor(ThemeBridge.themedColor(itemView.getContext(), R.attr.appSelectedBg));
            } else {
                // The app is both frozen and selected
                // we use a blended color of the two for its background
                itemView.setBackgroundColor(ThemeBridge.themedColor(itemView.getContext(), R.attr.appSelectedDisabledBg));
            }
            if (mStatus != null) mStatus.setVisibility(View.GONE);
            mSelectOrder.setVisibility(View.VISIBLE);
            mSelectOrder.setText(String.valueOf(mSelectedIndices.indexOf(mIndex) + 1));
        }

        // When an item should be displayed in deselected state
        void hideSelectOrder() {
            setUnselectedBackground();
            if (mStatus != null) mStatus.setVisibility(View.VISIBLE);
            mSelectOrder.setVisibility(View.GONE);
        }

        // Set the background when not in the selected state
        void setUnselectedBackground() {
            if (!mList.get(mIndex).isHidden()) {
                itemView.setBackground(null);
            } else {
                itemView.setBackgroundColor(ThemeBridge.themedColor(itemView.getContext(), R.attr.appDisabledBg));
            }
        }

        void setIndex(final int index) {
            mIndex = index;

            if (mIndex >= 0) {
                // Clear all animations first
                mSelectOrder.clearAnimation();

                ApplicationInfoWrapper info = mList.get(mIndex);
                mPackage.setText(info.getPackageName());

                mTitle.setText(info.getLabel());
                // Status chip instead of a "[Frozen]" prefix in the title
                mStatus.setText(info.isHidden() ? R.string.status_frozen : R.string.status_active);
                // Frozen apps: dimmed icon + snowflake badge
                mIcon.setAlpha(info.isHidden() ? 0.55f : 1f);
                if (mSuiteDot != null) {
                    mSuiteDot.setVisibility(NijatvaSuite.isSuite(info.getPackageName())
                            ? View.VISIBLE : View.GONE);
                }
                if (mFrozenBadge != null) {
                    mFrozenBadge.setVisibility(info.isHidden() ? View.VISIBLE : View.GONE);
                }

                // Special logic when in multi-select mode and this item is selected
                if (mMultiSelectMode && mSelectedIndices.contains(mIndex)) {
                    showSelectOrder();
                } else {
                    hideSelectOrder();
                }

                // Load the application icon from cache
                // or populate the cache through the service
                if (mIconCache.containsKey(info.getPackageName())) {
                    mIcon.setImageBitmap(mIconCache.get(info.getPackageName()));
                } else {
                    mIcon.setImageDrawable(mDefaultIcon);

                    try {
                        mService.loadIcon(info, new ILoadIconCallback.Stub() {
                            @Override
                            public void callback(Bitmap icon) {
                                if (index == mIndex) {
                                    mHandler.post(() -> mIcon.setImageBitmap(icon));
                                }

                                synchronized (AppListAdapter.class) {
                                    mIconCache.put(info.getPackageName(), icon);
                                }
                            }
                        });
                    } catch (RemoteException e) {
                        // Do Nothing
                    }
                }
            }
        }
    }

    interface ContextMenuHandler {
        void showContextMenu(ApplicationInfoWrapper info, View view);
    }

    interface ActionModeHandler {
        boolean createActionMode();
    }

    interface ActionModeCancelHandler {
        void cancelActionMode();
    }

    // The ORIGINAL list of applications without filtering
    private List<ApplicationInfoWrapper> mOrigList = new ArrayList<>();
    // The list of applications that is ACTUALLY displayed
    // (after filtering by search query if applicable)
    private List<ApplicationInfoWrapper> mList = new ArrayList<>();
    private int mSortMode = 0;
    private Context mContext = null;
    private int mFilterMode = FILTER_ALL;

    public static final int FILTER_ALL = 0;
    public static final int FILTER_ACTIVE = 1;
    public static final int FILTER_FROZEN = 2;
    public static final int FILTER_SYSTEM = 3;
    public static final int FILTER_SUITE = 4;
    private String mSearchQuery = null;
    private IShelterService mService;
    private Drawable mDefaultIcon;
    private String mLabelDisabled;
    private Map<String, Bitmap> mIconCache = new HashMap<>();
    private ContextMenuHandler mContextMenuHandler = null;
    private ActionModeHandler mActionModeHandler = null;
    private ActionModeCancelHandler mActionModeCancelHandler = null;
    private Runnable mSelectionChangedHandler = null;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    // Multi-selection mode
    private boolean mAllowMultiSelect = false;
    private boolean mMultiSelectMode = false;
    private List<Integer> mSelectedIndices = new ArrayList<>();

    AppListAdapter(IShelterService service, Drawable defaultIcon) {
        mService = service;
        mDefaultIcon = defaultIcon;
    }

    void setContextMenuHandler(ContextMenuHandler handler) {
        mContextMenuHandler = handler;
    }

    // When we enter multi-select mode, we have to notify our parent fragment
    // to enter action mode, in order to show the specific menus
    void setActionModeHandler(ActionModeHandler handler) {
        mActionModeHandler = handler;
    }

    // When there is no selection left, we have to notify our parent fragment
    // to exit action mode
    void setSelectionChangedHandler(Runnable handler) {
        mSelectionChangedHandler = handler;
    }

    void selectAll() {
        mSelectedIndices.clear();
        for (int i = 0; i < mList.size(); i++) {
            mSelectedIndices.add(i);
        }
        notifyDataSetChanged();
        if (mSelectionChangedHandler != null) mSelectionChangedHandler.run();
    }

    /** Every app in the current list that is NOT selected (used by "freeze all others"). */
    List<ApplicationInfoWrapper> getAllExceptSelected() {
        List<ApplicationInfoWrapper> out = new java.util.ArrayList<>();
        for (int i = 0; i < mList.size(); i++) {
            if (!mSelectedIndices.contains(i)) out.add(mList.get(i));
        }
        return out;
    }

    int getSelectedCount() {
        return mSelectedIndices.size();
    }

    void setActionModeCancelHandler(ActionModeCancelHandler handler) {
        mActionModeCancelHandler = handler;
    }

    void allowMultiSelect() {
        mAllowMultiSelect = true;
    }

    boolean isMultiSelectMode() {
        return mMultiSelectMode;
    }

    void cancelMultiSelectMode() {
        mMultiSelectMode = false;
        mSelectedIndices.clear();
        notifyDataSetChanged();
    }

    List<ApplicationInfoWrapper> getSelectedItems() {
        if (!mMultiSelectMode) return null;
        if (mSelectedIndices.size() == 0) return null;

        return mSelectedIndices.stream()
                .map((idx) -> mList.get(idx))
                .collect(Collectors.toList());
    }

    void setData(List<ApplicationInfoWrapper> apps) {
        mOrigList.clear();
        mList.clear();
        mIconCache.clear();
        mOrigList.addAll(apps);
        notifyChange();
    }

    // null = clear search query
    void setSearchQuery(String query) {
        mSearchQuery = query;
        notifyChange();
    }

    // 0 = name A-Z, 1 = name Z-A, 2 = frozen first, 3 = active first
    void setFilterMode(int mode) {
        mFilterMode = mode;
        notifyChange();
    }

    void setContext(Context c) {
        mContext = c;
    }

    void setSortMode(int mode) {
        mSortMode = mode;
        notifyChange();
    }

    // Selection is stored as positions into mList. Any change to the list order
    // (sort, search, data refresh) invalidates those positions, so the selection
    // must be dropped -- otherwise batch freeze/unfreeze could hit the WRONG apps.
    private void dropSelectionIfListReordered() {
        if (mMultiSelectMode && !mSelectedIndices.isEmpty()) {
            mSelectedIndices.clear();
            if (mActionModeCancelHandler != null) {
                mActionModeCancelHandler.cancelActionMode();
            }
        }
    }

    // Call this on ACTUAL data set change and/or search query change
    private void notifyChange() {
        dropSelectionIfListReordered();
        mList.clear();
        if (mSearchQuery == null) {
            // No search query, do not filter
            mList.addAll(mOrigList);
        } else {
            // Filter by search query
            mList.addAll(mOrigList.stream()
                    .filter((app) ->
                            app.getPackageName().toLowerCase().contains(mSearchQuery)
                                    || app.getLabel().toLowerCase().contains(mSearchQuery))
                    .collect(Collectors.toList()));
        }
        // Quick filters from the chip row
        if (mFilterMode == FILTER_ACTIVE) {
            mList.removeIf(ApplicationInfoWrapper::isHidden);
        } else if (mFilterMode == FILTER_FROZEN) {
            mList.removeIf(app -> !app.isHidden());
        } else if (mFilterMode == FILTER_SYSTEM) {
            mList.removeIf(app -> !app.isSystem());
        } else if (mFilterMode == FILTER_SUITE) {
            mList.removeIf(app -> !NijatvaSuite.isSuite(app.getPackageName()));
        }
        // Apply the selected sort order (shared, unit-tested logic)
        AppSortHelper.sort(mList, mSortMode);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        if (mLabelDisabled == null) {
            mLabelDisabled = viewGroup.getContext().getString(R.string.list_item_disabled);
        }
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        View view = inflater.inflate(R.layout.app_list_item, viewGroup, false);
        ViewHolder vh = new ViewHolder(view);
        vh.setIndex(i);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        viewHolder.setIndex(i);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.setIndex(-1);
    }
}
