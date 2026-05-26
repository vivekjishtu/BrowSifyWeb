/*
 * Copyright (C) 2026 Vivek Jishtu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package jp.hazuki.yuzubrowser.ui.theme

import android.content.Context
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs

object ThemeDataResolver {
    @JvmStatic
    fun resolve(context: Context): ThemeData? {
        return ThemeData.getInstance()
            ?: ThemeData.createInstanceIfNeed(context.applicationContext, AppPrefs.theme_setting.get())
    }
}
