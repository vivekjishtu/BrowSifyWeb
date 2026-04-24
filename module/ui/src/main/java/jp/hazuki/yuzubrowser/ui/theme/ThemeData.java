/*
 * Copyright (C) 2017-2019 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package jp.hazuki.yuzubrowser.ui.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.view.View;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import jp.hazuki.yuzubrowser.ui.R;

public class ThemeData {
    public static final String THEME_LIGHT = ThemeRepository.THEME_LIGHT;
    public static final String THEME_DARK = ThemeRepository.THEME_DARK;
    public static final String THEME_AUTO = ThemeRepository.THEME_SYSTEM;

    public Drawable tabBackgroundNormal, tabBackgroundSelect;
    public int tabTextColorNormal, tabTextColorLock, tabTextColorPin, tabTextColorSelect, tabAccentColor, tabDividerColor;
    public int scrollbarAccentColor;
    public boolean showTabDivider;
    public int progressColor, progressIndeterminateColor;
    public int toolbarBackgroundColor;
    public int toolbarTextColor, toolbarImageColor;
    public ShapeDrawable toolbarButtonBackgroundPress, toolbarTextButtonBackgroundPress;
    public int qcItemBackgroundColorNormal, qcItemBackgroundColorSelect, qcItemColor;
    public int statusBarColor;
    private boolean statusBarDarkIcon;
    public boolean refreshUseDark;
    public boolean lightTheme;
    @Nullable
    public final ResolvedTheme resolvedTheme;

    private ThemeData(Context context, ResolvedTheme theme) {
        resolvedTheme = theme;
        lightTheme = theme.isLight();

        tabTextColorNormal = theme.color("tabTextNormal");
        tabTextColorLock = theme.color("tabTextLock");
        tabTextColorPin = theme.color("tabTextPin");
        tabTextColorSelect = theme.color("tabTextSelected");
        tabAccentColor = theme.color("tabAccent");
        tabDividerColor = theme.color("tabDivider");
        scrollbarAccentColor = theme.color("scrollbarAccent");
        showTabDivider = theme.flag("showTabDivider");

        progressColor = theme.color("progress");
        progressIndeterminateColor = theme.color("progressIndeterminate");

        toolbarBackgroundColor = theme.color("toolbarBackground");
        toolbarTextColor = theme.color("toolbarText");
        toolbarImageColor = theme.color("toolbarIcon");

        int toolbarButtonPress = theme.color("toolbarButtonPress");
        if (toolbarButtonPress != 0) {
            int padding = context.getResources().getDimensionPixelOffset(R.dimen.dimen_theme_padding);
            Rect paddingRect = new Rect(padding, padding, padding, padding);
            Rect textPaddingRect = new Rect(padding, 0, padding, 0);

            toolbarButtonBackgroundPress = new ShapeDrawable(new RectShape());
            toolbarButtonBackgroundPress.setPadding(paddingRect);
            toolbarButtonBackgroundPress.getPaint().setColor(toolbarButtonPress);

            toolbarTextButtonBackgroundPress = new ShapeDrawable(new RectShape());
            toolbarTextButtonBackgroundPress.setPadding(textPaddingRect);
            toolbarTextButtonBackgroundPress.getPaint().setColor(toolbarButtonPress);
        }

        qcItemBackgroundColorNormal = theme.color("qcItemBackgroundNormal");
        qcItemBackgroundColorSelect = theme.color("qcItemBackgroundSelected");
        qcItemColor = theme.color("qcItem");
        statusBarColor = theme.color("statusBar");
        statusBarDarkIcon = theme.flag("statusBarDarkIcon");
        refreshUseDark = theme.flag("pullToRefreshDark");
    }

    public static boolean isEnabled() {
        return sInstance != null;
    }

    @Nullable
    public static ThemeData getInstance() {
        return sInstance;
    }

    @Nullable
    public static ThemeData createInstanceIfNeed(@NonNull Context context, @Nullable String folder) {
        String normalizedTheme = ThemeRepository.normalizeThemeId(folder);
        if (!isLoaded || !Objects.equals(normalizedTheme, loadedTheme)) {
            return createInstance(context, folder);
        } else {
            return sInstance;
        }
    }

    @Nullable
    public static ThemeData createInstance(@NonNull Context context, @Nullable String folder) {
        isLoaded = true;
        String normalizedTheme = ThemeRepository.normalizeThemeId(folder);
        ResolvedTheme resolvedTheme = ThemeRepository.resolve(context, normalizedTheme);
        if (resolvedTheme != null) {
            sInstance = new ThemeData(context, resolvedTheme);
            loadedTheme = normalizedTheme;
        } else {
            sInstance = null;
            loadedTheme = null;
        }
        return sInstance;
    }

    private static ThemeData sInstance;

    private static boolean isLoaded = false;

    public static boolean isLoaded() {
        return isLoaded;
    }

    private static String loadedTheme = null;

    @Nullable
    public static String getLoadedTheme() {
        return loadedTheme;
    }

    public static boolean isUseLightStatusBar() {
        return sInstance != null && (sInstance.statusBarDarkIcon || sInstance.isLightStatusBar());
    }

    public boolean useLightStatusBarAppearance() {
        return statusBarDarkIcon || isLightStatusBar();
    }

    @SuppressWarnings("deprecation")
    public static int getSystemUiVisibilityFlag() {
        if (isUseLightStatusBar()) {
            return View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            return 0;
        }
    }

    public boolean isLightStatusBar() {
        return isColorLight(statusBarColor);
    }

    public static boolean isColorLight(int color) {
        double lightness = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return lightness > 0.7;
    }
}
