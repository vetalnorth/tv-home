package com.mihir.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "tvos_prefs";
    private static final String KEY_PINNED = "dock_pinned_pkgs";
    private static final int COLS = 5;

    static final class AppItem {
        String pkg;
        String label;
        ComponentName component;
        Drawable icon;
        boolean isBanner;
    }

    private final List<AppItem> allApps = new ArrayList<>();
    private final Set<String> pinnedPkgs = new LinkedHashSet<>();
    private final List<View> dockViews = new ArrayList<>();
    private final List<View> gridViews = new ArrayList<>();
    private final List<View> headerPills = new ArrayList<>();

    private SmoothTvScrollView mainScroll;
    private HorizontalScrollView dockScroll;
    private LinearLayout contentBox, dockRow, gridContainer;
    private FrameLayout heroSection;
    private TextView greetingText, clockText, dateText, focusedAppTitle, wifiPill;
    private int focusedViewId = View.NO_ID;
    private PopupWindow inputsPopup;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
            updateWifi();
        }
    };

    private final BroadcastReceiver appChangesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadApps();
            rebuildUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupImmersiveMode();
        loadPinnedState();
        setContentView(buildInterface());
        reloadApps();
        rebuildUi();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(appChangesReceiver, pf);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupImmersiveMode();

        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(timeReceiver, tf);

        updateClock();
        updateWifi();

        View target = focusedViewId != View.NO_ID ? findViewById(focusedViewId) : null;
        if (target == null && !dockViews.isEmpty()) target = dockViews.get(0);
        if (target == null && !gridViews.isEmpty()) target = gridViews.get(0);
        if (target != null) target.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(timeReceiver);
        if (inputsPopup != null) inputsPopup.dismiss();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(appChangesReceiver);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (inputsPopup != null) inputsPopup.dismiss();
        if (!dockViews.isEmpty()) dockViews.get(0).requestFocus();
        mainScroll.smoothScrollTo(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (mainScroll.getScrollY() > 0) {
            if (!dockViews.isEmpty()) dockViews.get(0).requestFocus();
            mainScroll.smoothScrollTo(0, 0);
        }
    }

    private void setupImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void loadPinnedState() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> saved = sp.getStringSet(KEY_PINNED, null);
        pinnedPkgs.clear();
        if (saved != null && !saved.isEmpty()) {
            pinnedPkgs.addAll(saved);
        } else {
            pinnedPkgs.add("com.google.android.youtube.tv");
            pinnedPkgs.add("com.android.vending");
            pinnedPkgs.add("ru.kinopoisk.tv");
            pinnedPkgs.add("ru.kion.kiontv");
        }
    }

    private void savePinnedState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_PINNED, new HashSet<>(pinnedPkgs))
                .apply();
    }

    private View buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF07080D);

        // Жидкое стекло: градиент глубокого космоса с сапфировым отливом
        View glassBackdrop = new View(this);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF101626, 0xFF0A0D18, 0xFF06070B});
        glassBackdrop.setBackground(gradient);
        root.addView(glassBackdrop, new FrameLayout.LayoutParams(-1, -1));

        mainScroll = new SmoothTvScrollView(this);
        mainScroll.setVerticalScrollBarEnabled(false);
        mainScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mainScroll.setFillViewport(true);
        root.addView(mainScroll, new FrameLayout.LayoutParams(-1, -1));

        contentBox = new LinearLayout(this);
        contentBox.setOrientation(LinearLayout.VERTICAL);
        contentBox.setClipChildren(false);
        contentBox.setClipToPadding(false);
        mainScroll.addView(contentBox, new FrameLayout.LayoutParams(-1, -2));

        heroSection = new FrameLayout(this);
        heroSection.setClipChildren(false);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        contentBox.addView(heroSection, new LinearLayout.LayoutParams(-1, screenH));

        heroSection.addView(buildHeaderBar(), makeFp(-1, -2, Gravity.TOP, dp(54), dp(36), dp(54), 0));

        LinearLayout dockSection = new LinearLayout(this);
        dockSection.setOrientation(LinearLayout.VERTICAL);
        dockSection.setClipChildren(false);
        dockSection.setClipToPadding(false);
        heroSection.addView(dockSection, makeFp(-1, -2, Gravity.BOTTOM, dp(48), 0, dp(48), dp(36)));

        focusedAppTitle = makeText(24, "sans-serif-medium", Color.WHITE);
        focusedAppTitle.setSingleLine(true);
        dockSection.addView(focusedAppTitle, new LinearLayout.LayoutParams(-2, -2));
        ((LinearLayout.LayoutParams) focusedAppTitle.getLayoutParams()).setMargins(dp(16), 0, 0, dp(14));

        // Горизонтальный скроллер Дока в стиле tvOS Liquid Glass
        dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dockScroll.setClipChildren(false);
        dockScroll.setClipToPadding(false);

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER_VERTICAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setPadding(dp(20), dp(18), dp(20), dp(18));

        // Жидкая стеклянная капсула
        GradientDrawable glassPod = new GradientDrawable();
        glassPod.setCornerRadius(dp(26));
        glassPod.setColor(0x22FFFFFF);
        glassPod.setStroke(dp(1), 0x38FFFFFF);
        dockRow.setBackground(glassPod);

        dockScroll.addView(dockRow, new FrameLayout.LayoutParams(-2, -2));
        dockSection.addView(dockScroll, new LinearLayout.LayoutParams(-1, -2));

        TextView appsDivider = makeText(12, "sans-serif-medium", 0x77FFFFFF);
        appsDivider.setText("ПРИЛОЖЕНИЯ");
        appsDivider.setLetterSpacing(0.2f);
        contentBox.addView(appsDivider, makeLp(-2, -2, dp(64), dp(24), dp(64), dp(16)));

        gridContainer = new LinearLayout(this);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setClipChildren(false);
        gridContainer.setClipToPadding(false);
        contentBox.addView(gridContainer, makeLp(-1, -2, dp(64), 0, dp(64), dp(160)));

        return root;
    }

    private View buildHeaderBar() {
        FrameLayout header = new FrameLayout(this);
        header.setClipChildren(false);

        LinearLayout leftSide = new LinearLayout(this);
        leftSide.setOrientation(LinearLayout.VERTICAL);

        greetingText = makeText(11, "sans-serif-medium", 0x88FFFFFF);
        greetingText.setLetterSpacing(0.18f);
        leftSide.addView(greetingText);

        clockText = makeText(46, "sans-serif-thin", Color.WHITE);
        clockText.setIncludeFontPadding(false);
        leftSide.addView(clockText, makeLp(-2, -2, 0, dp(2), 0, dp(2)));

        dateText = makeText(11, "sans-serif-medium", 0x77FFFFFF);
        dateText.setLetterSpacing(0.12f);
        leftSide.addView(dateText);

        header.addView(leftSide, new FrameLayout.LayoutParams(dp(450), -2, Gravity.START | Gravity.TOP));

        LinearLayout rightSide = new LinearLayout(this);
        rightSide.setGravity(Gravity.CENTER_VERTICAL);
        rightSide.setClipChildren(false);

        TextView inputsBtn = createGlassPill("Входы", "⇥");
        inputsBtn.setOnClickListener(this::showInputsMenu);
        wifiPill = createGlassPill("Сеть", "◉");
        wifiPill.setOnClickListener(v -> launchIntentSafely(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        TextView settingsBtn = createGlassPill("Настройки", "⚙");
        settingsBtn.setOnClickListener(v -> launchIntentSafely(new Intent(Settings.ACTION_SETTINGS)));

        for (TextView pill : new TextView[]{inputsBtn, wifiPill, settingsBtn}) {
            rightSide.addView(pill, makeLp(-2, dp(34), dp(12), 0, 0, 0));
            headerPills.add(pill);
        }
        header.addView(rightSide, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        return header;
    }

    private TextView createGlassPill(String title, String glyph) {
        TextView pill = makeText(12, "sans-serif-medium", 0xE6FFFFFF);
        pill.setText(glyph + "  " + title);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(18), 0, dp(18), 0);
        pill.setFocusable(true);
        pill.setId(View.generateViewId());

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(17));
        pillBg.setColor(0x28FFFFFF);
        pillBg.setStroke(dp(1), 0x33FFFFFF);
        pill.setBackground(pillBg);

        pill.setOnFocusChangeListener((v, hasFocus) -> {
            pillBg.setColor(hasFocus ? Color.WHITE : 0x28FFFFFF);
            pillBg.setStroke(dp(1), hasFocus ? Color.WHITE : 0x33FFFFFF);
            ((TextView) v).setTextColor(hasFocus ? 0xFF0D1017 : 0xE6FFFFFF);
            v.animate().scaleX(hasFocus ? 1.05f : 1f).scaleY(hasFocus ? 1.05f : 1f).setDuration(90).start();
            if (hasFocus) {
                focusedViewId = v.getId();
                mainScroll.smoothScrollTo(0, 0);
            }
        });
        return pill;
    }

    private void reloadApps() {
        PackageManager pm = getPackageManager();
        allApps.clear();

        Intent tvIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> tvList = pm.queryIntentActivities(tvIntent, 0);

        Intent normalIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> normalList = pm.queryIntentActivities(normalIntent, 0);

        Set<String> seenPackages = new HashSet<>();
        seenPackages.add(getPackageName());

        List<ResolveInfo> combined = new ArrayList<>(tvList);
        for (ResolveInfo info : normalList) {
            if (!seenPackages.contains(info.activityInfo.packageName)) {
                combined.add(info);
            }
        }

        for (ResolveInfo ri : combined) {
            ActivityInfo ai = ri.activityInfo;
            if (seenPackages.contains(ai.packageName)) continue;
            seenPackages.add(ai.packageName);

            AppItem item = new AppItem();
            item.pkg = ai.packageName;
            item.label = String.valueOf(ri.loadLabel(pm));
            item.component = new ComponentName(ai.packageName, ai.name);

            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            item.isBanner = banner != null;
            item.icon = banner != null ? banner : ri.loadIcon(pm);

            allApps.add(item);
        }

        Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private void rebuildUi() {
        dockRow.removeAllViews();
        gridContainer.removeAllViews();
        dockViews.clear();
        gridViews.clear();

        List<AppItem> dockList = new ArrayList<>();
        List<AppItem> gridList = new ArrayList<>();

        for (AppItem app : allApps) {
            if (pinnedPkgs.contains(app.pkg)) {
                dockList.add(app);
            } else {
                gridList.add(app);
            }
        }

        if (dockList.isEmpty() && !allApps.isEmpty()) {
            for (int i = 0; i < Math.min(4, allApps.size()); i++) {
                AppItem a = allApps.get(i);
                dockList.add(a);
                pinnedPkgs.add(a.pkg);
            }
            gridList.removeAll(dockList);
            savePinnedState();
        }

        for (int i = 0; i < dockList.size(); i++) {
            AppItem app = dockList.get(i);
            View tile = createTile(app, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(220), dp(124));
            lp.setMargins(i == 0 ? 0 : dp(16), 0, 0, 0);
            dockRow.addView(tile, lp);
        }

        LinearLayout curRow = null;
        for (int i = 0; i < gridList.size(); i++) {
            if (i % COLS == 0) {
                curRow = new LinearLayout(this);
                curRow.setClipChildren(false);
                curRow.setClipToPadding(false);
                gridContainer.addView(curRow, makeLp(-1, -2, 0, 0, 0, dp(20)));
            }
            AppItem app = gridList.get(i);
            View tile = createTile(app, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(i % COLS == 0 ? 0 : dp(14), 0, i % COLS == COLS - 1 ? 0 : dp(14), 0);
            curRow.addView(tile, lp);
        }

        if (curRow != null) {
            for (int i = gridList.size() % COLS; i != 0 && i < COLS; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                lp.setMargins(dp(14), 0, i == COLS - 1 ? 0 : dp(14), 0);
                curRow.addView(new View(this), lp);
            }
        }

        bindNavigationGraph();

        if (!dockList.isEmpty()) {
            focusedAppTitle.setText(dockList.get(0).label);
        }
    }

    private View createTile(AppItem app, boolean inDock) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);

        FrameLayout card = new TvAspectFrame(this);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(16));
            }
        });

        // Стеклянная подложка плитки
        GradientDrawable baseBg = new GradientDrawable();
        baseBg.setColor(0xFF1B1E28);
        card.setBackground(baseBg);

        ImageView artView = new ImageView(this);
        artView.setImageDrawable(app.icon);
        artView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (app.isBanner) {
            artView.setPadding(dp(8), dp(8), dp(8), dp(8));
        } else {
            artView.setPadding(dp(22), dp(16), dp(22), dp(16));
        }
        card.addView(artView, new FrameLayout.LayoutParams(-1, -1));

        // Эффект жидкого стекла: неоновый белый контур при фокусе
        GradientDrawable focusBorder = new GradientDrawable();
        focusBorder.setCornerRadius(dp(16));
        focusBorder.setStroke(dp(3), Color.WHITE);
        View focusOverlay = new View(this);
        focusOverlay.setBackground(focusBorder);
        focusOverlay.setAlpha(0f);
        card.addView(focusOverlay, new FrameLayout.LayoutParams(-1, -1));

        cell.addView(card, new LinearLayout.LayoutParams(-1, inDock ? dp(124) : -2));

        TextView label = makeText(12, "sans-serif-medium", Color.WHITE);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0.85f);
        if (!inDock) {
            cell.addView(label, makeLp(-1, -2, 0, dp(8), 0, 0));
        }

        card.setOnClickListener(v -> executeLaunch(app));

        card.setOnLongClickListener(v -> {
            if (inDock) {
                pinnedPkgs.remove(app.pkg);
                Toast.makeText(this, "Откреплено: " + app.label, Toast.LENGTH_SHORT).show();
            } else {
                pinnedPkgs.add(app.pkg);
                Toast.makeText(this, "Закреплено в Док: " + app.label, Toast.LENGTH_SHORT).show();
            }
            savePinnedState();
            rebuildUi();
            return true;
        });

        card.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? 1.07f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(90).start();
            focusOverlay.setAlpha(hasFocus ? 1f : 0f);

            if (!hasFocus) return;
            focusedViewId = v.getId();
            focusedAppTitle.setText(app.label);

            if (inDock) {
                mainScroll.smoothScrollTo(0, 0);
                scrollDockToChild(cell); // Автоматическая плавная прокрутка ленты Дока
            } else {
                alignScrollToTile(cell);
            }
        });

        (inDock ? dockViews : gridViews).add(card);
        return cell;
    }

    private void scrollDockToChild(View child) {
        int childLeft = child.getLeft();
        int childWidth = child.getWidth();
        int scrollWidth = dockScroll.getWidth();
        // Центрируем плитку внутри ленты Дока
        int targetX = childLeft - (scrollWidth / 2) + (childWidth / 2);
        dockScroll.smoothScrollTo(Math.max(0, targetX), 0);
    }

    private void alignScrollToTile(View tile) {
        View row = (View) tile.getParent();
        if (row == null) return;
        int rowTop = gridContainer.getTop() + row.getTop();
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int scrollTarget = rowTop - (int) (screenH * 0.40f);
        int maxScroll = Math.max(0, contentBox.getHeight() - mainScroll.getHeight());
        mainScroll.smoothScrollTo(0, Math.max(0, Math.min(scrollTarget, maxScroll)));
    }

    private void bindNavigationGraph() {
        int dCount = dockViews.size();
        int gCount = gridViews.size();

        for (int i = 0; i < headerPills.size(); i++) {
            View pill = headerPills.get(i);
            pill.setNextFocusLeftId(i == 0 ? pill.getId() : headerPills.get(i - 1).getId());
            pill.setNextFocusRightId(i == headerPills.size() - 1 ? pill.getId() : headerPills.get(i + 1).getId());
            pill.setNextFocusUpId(pill.getId());
            pill.setNextFocusDownId(dCount > 0 ? dockViews.get(Math.min(dCount - 1, dCount / 2)).getId() : pill.getId());
        }

        int topId = headerPills.isEmpty() ? View.NO_ID : headerPills.get(0).getId();

        for (int i = 0; i < dCount; i++) {
            View card = dockViews.get(i);
            card.setNextFocusUpId(topId);
            card.setNextFocusLeftId(i == 0 ? card.getId() : dockViews.get(i - 1).getId());
            card.setNextFocusRightId(i == dCount - 1 ? card.getId() : dockViews.get(i + 1).getId());
            card.setNextFocusDownId(gCount > 0 ? gridViews.get(Math.min(i, gCount - 1)).getId() : card.getId());
        }

        int lastRowIndex = gCount > 0 ? (gCount - 1) / COLS : 0;
        for (int j = 0; j < gCount; j++) {
            View card = gridViews.get(j);
            int row = j / COLS;
            int col = j % COLS;

            card.setNextFocusUpId(row == 0
                    ? (dCount > 0 ? dockViews.get(Math.min(col, dCount - 1)).getId() : topId)
                    : gridViews.get(j - COLS).getId());
            card.setNextFocusDownId(j + COLS < gCount ? gridViews.get(j + COLS).getId()
                    : row < lastRowIndex ? gridViews.get(gCount - 1).getId() : card.getId());
            card.setNextFocusLeftId(col == 0 ? card.getId() : gridViews.get(j - 1).getId());
            card.setNextFocusRightId(col == COLS - 1 || j == gCount - 1 ? card.getId() : gridViews.get(j + 1).getId());
        }
    }

    private void showInputsMenu(View anchor) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xF0131722);
        bg.setStroke(dp(1), 0x30FFFFFF);
        list.setBackground(bg);

        PopupWindow popup = new PopupWindow(list, dp(220), -2, true);
        List<TextView> rows = new ArrayList<>();

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            for (TvInputInfo t : tim.getTvInputList()) {
                if (t.isPassthroughInput() && !t.isHidden(this)) {
                    CharSequence label = t.loadCustomLabel(this);
                    if (label == null || label.length() == 0) label = t.loadLabel(this);
                    rows.add(createMenuRow(String.valueOf(label), () -> {
                        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(t.getId()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        launchIntentSafely(i);
                    }));
                }
            }
        }

        if (rows.isEmpty()) {
            rows.add(createMenuRow("Настройки", () -> launchIntentSafely(new Intent(Settings.ACTION_SETTINGS))));
        }

        for (TextView r : rows) {
            r.setOnClickListener(v -> {
                popup.dismiss();
                ((Runnable) v.getTag()).run();
            });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(40)));
        }

        for (int i = 0; i < rows.size(); i++) {
            TextView r = rows.get(i);
            r.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }

        popup.setOutsideTouchable(true);
        popup.setElevation(dp(16));
        popup.setOnDismissListener(() -> {
            inputsPopup = null;
            anchor.requestFocus();
        });
        inputsPopup = popup;
        popup.showAsDropDown(anchor, 0, dp(8), Gravity.START);
        rows.get(0).requestFocus();
    }

    private TextView createMenuRow(String title, Runnable action) {
        TextView r = makeText(13, "sans-serif", Color.WHITE);
        r.setText(title);
        r.setTag(action);
        r.setId(View.generateViewId());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), 0, dp(14), 0);
        r.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(Color.TRANSPARENT);
        r.setBackground(bg);

        r.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : Color.TRANSPARENT);
            r.setTextColor(has ? 0xFF090A0F : Color.WHITE);
        });
        return r;
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        greetingText.setText(hour < 5 ? "ДОБРОЙ НОЧИ" : hour < 12 ? "ДОБРОЕ УТРО" : hour < 17 ? "ДОБРЫЙ ДЕНЬ" : "ДОБРЫЙ ВЕЧЕР");
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        clockText.setText(new SimpleDateFormat(is24 ? "H:mm" : "h:mm", Locale.getDefault()).format(now.getTime()));
        dateText.setText(new SimpleDateFormat("EEEE, d MMMM", new Locale("ru")).format(now.getTime()).toUpperCase());
    }

    private void updateWifi() {
        String name = "Сеть";
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                String ssid = wm.getConnectionInfo().getSSID();
                if (ssid != null && !ssid.contains("unknown ssid")) name = ssid.replace("\"", "");
                if (wm.getConnectionInfo().getNetworkId() == -1) name = "Нет сети";
            }
        } catch (SecurityException ignored) { }
        wifiPill.setText("◉  " + name);
    }

    private void executeLaunch(AppItem app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        intent.setComponent(app.component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!launchIntentSafely(intent)) {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(app.pkg);
            if (fallback != null) launchIntentSafely(fallback);
        }
    }

    private boolean launchIntentSafely(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private TextView makeText(float sp, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private int dp(float val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    private static FrameLayout.LayoutParams makeFp(int w, int h, int gravity, int l, int t, int r, int b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static LinearLayout.LayoutParams makeLp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP && !headerPills.isEmpty()) {
            showInputsMenu(headerPills.get(0));
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    static final class TvAspectFrame extends FrameLayout {
        TvAspectFrame(Context c) { super(c); }
        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(w * 9 / 16, MeasureSpec.EXACTLY));
        }
    }

    static final class SmoothTvScrollView extends ScrollView {
        SmoothTvScrollView(Context c) { super(c); }
        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            return 0;
        }
    }
}
