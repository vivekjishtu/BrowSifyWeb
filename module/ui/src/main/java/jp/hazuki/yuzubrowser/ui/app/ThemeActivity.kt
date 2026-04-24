/*
 * Copyright (C) 2017-2019 Hazuki
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

package jp.hazuki.yuzubrowser.ui.app

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.DynamicColors
import jp.hazuki.yuzubrowser.core.utility.utils.createLanguageConfig
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import jp.hazuki.yuzubrowser.ui.theme.ThemeData
import jp.hazuki.yuzubrowser.ui.theme.ThemeRepository

@SuppressLint("Registered")
open class ThemeActivity : AppCompatActivity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge display.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (isDynamicColorEnabled()) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (shouldApplySystemBarPadding()) {
            val content = findViewById<View>(android.R.id.content)
            if (content != null) {
                ViewCompat.setOnApplyWindowInsetsListener(content) { v, windowInsets ->
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    v.updatePadding(
                        left = insets.left,
                        right = insets.right,
                        top = insets.top,
                        bottom = insets.bottom
                    )
                    windowInsets
                }
            }
        }
        updateSystemBarIcons()
    }

    protected open fun shouldApplySystemBarPadding(): Boolean = true

    override fun attachBaseContext(newBase: Context) {
        val application = newBase.applicationContext
        val selectedTheme = ThemeRepository.migrateStoredThemeSetting(application)
        if (selectedTheme == ThemeData.THEME_AUTO) {
            // Recreate auto theme on each attach so it tracks system day/night changes.
            ThemeData.createInstance(newBase, ThemeData.THEME_AUTO)
        } else if (!ThemeData.isLoaded() || ThemeData.getLoadedTheme() != selectedTheme) {
            ThemeData.createInstance(application, selectedTheme)
        }

        val isLightMode = isLightMode(selectedTheme, newBase)
        val config = newBase.createLanguageConfig(AppPrefs.language.get())

        applyThemeMode(newBase, isLightMode, selectedTheme)
        if (selectedTheme != ThemeData.THEME_AUTO) {
            config.updateTheme(isLightMode)
        }

        super.attachBaseContext(ContextCompat(newBase.createConfigurationContext(config), newBase))
    }

    private fun applyThemeMode(context: Context, isLightMode: Boolean, selectedTheme: String) {
        val mode = when {
            selectedTheme == ThemeData.THEME_AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            isLightMode -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }

        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val appMode = when (mode) {
                AppCompatDelegate.MODE_NIGHT_YES -> UiModeManager.MODE_NIGHT_YES
                AppCompatDelegate.MODE_NIGHT_NO -> UiModeManager.MODE_NIGHT_NO
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            try {
                val getter = uiModeManager.javaClass.getMethod("getApplicationNightMode")
                val setter = uiModeManager.javaClass.getMethod("setApplicationNightMode", Int::class.javaPrimitiveType)
                val currentMode = getter.invoke(uiModeManager) as Int
                if (currentMode != appMode) {
                    setter.invoke(uiModeManager, appMode)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateSystemBarIcons() {
        val theme = ThemeData.getInstance() ?: return
        val isLight = theme.lightTheme
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight

        // Allow theme override if explicitly set
        theme.resolvedTheme?.let { resolved ->
            if (resolved.flags.containsKey("statusBarDarkIcon")) {
                controller.isAppearanceLightStatusBars = resolved.flag("statusBarDarkIcon")
            }
        }
    }

    private fun isLightMode(selectedTheme: String, context: Context): Boolean {
        if (selectedTheme == ThemeData.THEME_AUTO) {
            val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return nightMask != Configuration.UI_MODE_NIGHT_YES
        }
        return ThemeData.getInstance()?.lightTheme ?: false
    }

    private fun Configuration.updateTheme(isLightMode: Boolean) {
        val newNightMode = if (isLightMode) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES

        uiMode = newNightMode or (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
    }

    protected open fun isDynamicColorEnabled(): Boolean {
        return AppPrefs.theme_setting.get() == "dynamic"
    }

    private class ContextCompat(
        configContext: Context,
        private val baseActivityContext: Context
    ) : ContextWrapper(configContext) {

        override fun getSystemService(name: String): Any? {
            return baseActivityContext.getSystemService(name)
        }

        override fun getSystemServiceName(serviceClass: Class<*>): String? {
            return baseActivityContext.getSystemServiceName(serviceClass)
        }
    }
}
