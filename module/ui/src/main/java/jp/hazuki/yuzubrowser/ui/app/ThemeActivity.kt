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
import androidx.core.view.updatePadding
import jp.hazuki.yuzubrowser.core.utility.utils.createLanguageConfig
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import jp.hazuki.yuzubrowser.ui.theme.ThemeData

@SuppressLint("Registered")
open class ThemeActivity : AppCompatActivity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge display.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
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
    }

    protected open fun shouldApplySystemBarPadding(): Boolean = true

    override fun attachBaseContext(newBase: Context) {
        val application = newBase.applicationContext
        val selectedTheme = PrefPool.getSharedPref(application).getString(theme_setting, ThemeData.THEME_AUTO)
            ?: ThemeData.THEME_AUTO
        if (selectedTheme == ThemeData.THEME_AUTO) {
            // Recreate auto theme on each attach so it tracks system day/night changes.
            ThemeData.createInstance(newBase, ThemeData.THEME_AUTO)
        } else if (!ThemeData.isLoaded() || ThemeData.getLoadedTheme() != selectedTheme) {
            ThemeData.createInstance(application, selectedTheme)
        }

        val isLightMode = isLightMode(selectedTheme, newBase)
        val config = newBase.createLanguageConfig(AppPrefs.language.get())

        applyThemeMode(isLightMode, selectedTheme)
        if (selectedTheme != ThemeData.THEME_AUTO) {
            config.updateTheme(isLightMode)
        }

        super.attachBaseContext(ContextCompat(newBase.createConfigurationContext(config), newBase))
    }

    private fun applyThemeMode(isLightMode: Boolean, selectedTheme: String) {
        if (selectedTheme == ThemeData.THEME_AUTO) {
            if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            return
        }
        val defaultMode = AppCompatDelegate.getDefaultNightMode()
        if (isLightMode) {
            if (defaultMode != AppCompatDelegate.MODE_NIGHT_NO) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        } else {
            if (defaultMode != AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
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

    companion object {
        private const val theme_setting = "theme_setting"
    }
}
