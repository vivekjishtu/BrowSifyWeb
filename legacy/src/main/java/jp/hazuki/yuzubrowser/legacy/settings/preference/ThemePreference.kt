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

package jp.hazuki.yuzubrowser.legacy.settings.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.ui.theme.ThemeData
import jp.hazuki.yuzubrowser.ui.theme.ThemeRepository
import java.io.File
import java.io.IOException
import java.util.*

class ThemePreference(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {
    init {
        load()
    }

    fun load() {
        val dir = context.getExternalFilesDir("theme")!!

        if (!dir.isDirectory) {
            dir.delete()
        }

        if (!dir.exists()) {
            dir.mkdirs()
        }

        val noMedia = File(dir, ".nomedia")

        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }

        val themeList = ArrayList<String>()
        val valueList = ArrayList<String>()

        ThemeRepository.listThemes(context).forEach { theme ->
            val name = when (theme.id) {
                ThemeData.THEME_AUTO -> context.getString(R.string.pref_system_theme)
                ThemeData.THEME_DARK -> context.getString(R.string.pref_dark_theme)
                ThemeData.THEME_LIGHT -> context.getString(R.string.pref_light_theme)
                else -> theme.name
            }
            themeList.add(name)
            valueList.add(theme.id)
        }

        val currentValue = ThemeRepository.migrateStoredThemeSetting(context)
        if (currentValue != value) {
            value = currentValue
        }

        if (!valueList.contains(value)) {
            value = ThemeData.THEME_AUTO
        }

        entries = themeList.toTypedArray()
        entryValues = valueList.toTypedArray()
    }
}
