package com.nijatva.workspace.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.nijatva.workspace.util.ThemeBridge;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.nijatva.workspace.R;
import com.nijatva.workspace.services.IAppInstallCallback;
import com.nijatva.workspace.services.IGetAppsCallback;
import com.nijatva.workspace.services.ILoadIconCallback;
import com.nijatva.workspace.services.IShelterService;
import com.nijatva.workspace.services.ShelterService;
import com.nijatva.workspace.util.ApplicationInfoWrapper;
import com.nijatva.workspace.util.LocalStorageManager;
import com.nijatva.workspace.util.Utility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AppListFragment extends BaseFragment {
    static final String BROADCAST_REFRESH = "com.nijatva.workspace.broadcast.REFRESH";

    // Menu Items
    private static final int MENU_ITEM_CLONE = 10001;
    private static final int MENU_ITEM_UNINSTALL = 10002;
    private static final int MENU_ITEM_FREEZE = 10003;
    private static final int MENU_ITEM_UNFREEZE = 10004;
    private static final int MENU_ITEM_LAUNCH = 10005;
    private static final int MENU_ITEM_CREATE_UNFREEZE_SHORTCUT = 10006;
    private static final int MENU_ITEM_AUTO_FREEZE = 10007;
    private static final int MENU_ITEM_SELECT_ALL = 10010;
    private static final int MENU_ITEM_FREEZE_OTHERS = 10012;
    private static final int MENU_ITEM_APP_INFO = 10011;
    private static final int MENU_ITEM_ALLOW_CROSS_PROFILE_WIDGET = 10008;
    private static final int MENU_ITEM_ALLOW_CROSS_PROFILE_INTERACTION = 10009;

    private IShelterService mService = null;
    private boolean mIsRemote = false;
    private boolean mRefreshing = false;
    private Drawable mDefaultIcon = null;
    private ApplicationInfoWrapper mSelectedApp = null;

    // Cache of allowed Cross-profile widget providers
    // Only useful if this fragment manages the work profile
    private Set<String> mCrossProfileWidgetProviders = new HashSet<>();

    // Packages allowed to interact across profiles
    private Set<String> mCrossProfilePackages = new HashSet<>();

    // Views
    private RecyclerView mList = null;
    private AppListAdapter mAdapter = null;
    private SwipeRefreshLayout mSwipeRefresh = null;

    // Variable to store the current action mode object if any
    private ActionMode mActionMode = null;

    // Receiver for Refresh events
    // used for app changes
    private BroadcastReceiver mRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    // Receiver for context menu closed event
    private BroadcastReceiver mContextMenuClosedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mSelectedApp = null;
        }
    };

    // Receiver for search event
    private BroadcastReceiver mSearchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String query = intent.getStringExtra("text");
            if ("".equals(query)) {
                // Consider empty query as null
                query = null;
            }
            mAdapter.setSearchQuery(query);
        }
    };

    // Receiver for sort order changes
    private BroadcastReceiver mSortReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mAdapter.setSortMode(intent.getIntExtra("mode", 0));
        }
    };

    static AppListFragment newInstance(IShelterService service, boolean isRemote) {
        AppListFragment fragment = new AppListFragment();
        Bundle args = new Bundle();
        args.putBinder("service", service.asBinder());
        args.putBoolean("is_remote", isRemote);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDefaultIcon = getActivity().getPackageManager().getDefaultActivityIcon();
        IBinder service = getArguments().getBinder("service");
        mService = IShelterService.Stub.asInterface(service);
        mIsRemote = getArguments().getBoolean("is_remote");
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getContext())
                .registerReceiver(mRefreshReceiver, new IntentFilter(BROADCAST_REFRESH));
        LocalBroadcastManager.getInstance(getContext())
                .registerReceiver(mContextMenuClosedReceiver,
                        new IntentFilter(MainActivity.BROADCAST_CONTEXT_MENU_CLOSED));
        LocalBroadcastManager.getInstance(getContext())
                .registerReceiver(mSearchReceiver,
                        new IntentFilter(MainActivity.BROADCAST_SEARCH_FILTER_CHANGED));
        LocalBroadcastManager.getInstance(getContext())
                .registerReceiver(mSortReceiver,
                        new IntentFilter(MainActivity.BROADCAST_SORT_CHANGED));
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSelectedApp = null;
        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(mRefreshReceiver);
        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(mContextMenuClosedReceiver);
        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(mSearchReceiver);
        LocalBroadcastManager.getInstance(getContext())
                .unregisterReceiver(mSortReceiver);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);

        // Save the views
        mList = view.findViewById(R.id.fragment_list_recycler_view);
        mSwipeRefresh = view.findViewById(R.id.fragment_swipe_refresh);
        mAdapter = new AppListAdapter(mService, mDefaultIcon);
        mAdapter.setContextMenuHandler((info, v) -> showAppSheet(info));
        if (mIsRemote) {
            // Allow multi-select actions if this is in the work profile
            // to allow things like multi-app unfreeze shortcuts
            mAdapter.allowMultiSelect();
            mAdapter.setActionModeHandler(this::createMultiSelectActionMode);
            mAdapter.setActionModeCancelHandler(() -> {
                if (mActionMode != null) {
                    mActionMode.finish();
                }
            });
        }
        mAdapter.setContext(getContext());
        mAdapter.setSortMode(LocalStorageManager.getInstance()
                .getInt(LocalStorageManager.PREF_SORT_MODE));
        mList.setAdapter(mAdapter);
        mList.setLayoutManager(new LinearLayoutManager(getActivity()));
        mList.setHasFixedSize(true);

        mSwipeRefresh.setOnRefreshListener(this::refresh);
        registerForContextMenu(mList);

        // Quick filter chips
        com.google.android.material.chip.ChipGroup chips = view.findViewById(R.id.chip_group);
        if (chips != null) {
            chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
                int id = checkedIds.isEmpty() ? R.id.chip_all : checkedIds.get(0);
                if (id == R.id.chip_active) {
                    mAdapter.setFilterMode(AppListAdapter.FILTER_ACTIVE);
                } else if (id == R.id.chip_frozen) {
                    mAdapter.setFilterMode(AppListAdapter.FILTER_FROZEN);
                } else if (id == R.id.chip_system) {
                    mAdapter.setFilterMode(AppListAdapter.FILTER_SYSTEM);
                } else if (id == R.id.chip_suite) {
                    mAdapter.setFilterMode(AppListAdapter.FILTER_SUITE);
                } else {
                    mAdapter.setFilterMode(AppListAdapter.FILTER_ALL);
                }
            });
        }

        // Batch action dock
        View freezeBtn = view.findViewById(R.id.batch_freeze);
        View unfreezeBtn = view.findViewById(R.id.batch_unfreeze);
        View shortcutBtn = view.findViewById(R.id.batch_shortcut);
        if (freezeBtn != null) freezeBtn.setOnClickListener(v -> applyBatchFreeze(true));
        if (unfreezeBtn != null) unfreezeBtn.setOnClickListener(v -> applyBatchFreeze(false));
        if (shortcutBtn != null) shortcutBtn.setOnClickListener(v -> {
            List<ApplicationInfoWrapper> sel = mAdapter.getSelectedItems();
            if (sel != null && !sel.isEmpty()) {
                loadIconAndAddUnfreezeShortcut(sel.get(0), sel);
                if (mActionMode != null) mActionMode.finish();
            }
        });

        return view;
    }

    void refresh() {
        if (mAdapter == null) return;
        if (mRefreshing) return;
        if (mAdapter.isMultiSelectMode()) {
            mSwipeRefresh.setRefreshing(false);
            return; // Disallow refreshing when we are multi-selecting
        }
        mRefreshing = true;
        mSwipeRefresh.setRefreshing(true);

        try {
            mService.getApps(new IGetAppsCallback.Stub() {
                @Override
                public void callback(List<ApplicationInfoWrapper> apps) {
                    if (mIsRemote) {
                        mCrossProfileWidgetProviders.clear();
                        mCrossProfilePackages.clear();

                        // Update the cross-profile packages / widget providers list
                        try {
                            mCrossProfileWidgetProviders.addAll(mService.getCrossProfileWidgetProviders());

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                mCrossProfilePackages.addAll(mService.getCrossProfilePackages());
                        } catch (RemoteException ignored) {

                        }
                    }

                    if (mIsRemote) {
                        Utility.deleteMissingApps(
                                LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE,
                                apps);
                    }
                    runOnUiThread(() -> {
                        mSwipeRefresh.setRefreshing(false);
                        mAdapter.setData(apps);
                        updateStatusStrip(apps);
                        mRefreshing = false;
                    });
                }
            }, ((MainActivity) getActivity()).mShowAll);
        } catch (RemoteException e) {
            // Just... do nothing for now
        }
    }

    // Enter multi-select mode for work profile
    // Compact workspace status: total apps and how many are frozen
    private void updateStatusStrip(List<ApplicationInfoWrapper> apps) {
        View v = getView();
        if (v == null) return;
        TextView strip = v.findViewById(R.id.fragment_status_strip);
        if (strip == null) return;
        int total = apps == null ? 0 : apps.size();
        int frozen = 0;
        if (apps != null) {
            for (ApplicationInfoWrapper a : apps) {
                if (a.isHidden()) frozen++;
            }
        }
        int suite = 0;
        if (apps != null) {
            for (ApplicationInfoWrapper a : apps) {
                if (com.nijatva.workspace.util.NijatvaSuite.isSuite(a.getPackageName())) suite++;
            }
        }
        strip.setText(getString(R.string.status_strip_suite, total, frozen, suite));
        setChipText(v, R.id.chip_suite, R.string.chip_suite, suite);

        // Chip labels carry live counts
        int system = 0;
        if (apps != null) {
            for (ApplicationInfoWrapper a : apps) {
                if (a.isSystem()) system++;
            }
        }
        setChipText(v, R.id.chip_all, R.string.chip_all, total);
        setChipText(v, R.id.chip_active, R.string.chip_active, total - frozen);
        setChipText(v, R.id.chip_frozen, R.string.chip_frozen, frozen);
        setChipText(v, R.id.chip_system, R.string.chip_system, system);
    }

    private void setChipText(View root, int chipId, int labelRes, int count) {
        View chip = root.findViewById(chipId);
        if (chip instanceof android.widget.TextView) {
            ((android.widget.TextView) chip).setText(
                    getString(R.string.chip_label, getString(labelRes), count));
        }
    }

    // Bottom sheet with the app actions (tap on a row)
    private void showAppSheet(final ApplicationInfoWrapper app) {
        final Context ctx = getContext();
        if (ctx == null) return;
        mSelectedApp = app;

        final android.app.Dialog sheet = new android.app.Dialog(ctx);
        sheet.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_sheet);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);
        box.setPadding(pad, (int) (14 * d), pad, (int) (20 * d));

        // drag handle
        View handle = new View(ctx);
        handle.setBackgroundResource(R.drawable.bg_status_chip);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams((int) (36 * d), (int) (4 * d));
        hl.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = (int) (14 * d);
        box.addView(handle, hl);

        TextView title = new TextView(ctx);
        title.setText(app.getLabel());
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(ThemeBridge.themedColor(ctx, R.attr.appTextPrimary));
        box.addView(title);

        TextView pkg = new TextView(ctx);
        pkg.setText(app.getPackageName());
        pkg.setTextSize(12);
        pkg.setTextColor(ThemeBridge.themedColor(ctx, R.attr.appTextSecondary));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pl.bottomMargin = (int) (10 * d);
        box.addView(pkg, pl);

        addSheetRow(ctx, box, sheet, R.string.launch,
                () -> performAppAction(MENU_ITEM_LAUNCH, false));
        addSheetRow(ctx, box, sheet,
                app.isHidden() ? R.string.unfreeze_app : R.string.freeze_app,
                () -> performAppAction(app.isHidden() ? MENU_ITEM_UNFREEZE : MENU_ITEM_FREEZE, false));
        if (!app.isSystem()) {
            addSheetRow(ctx, box, sheet, R.string.clone_to_main_profile,
                    () -> performAppAction(MENU_ITEM_CLONE, false));
        }
        addSheetRow(ctx, box, sheet, R.string.create_unfreeze_shortcut,
                () -> performAppAction(MENU_ITEM_CREATE_UNFREEZE_SHORTCUT, false));
        addSheetRow(ctx, box, sheet, R.string.app_info,
                () -> performAppAction(MENU_ITEM_APP_INFO, false));
        addSheetRow(ctx, box, sheet, R.string.copy_package,
                () -> copyPackageName(app.getPackageName()));
        if (mService != null) {
            addSheetRow(ctx, box, sheet, R.string.inspect_components,
                    () -> showSecurityDetails(app));
        }
        if (mService != null && app.isHidden()) {
            addSheetRow(ctx, box, sheet, R.string.leash_title, () -> showLeashDialog(app));
        }
        if (mService != null) {
            boolean suspended = false;
            try { suspended = mService.isSuspended(app.getPackageName()); } catch (RemoteException ignored) { }
            final boolean nowSuspended = suspended;
            addSheetRow(ctx, box, sheet,
                    nowSuspended ? R.string.unsuspend_app : R.string.suspend_app,
                    () -> toggleSuspend(app, !nowSuspended));
            boolean guarded = false;
            try { guarded = mService.isTamperGuarded(app.getPackageName()); } catch (RemoteException ignored) { }
            final boolean nowGuarded = guarded;
            addSheetRow(ctx, box, sheet,
                    nowGuarded ? R.string.tamper_guard_off : R.string.tamper_guard_on,
                    () -> toggleTamper(app, !nowGuarded));
        }
        if (!app.isSystem() && mService != null) {
            boolean blocked = false;
            try {
                blocked = mService.isUninstallBlocked(app.getPackageName());
            } catch (RemoteException ignored) {
            }
            final boolean nowBlocked = blocked;
            addSheetRow(ctx, box, sheet,
                    nowBlocked ? R.string.allow_uninstall : R.string.block_uninstall,
                    () -> toggleUninstallGuard(app, !nowBlocked));
        }
        if (!app.isSystem()) {
            addSheetRow(ctx, box, sheet, R.string.uninstall_app,
                    () -> performAppAction(MENU_ITEM_UNINSTALL, false));
        }

        sheet.setContentView(box);
        android.view.Window w = sheet.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            w.setGravity(android.view.Gravity.BOTTOM);
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        sheet.show();
    }

    private void addSheetRow(final Context ctx, LinearLayout parent,
                             final android.app.Dialog sheet, int labelRes, final Runnable action) {
        TextView row = new TextView(ctx);
        row.setText(labelRes);
        row.setTextSize(15);
        row.setTextColor(ThemeBridge.themedColor(ctx, R.attr.appTextPrimary));
        int p = (int) (14 * getResources().getDisplayMetrics().density);
        row.setPadding(0, p, 0, p);
        android.util.TypedValue tv = new android.util.TypedValue();
        if (ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            row.setBackgroundResource(tv.resourceId);
        }
        row.setOnClickListener(v -> {
            sheet.dismiss();
            action.run();
        });
        parent.addView(row);
    }

    private void showSecurityDetails(ApplicationInfoWrapper app) {
        if (mService == null) return;
        try {
            String json = mService.getAppComponents(app.getPackageName());
            org.json.JSONObject o = new org.json.JSONObject(json);
            StringBuilder sb = new StringBuilder();
            sb.append(getString(R.string.inspect_exported)).append("\n");
            sb.append("  Activities: ").append(o.optInt("exportedActivities")).append("\n");
            sb.append("  Services: ").append(o.optInt("exportedServices")).append("\n");
            sb.append("  Receivers: ").append(o.optInt("exportedReceivers")).append("\n");
            sb.append("  Providers: ").append(o.optInt("exportedProviders")).append("\n\n");
            String sha = o.optString("signature");
            sb.append("SHA-256: ").append(sha.isEmpty() ? "-" : sha);
            new AlertDialog.Builder(getContext())
                    .setTitle(app.getLabel())
                    .setMessage(sb.toString())
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.inspect_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void showLeashDialog(final ApplicationInfoWrapper app) {
        final long[] minutes = {15, 30, 60};
        final String[] labels = {getString(R.string.leash_15), getString(R.string.leash_30),
                getString(R.string.leash_60)};
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.leash_title)
                .setItems(labels, (d, which) -> {
                    if (mService == null) return;
                    long millis = minutes[which] * 60L * 1000L;
                    try {
                        boolean ok = mService.unfreezeFor(app.getPackageName(), millis);
                        Toast.makeText(getContext(),
                                ok ? getString(R.string.leash_done, labels[which])
                                   : getString(R.string.leash_failed),
                                Toast.LENGTH_SHORT).show();
                    } catch (RemoteException e) {
                        Toast.makeText(getContext(), R.string.leash_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toggleSuspend(ApplicationInfoWrapper app, boolean suspend) {
        if (mService == null) return;
        try {
            boolean ok = mService.setSuspended(app.getPackageName(), suspend);
            Toast.makeText(getContext(),
                    ok ? (suspend ? R.string.suspend_done : R.string.unsuspend_done)
                       : R.string.suspend_failed, Toast.LENGTH_SHORT).show();
        } catch (RemoteException e) {
            Toast.makeText(getContext(), R.string.suspend_failed, Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void toggleTamper(ApplicationInfoWrapper app, boolean enable) {
        if (mService == null) return;
        try {
            boolean ok = mService.setTamperGuard(app.getPackageName(), enable);
            Toast.makeText(getContext(),
                    ok ? (enable ? R.string.tamper_on_done : R.string.tamper_off_done)
                       : R.string.tamper_failed, Toast.LENGTH_SHORT).show();
        } catch (RemoteException e) {
            Toast.makeText(getContext(), R.string.tamper_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void freezeAllExceptSelected() {
        if (mService == null) return;
        List<ApplicationInfoWrapper> others = mAdapter.getAllExceptSelected();
        int done = 0;
        for (ApplicationInfoWrapper app : others) {
            try {
                if (!app.isHidden()) { mService.freezeApp(app); done++; }
            } catch (RemoteException ignored) { }
        }
        if (mActionMode != null) mActionMode.finish();
        Toast.makeText(getContext(),
                getString(R.string.freeze_all_except_done, done), Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void toggleUninstallGuard(ApplicationInfoWrapper app, boolean block) {
        if (mService == null) return;
        try {
            boolean ok = mService.setUninstallBlocked(app.getPackageName(), block);
            Toast.makeText(getContext(),
                    ok ? (block ? R.string.uninstall_blocked_done : R.string.uninstall_allowed_done)
                       : R.string.uninstall_guard_failed,
                    Toast.LENGTH_SHORT).show();
        } catch (RemoteException e) {
            Toast.makeText(getContext(), R.string.uninstall_guard_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyPackageName(String pkg) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("package", pkg));
            Toast.makeText(getContext(), R.string.package_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void showBatchDock(boolean show) {
        View v = getView();
        if (v == null) return;
        View dock = v.findViewById(R.id.batch_dock);
        if (dock != null) dock.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void applyBatchFreeze(boolean freeze) {
        List<ApplicationInfoWrapper> sel = mAdapter.getSelectedItems();
        if (sel == null || sel.isEmpty() || mService == null) {
            showBatchDock(false);
            return;
        }
        int done = 0;
        for (ApplicationInfoWrapper app : sel) {
            try {
                if (freeze) mService.freezeApp(app);
                else mService.unfreezeApp(app);
                done++;
            } catch (RemoteException e) {
                // skip this one, keep going
            }
        }
        if (mActionMode != null) mActionMode.finish();
        showBatchDock(false);
        Toast.makeText(getContext(),
                getString(freeze ? R.string.freeze_all_success : R.string.unfreeze_all_success),
                Toast.LENGTH_SHORT).show();
        refresh();
    }

    boolean createMultiSelectActionMode() {
        mActionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(Menu.NONE, MENU_ITEM_SELECT_ALL, Menu.NONE, R.string.nj_select_all)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.add(Menu.NONE, MENU_ITEM_FREEZE_OTHERS, Menu.NONE, R.string.freeze_all_except)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                menu.add(Menu.NONE, MENU_ITEM_CREATE_UNFREEZE_SHORTCUT, Menu.NONE, R.string.create_unfreeze_shortcut)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                // Short, count-based title: long titles overlapped the action items
                mode.setTitle(getString(R.string.n_selected, mAdapter.getSelectedCount()));
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                List<ApplicationInfoWrapper> list = mAdapter.getSelectedItems();
                if (list == null) {
                    // We can't perform any action on nothing
                    return false;
                }

                switch (item.getItemId()) {
                    case MENU_ITEM_SELECT_ALL:
                        mAdapter.selectAll();
                        return true;
                    case MENU_ITEM_FREEZE_OTHERS:
                        freezeAllExceptSelected();
                        return true;
                    case MENU_ITEM_CREATE_UNFREEZE_SHORTCUT:
                        // When multiple apps are selected for creating unfreeze & launch shortcut
                        // the shortcut will launch the first one, before which all the others
                        // will be unfrozen. This helps apps that has dependency relationships.
                        loadIconAndAddUnfreezeShortcut(list.get(0), list);
                        mode.finish();
                        return true;
                }

                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
                mAdapter.cancelMultiSelectMode();
                showBatchDock(false);
            }
        });
        showBatchDock(true);
        // Keep the count in the title in sync with the selection
        mAdapter.setSelectionChangedHandler(() -> {
            if (mActionMode != null) {
                mActionMode.setTitle(getString(R.string.n_selected, mAdapter.getSelectedCount()));
            }
        });
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (mSelectedApp == null) return;

        if (mIsRemote) {
            if (!mSelectedApp.isSystem())
                menu.add(Menu.NONE, MENU_ITEM_CLONE, Menu.NONE, R.string.clone_to_main_profile);
            // Freezing / Unfreezing is only available in profiles that we can control
            if (mSelectedApp.isHidden()) {
                menu.add(Menu.NONE, MENU_ITEM_UNFREEZE, Menu.NONE, R.string.unfreeze_app);
                menu.add(Menu.NONE, MENU_ITEM_LAUNCH, Menu.NONE, R.string.unfreeze_and_launch);
            } else {
                menu.add(Menu.NONE, MENU_ITEM_FREEZE, Menu.NONE, R.string.freeze_app);
                menu.add(Menu.NONE, MENU_ITEM_LAUNCH, Menu.NONE, R.string.launch);
            }
            menu.add(Menu.NONE, MENU_ITEM_APP_INFO, Menu.NONE, R.string.app_info);

            // Cross-profile widget / packages settings is also limited to work profile
            MenuItem crossProfileWdiegt =
                    menu.add(Menu.NONE, MENU_ITEM_ALLOW_CROSS_PROFILE_WIDGET, Menu.NONE,
                            R.string.allow_cross_profile_widgets);
            crossProfileWdiegt.setCheckable(true);
            crossProfileWdiegt.setChecked(
                    mCrossProfileWidgetProviders.contains(mSelectedApp.getPackageName()));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MenuItem crossProfileInteraction =
                        menu.add(Menu.NONE, MENU_ITEM_ALLOW_CROSS_PROFILE_INTERACTION, Menu.NONE,
                                R.string.allow_cross_profile_interaction);
                crossProfileInteraction.setCheckable(true);
                crossProfileInteraction.setChecked(
                        mCrossProfilePackages.contains(mSelectedApp.getPackageName()));
            }

            // TODO: If we implement God Mode (i.e. Shelter as device owner), we should
            // TODO: use two different lists to store auto freeze apps because we'll be
            // TODO: able to freeze apps in main profile.
            MenuItem autoFreeze = menu.add(Menu.NONE, MENU_ITEM_AUTO_FREEZE, Menu.NONE, R.string.auto_freeze);
            autoFreeze.setCheckable(true);
            autoFreeze.setChecked(
                    LocalStorageManager.getInstance().stringListContains(
                            LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, mSelectedApp.getPackageName()));
            menu.add(Menu.NONE, MENU_ITEM_CREATE_UNFREEZE_SHORTCUT, Menu.NONE, R.string.create_unfreeze_shortcut);
        } else {
            menu.add(Menu.NONE, MENU_ITEM_CLONE, Menu.NONE, R.string.clone_to_work_profile);
        }

        if (!mSelectedApp.isSystem()) {
            // We can't uninstall system apps in both cases
            // but we'll be able to "freeze" them
            menu.add(Menu.NONE, MENU_ITEM_UNINSTALL, Menu.NONE, R.string.uninstall_app);
        }

        if (menu.size() > 0) {
            // Only set title when the menu is not empty
            // this ensures that no menu will be shown
            // if no operation available
            menu.setHeaderTitle(
                    getString(R.string.app_context_menu_title, mSelectedApp.getLabel()));
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mSelectedApp == null) return false;
        return performAppAction(item.getItemId(), item.isChecked());
    }

    private boolean performAppAction(int itemId, boolean checked) {
        if (mSelectedApp == null) return false;

        switch (itemId) {
            case MENU_ITEM_CLONE:
                if (Utility.isMIUI() && !mSelectedApp.isSystem()) {
                    // Cannot clone non-system apps on MIUI
                    // Keep this variable intact when showing the dialog
                    final ApplicationInfoWrapper selectedApp = mSelectedApp;
                    new AlertDialog.Builder(getContext())
                            .setMessage(R.string.miui_cannot_clone)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNegativeButton(R.string.continue_anyway, (diag, button) ->
                                    installOrUninstall(selectedApp, true))
                            .show();
                } else {
                    installOrUninstall(mSelectedApp, true);
                }
                return true;
            case MENU_ITEM_UNINSTALL:
                installOrUninstall(mSelectedApp, false);
                return true;
            case MENU_ITEM_FREEZE:
                try {
                    mService.freezeApp(mSelectedApp);
                } catch (RemoteException e) {

                }
                Toast.makeText(getContext(),
                        getString(R.string.freeze_success, mSelectedApp.getLabel()), Toast.LENGTH_SHORT).show();
                refresh();
                return true;
            case MENU_ITEM_UNFREEZE:
                try {
                    mService.unfreezeApp(mSelectedApp);
                } catch (RemoteException e) {

                }
                Toast.makeText(getContext(),
                        getString(R.string.unfreeze_success, mSelectedApp.getLabel()), Toast.LENGTH_SHORT).show();
                refresh();
                return true;
            case MENU_ITEM_LAUNCH:
                // LAUNCH and UNFREEZE_AND_LAUNCH share the same ID
                // because the implementation of UNFREEZE_AND_LAUNCH in DummyActivity
                // will work for both
                Intent intent = new Intent(DummyActivity.UNFREEZE_AND_LAUNCH);
                intent.setComponent(new ComponentName(getContext(), DummyActivity.class));
                intent.putExtra("packageName", mSelectedApp.getPackageName());
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                DummyActivity.registerSameProcessRequest(intent);
                startActivity(intent);
                return true;
            case MENU_ITEM_CREATE_UNFREEZE_SHORTCUT:
                loadIconAndAddUnfreezeShortcut(mSelectedApp, null);
                return true;
            case MENU_ITEM_AUTO_FREEZE:
                boolean orig = LocalStorageManager.getInstance().stringListContains(
                        LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, mSelectedApp.getPackageName());

                if (!orig) {
                    LocalStorageManager.getInstance().appendStringList(
                            LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, mSelectedApp.getPackageName());
                } else {
                    LocalStorageManager.getInstance().removeFromStringList(
                            LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, mSelectedApp.getPackageName());
                }
                return true;
            case MENU_ITEM_ALLOW_CROSS_PROFILE_WIDGET: {
                boolean newState = !checked;
                try {
                    if (mService.setCrossProfileWidgetProviderEnabled(mSelectedApp.getPackageName(), newState)) {

                        // Update the cached list of all cross-profile widget providers
                        if (newState) {
                            mCrossProfileWidgetProviders.add(mSelectedApp.getPackageName());
                        } else {
                            mCrossProfileWidgetProviders.remove(mSelectedApp.getPackageName());
                        }
                    }
                } catch (RemoteException e) {

                }
                return true;
            }
            case MENU_ITEM_APP_INFO: {
                try {
                    Intent info = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + mSelectedApp.getPackageName()));
                    info.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(info);
                } catch (Exception e) {
                    Toast.makeText(getContext(), R.string.app_info_failed, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            case MENU_ITEM_ALLOW_CROSS_PROFILE_INTERACTION: {
                boolean newState = !checked;
                if (newState) {
                    mCrossProfilePackages.add(mSelectedApp.getPackageName());
                } else {
                    mCrossProfilePackages.remove(mSelectedApp.getPackageName());
                }
                try {
                    mService.setCrossProfilePackages(new ArrayList<>(mCrossProfilePackages));
                } catch (RemoteException ignored) {

                }
                return true;
            }
        }

        return false;
    }

    void installOrUninstall(final ApplicationInfoWrapper app, final boolean isInstall) {
        mSelectedApp = null;
        IAppInstallCallback.Stub callback = new IAppInstallCallback.Stub() {
            @Override
            public void callback(int result) {
                runOnUiThread(() ->
                        installAppCallback(result, app, isInstall));
            }
        };

        try {
            if (isInstall) {
                ((MainActivity) getActivity()).getOtherService(mIsRemote)
                        .installApp(app, callback);
            } else {
                mService.uninstallApp(app, callback);
            }
        } catch (RemoteException e) {
            // TODO: Maybe tell the user?
        }
    }

    void installAppCallback(int result, ApplicationInfoWrapper app, boolean isInstall) {
        if (result == Activity.RESULT_OK) {
            String message = getString(isInstall ? R.string.clone_success : R.string.uninstall_success);
            message = String.format(message, app.getLabel());
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            LocalBroadcastManager.getInstance(getContext())
                    .sendBroadcast(new Intent(BROADCAST_REFRESH));
        } else if (result == ShelterService.RESULT_CANNOT_INSTALL_SYSTEM_APP) {
            Toast.makeText(getContext(),
                    getString(isInstall ? R.string.clone_fail_system_app :
                            R.string.uninstall_fail_system_app), Toast.LENGTH_SHORT).show();
        }
    }

    void loadIconAndAddUnfreezeShortcut(final ApplicationInfoWrapper app, final List<ApplicationInfoWrapper> linkedApps) {
        try {
            // Call the service to load the latest icon
            mService.loadIcon(app, new ILoadIconCallback.Stub() {
                @Override
                public void callback(Bitmap icon) {
                    runOnUiThread(() -> addUnfreezeShortcut(app, linkedApps, icon));
                }
            });
        } catch (RemoteException e) {
            // Ignore
        }
    }

    void addUnfreezeShortcut(ApplicationInfoWrapper app, List<ApplicationInfoWrapper> linkedApps, Bitmap icon) {
        String id = "shelter-" + app.getPackageName();
        // First, create an Intent to be sent when clicking on the shortcut
        Intent launchIntent = new Intent(DummyActivity.PUBLIC_UNFREEZE_AND_LAUNCH);
        launchIntent.setComponent(new ComponentName(getContext(), DummyActivity.class));
        launchIntent.putExtra("packageName", app.getPackageName());
        if (linkedApps != null) {
            String appListStr = linkedApps.stream()
                    .map(ApplicationInfoWrapper::getPackageName).collect(Collectors.joining(","));
            id += appListStr.hashCode();
            // Multiple apps can be added so that
            // these "linked" apps are all unfrozen
            // before launching the main app
            // Note: PersistableBundle doesn't support String array lists inside them
            launchIntent.putExtra("linkedPackages", appListStr);
        }
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Then tell the launcher to add the shortcut
        Utility.createLauncherShortcut(getContext(), launchIntent,
                Icon.createWithBitmap(icon), id,
                app.getLabel());
    }
}
