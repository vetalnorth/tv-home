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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
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

    private static final String PREFS_NAME = "tvhome_prefs";
    private static final String KEY_PINNED = "pinned_packages";
    private static final int COLS = 5;

    static final class App {
        String pkg;
        String label;
        ComponentName component;
        Drawable art;
        boolean isBanner;
    }

    private final List<App> allInstalledApps = new ArrayList<>();
    private final Set<String> pinnedPkgs = new LinkedHashSet<>();
    private final List<View> dockCards = new ArrayList<>();
    private final List<View> gridCards = new ArrayList<>();
    private final List<View> pills = new ArrayList<>();

    private ScrollView scroll;
    private LinearLayout content, dockRow, grid;
    private FrameLayout hero;
    private TextView greeting, clock, ampm, date, title, appsLabel, wifiPill;
    private int lastFocusedId = View.NO_ID;
    private PopupWindow inputsPopup;

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
            updateWifi();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadAppsFromSystem();
            renderUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        loadPinnedPreferences();
        setContentView(buildBaseLayout());
        loadAppsFromSystem();
        renderUi();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(tickReceiver, tf);

        updateClock();
        updateWifi();

        View target = lastFocusedId != View.NO_ID ? findViewById(lastFocusedId) : null;
        if (target == null && !dockCards.isEmpty()) target = dockCards.get(0);
        if (target == null && !gridCards.isEmpty()) target = gridCards.get(0);
        if (target != null) target.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(tickReceiver);
        if (inputsPopup != null) inputsPopup.dismiss();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (inputsPopup != null) inputsPopup.dismiss();
        if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
        scroll.smoothScrollTo(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (scroll.getScrollY() > 0) {
            if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
            scroll.smoothScrollTo(0, 0);
        }
    }

    private void loadPinnedPreferences() {
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

    private void savePinnedPreferences() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sp.edit().putStringSet(KEY_PINNED, new HashSet<>(pinnedPkgs)).apply();
    }

    private View buildBaseLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0C0E14);

        View backgroundGradient = new View(this);
        backgroundGradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF121622, 0xFF0A0C10, 0xFF060709}));
        root.addView(backgroundGradient, new FrameLayout.LayoutParams(-1, -1));

        scroll = new ScrollView(this) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) {
                return 0;
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));

        hero = new FrameLayout(this);
        hero.setClipChildren(false);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        content.addView(hero, new LinearLayout.LayoutParams(-1, screenH));

        hero.addView(buildHeader(), makeFp(-1, -2, Gravity.TOP, dp(48), dp(32), dp(48), 0));

        LinearLayout dockSection = new LinearLayout(this);
        dockSection.setOrientation(LinearLayout.VERTICAL);
        dockSection.setClipChildren(false);
        dockSection.setClipToPadding(false);
        hero.addView(dockSection, makeFp(-1, -2, Gravity.BOTTOM, dp(48), 0, dp(48), dp(36)));

        title = createText(24, "sans-serif-medium", Color.WHITE);
        title.setSingleLine(true);
        dockSection.addView(title, new LinearLayout.LayoutParams(-2, -2));
        ((LinearLayout.LayoutParams) title.getLayoutParams()).setMargins(dp(14), 0, 0, dp(12));

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER_VERTICAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setPadding(dp(18), dp(16), dp(18), dp(16));

        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setCornerRadius(dp(20));
        dockBg.setColor(0x24FFFFFF);
        dockBg.setStroke(dp(1), 0x22FFFFFF);
        dockRow.setBackground(dockBg);
        dockSection.addView(dockRow, new LinearLayout.LayoutParams(-1, -2));

        appsLabel = createText(13, "sans-serif-medium", 0x88FFFFFF);
        appsLabel.setText("ВСЕ ПРИЛОЖЕНИЯ  (зажмите ОК на плитке, чтобы закрепить в Док)");
        appsLabel.setLetterSpacing(0.08f);
        content.addView(appsLabel, makeLp(-2, -2, dp(56), dp(24), dp(56), dp(16)));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        content.addView(grid, makeLp(-1, -2, dp(56), 0, dp(56), dp(120)));

        return root;
    }

    private View buildHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setClipChildren(false);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        greeting = createText(11, "sans-serif-medium", 0x99FFFFFF);
        greeting.setLetterSpacing(0.15f);
        greeting.setSingleLine(true);
        left.addView(greeting);

        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.BOTTOM);
        clock = createText(46, "sans-serif-thin", Color.WHITE);
        clock.setIncludeFontPadding(false);
        clockRow.addView(clock);
        ampm = createText(12, "sans-serif-light", 0xCCFFFFFF);
        clockRow.addView(ampm, makeLp(-2, -2, dp(6), 0, 0, dp(6)));
        left.addView(clockRow, makeLp(-2, -2, 0, dp(2), 0, dp(2)));

        date = createText(11, "sans-serif-medium", 0x88FFFFFF);
        date.setSingleLine(true);
        date.setLetterSpacing(0.12f);
        left.addView(date);

        header.addView(left, new FrameLayout.LayoutParams(dp(400), -2, Gravity.START | Gravity.TOP));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);

        TextView inputs = makePill("Входы", "⇥");
        inputs.setOnClickListener(this::showInputsDialog);
        wifiPill = makePill("Сеть", "◉");
        wifiPill.setOnClickListener(v -> launchIntent(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        TextView settings = makePill("Настройки", "⚙");
        settings.setOnClickListener(v -> launchIntent(new Intent(Settings.ACTION_SETTINGS)));

        for (TextView p : new TextView[]{inputs, wifiPill, settings}) {
            right.addView(p, makeLp(-2, dp(32), dp(10), 0, 0, 0));
            pills.add(p);
        }
        header.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        return header;
    }

    private TextView makePill(String label, String icon) {
        TextView p = createText(12, "sans-serif-medium", 0xE6FFFFFF);
        p.setText(icon + "  " + label);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(16), 0, dp(16), 0);
        p.setFocusable(true);
        p.setId(View.generateViewId());

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0x40000000);
        bg.setStroke(dp(1), 0x24FFFFFF);
        p.setBackground(bg);

        p.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : 0x40000000);
            ((TextView) v).setTextColor(has ? 0xFF0F1015 : 0xE6FFFFFF);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
            if (has) {
                lastFocusedId = v.getId();
                scroll.smoothScrollTo(0, 0);
            }
        });
        return p;
    }

    private void loadAppsFromSystem() {
        PackageManager pm = getPackageManager();
        allInstalledApps.clear();

        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(leanback, 0);

        Intent standard = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> normalActivities = pm.queryIntentActivities(standard, 0);

        Set<String> seen = new HashSet<>();
        seen.add(getPackageName());

        List<ResolveInfo> combined = new ArrayList<>(activities);
        for (ResolveInfo r : normalActivities) {
            if (!seen.contains(r.activityInfo.packageName)) {
                combined.add(r);
            }
        }

        for (ResolveInfo ri : combined) {
            ActivityInfo ai = ri.activityInfo;
            if (seen.contains(ai.packageName)) continue;
            seen.add(ai.packageName);

            App app = new App();
            app.pkg = ai.packageName;
            app.label = String.valueOf(ri.loadLabel(pm));
            app.component = new ComponentName(ai.packageName, ai.name);

            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            app.isBanner = banner != null;
            app.art = banner != null ? banner : ri.loadIcon(pm);

            allInstalledApps.add(app);
        }

        Collections.sort(allInstalledApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private void renderUi() {
        dockRow.removeAllViews();
        grid.removeAllViews();
        dockCards.clear();
        gridCards.clear();

        List<App> dockApps = new ArrayList<>();
        List<App> gridApps = new ArrayList<>();

        for (App a : allInstalledApps) {
            if (pinnedPkgs.contains(a.pkg)) {
                dockApps.add(a);
            } else {
                gridApps.add(a);
            }
        }

        // Если док пустой, закрепляем первые 4 приложения
        if (dockApps.isEmpty() && !allInstalledApps.isEmpty()) {
            for (int i = 0; i < Math.min(4, allInstalledApps.size()); i++) {
                App a = allInstalledApps.get(i);
                dockApps.add(a);
                pinnedPkgs.add(a.pkg);
            }
            gridApps.removeAll(dockApps);
            savePinnedPreferences();
        }

        // Отрисовка Дока
        for (int i = 0; i < dockApps.size(); i++) {
            App app = dockApps.get(i);
            View tile = createTile(app, true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(220), dp(124));
            p.setMargins(i == 0 ? 0 : dp(14), 0, 0, 0);
            dockRow.addView(tile, p);
        }

        // Отрисовка Сетки приложений
        LinearLayout row = null;
        for (int i = 0; i < gridApps.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                grid.addView(row, makeLp(-1, -2, 0, 0, 0, dp(18)));
            }
            App app = gridApps.get(i);
            View tile = createTile(app, false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(i % COLS == 0 ? 0 : dp(12), 0, i % COLS == COLS - 1 ? 0 : dp(12), 0);
            row.addView(tile, p);
        }

        if (row != null) {
            for (int i = gridApps.size() % COLS; i != 0 && i < COLS; i++) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1f);
                p.setMargins(dp(12), 0, i == COLS - 1 ? 0 : dp(12), 0);
                row.addView(new View(this), p);
            }
        }

        bindFocusNavigation();

        if (!dockApps.isEmpty()) {
            title.setText(dockApps.get(0).label);
        }
    }

    private View createTile(App app, boolean inDock) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);

        FrameLayout card = new AspectRatioFrame(this);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
            }
        });

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E2129);
        card.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.art);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (app.isBanner) {
            icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        } else {
            icon.setPadding(dp(22), dp(16), dp(22), dp(16));
        }
        card.addView(icon, new FrameLayout.LayoutParams(-1, -1));

        GradientDrawable focusBorder = new GradientDrawable();
        focusBorder.setCornerRadius(dp(14));
        focusBorder.setStroke(dp(3), Color.WHITE);
        View focusHighlight = new View(this);
        focusHighlight.setBackground(focusBorder);
        focusHighlight.setAlpha(0f);
        card.addView(focusHighlight, new FrameLayout.LayoutParams(-1, -1));

        wrapper.addView(card, new LinearLayout.LayoutParams(-1, inDock ? dp(124) : -2));

        TextView label = createText(12, "sans-serif-medium", Color.WHITE);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0.85f);
        if (!inDock) {
            wrapper.addView(label, makeLp(-1, -2, 0, dp(8), 0, 0));
        }

        card.setOnClickListener(v -> launchApp(app));

        // Долгое нажатие ОК на пульте: закрепление/открепление
        card.setOnLongClickListener(v -> {
            if (inDock) {
                pinnedPkgs.remove(app.pkg);
                Toast.makeText(this, app.label + " убран из Дока", Toast.LENGTH_SHORT).show();
            } else {
                pinnedPkgs.add(app.pkg);
                Toast.makeText(this, app.label + " закреплен в Доке", Toast.LENGTH_SHORT).show();
            }
            savePinnedPreferences();
            renderUi();
            return true;
        });

        card.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.06f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            focusHighlight.setAlpha(hasFocus ? 1f : 0f);

            if (!hasFocus) return;
            lastFocusedId = v.getId();
            title.setText(app.label);

            if (inDock) {
                scroll.smoothScrollTo(0, 0);
            } else {
                scrollFocusToRow(wrapper);
            }
        });

        (inDock ? dockCards : gridCards).add(card);
        return wrapper;
    }

    private void scrollFocusToRow(View view) {
        View row = (View) view.getParent();
        int y = grid.getTop() + row.getTop();
        int screenH = getResources().getDisplayMetrics().heightPixels;
        View band = (View) dockRow.getParent();
        int target = grid.indexOfChild(row) == 0
                ? hero.getTop() + band.getTop() + dockRow.getTop() - dp(20)
                : y - (int) (screenH * 0.45f);
        int max = Math.max(0, content.getHeight() - scroll.getHeight());
        scroll.smoothScrollTo(0, Math.max(0, Math.min(target, max)));
    }

    private void bindFocusNavigation() {
        int d = dockCards.size();
        int g = gridCards.size();

        for (int i = 0; i < pills.size(); i++) {
            View p = pills.get(i);
            p.setNextFocusLeftId(i == 0 ? p.getId() : pills.get(i - 1).getId());
            p.setNextFocusRightId(i == pills.size() - 1 ? p.getId() : pills.get(i + 1).getId());
            p.setNextFocusUpId(p.getId());
            p.setNextFocusDownId(d > 0 ? dockCards.get(Math.min(d - 1, d / 2)).getId() : p.getId());
        }

        int topId = pills.isEmpty() ? View.NO_ID : pills.get(0).getId();

        for (int i = 0; i < d; i++) {
            View c = dockCards.get(i);
            c.setNextFocusUpId(topId);
            c.setNextFocusLeftId(i == 0 ? c.getId() : dockCards.get(i - 1).getId());
            c.setNextFocusRightId(i == d - 1 ? c.getId() : dockCards.get(i + 1).getId());
            c.setNextFocusDownId(g > 0 ? gridCards.get(Math.min(i, g - 1)).getId() : c.getId());
        }

        int lastRow = g > 0 ? (g - 1) / COLS : 0;
        for (int j = 0; j < g; j++) {
            View c = gridCards.get(j);
            int r = j / COLS;
            int col = j % COLS;

            c.setNextFocusUpId(r == 0
                    ? (d > 0 ? dockCards.get(Math.min(col, d - 1)).getId() : topId)
                    : gridCards.get(j - COLS).getId());
            c.setNextFocusDownId(j + COLS < g ? gridCards.get(j + COLS).getId()
                    : r < lastRow ? gridCards.get(g - 1).getId() : c.getId());
            c.setNextFocusLeftId(col == 0 ? c.getId() : gridCards.get(j - 1).getId());
            c.setNextFocusRightId(col == COLS - 1 || j == g - 1 ? c.getId() : gridCards.get(j + 1).getId());
        }
    }

    private void showInputsDialog(View anchor) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0xF0181A22);
        bg.setStroke(dp(1), 0x24FFFFFF);
        list.setBackground(bg);

        PopupWindow pw = new PopupWindow(list, dp(220), -2, true);
        List<TextView> rows = new ArrayList<>();

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            for (TvInputInfo t : tim.getTvInputList()) {
                if (t.isPassthroughInput() && !t.isHidden(this)) {
                    CharSequence label = t.loadCustomLabel(this);
                    if (label == null || label.length() == 0) label = t.loadLabel(this);
                    final String titleStr = String.valueOf(label);
                    rows.add(createInputRow(titleStr, () -> {
                        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(t.getId()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        launchIntent(i);
                    }));
                }
            }
        }

        if (rows.isEmpty()) {
            rows.add(createInputRow("Настройки ТВ", () -> launchIntent(new Intent(Settings.ACTION_SETTINGS))));
        }

        for (TextView r : rows) {
            r.setOnClickListener(v -> {
                pw.dismiss();
                ((Runnable) v.getTag()).run();
            });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(38)));
        }

        for (int i = 0; i < rows.size(); i++) {
            TextView r = rows.get(i);
            r.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }

        pw.setOutsideTouchable(true);
        pw.setElevation(dp(16));
        pw.setOnDismissListener(() -> {
            inputsPopup = null;
            anchor.requestFocus();
        });
        inputsPopup = pw;
        pw.showAsDropDown(anchor, 0, dp(8), Gravity.START);
        rows.get(0).requestFocus();
    }

    private TextView createInputRow(String label, Runnable action) {
        TextView r = createText(13, "sans-serif", Color.WHITE);
        r.setText(label);
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
            r.setTextColor(has ? 0xFF111111 : Color.WHITE);
        });
        return r;
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 5 ? "ДОБРОЙ НОЧИ" : h < 12 ? "ДОБРОЕ УТРО" : h < 17 ? "ДОБРЫЙ ДЕНЬ" : "ДОБРЫЙ ВЕЧЕР");
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(is24 ? "H:mm" : "h:mm", Locale.getDefault()).format(now.getTime()));
        ampm.setText(is24 ? "" : (h < 12 ? "AM" : "PM"));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", new Locale("ru")).format(now.getTime()).toUpperCase());
    }

    private void updateWifi() {
        String name = "Сеть";
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                String s = wm.getConnectionInfo().getSSID();
                if (s != null && !s.contains("unknown ssid")) name = s.replace("\"", "");
                if (wm.getConnectionInfo().getNetworkId() == -1) name = "Нет сети";
            }
        } catch (SecurityException ignored) { }
        wifiPill.setText("◉  " + name);
    }

    private void launchApp(App app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        intent.setComponent(app.component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!launchIntent(intent)) {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(app.pkg);
            if (fallback != null) launchIntent(fallback);
        }
    }

    private boolean launchIntent(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private TextView createText(float sp, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP && !pills.isEmpty()) {
            showInputsDialog(pills.get(0));
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    static final class AspectRatioFrame extends FrameLayout {
        AspectRatioFrame(Context c) { super(c); }
        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(w * 9 / 16, MeasureSpec.EXACTLY));
        }
    }
}
