/*
 * Copyright (C) 2017 Hazuki
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
package jp.hazuki.yuzubrowser.legacy.settings.activity

import android.os.Bundle
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResultListener
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import jp.hazuki.yuzubrowser.core.utility.utils.ui
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.settings.preference.ThemePreference
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import jp.hazuki.yuzubrowser.ui.RestartActivity
import kotlinx.coroutines.delay

class UiSettingFragment : YuzuPreferenceFragment() {

    override fun onCreateYuzuPreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_ui_settings)
        findPreference<Preference>("theme_setting")!!.setOnPreferenceChangeListener { _, _ ->
            ui {
                delay(100L)
                startActivity(RestartActivity.createIntent(requireContext()))
            }
            true
        }

        findPreference<Preference>("theme_management")!!.setOnPreferenceClickListener {
            openFragment(ThemeManagementFragment())
            true
        }

        findPreference<Preference>("restart")!!.setOnPreferenceClickListener {
            startActivity(RestartActivity.createIntent(requireContext()))
            true
        }

        findPreference<Preference>("reader_settings")!!.setOnPreferenceClickListener {
            parentFragmentManager.commit {
                replace(R.id.container, ReaderSettingsFragment())
                addToBackStack("reader_settings")
            }
            true
        }

        bindIntSummary("swipebtn_sensitivity", AppPrefs.swipebtn_sensitivity.get(), "")
        bindIntSummary("toolbar_size_tab", AppPrefs.toolbar_tab.size.get(), "dp")
        bindIntSummary("toolbar_size_url", AppPrefs.toolbar_url.size.get(), "dp")
        bindIntSummary("toolbar_size_progress", AppPrefs.toolbar_progress.size.get(), "dp")
        bindIntSummary("toolbar_size_custom1", AppPrefs.toolbar_custom1.size.get(), "dp")
        bindIntSummary("toolbar_text_size_url", AppPrefs.toolbar_text_size_url.get(), "sp")
        bindIntSummary("tab_size_x", AppPrefs.tab_size_x.get(), "dp")
        bindIntSummary("tab_font_size", AppPrefs.tab_font_size.get(), "sp")

        bindToolbarCardSummary("ps_toolbar_tab", "toolbar_size_tab")
        bindToolbarCardSummary("ps_toolbar_url", "toolbar_size_url")
        bindToolbarCardSummary("ps_toolbar_progress", "toolbar_size_progress")
        bindToolbarCardSummary("ps_toolbar_custom", "toolbar_size_custom1")

        setFragmentResultListener(ThemeManagementFragment.REQUEST_THEME_LIST_UPDATE) { _, bundle ->
            if (bundle.getBoolean(ThemeManagementFragment.REQUEST_THEME_LIST_UPDATE)) {
                findPreference<ThemePreference>("theme_setting")!!.load()
            }
        }
    }

    private fun bindToolbarCardSummary(toolbarKey: String, sizeKey: String) {
        val toolbarPreference = findPreference<PreferenceScreen>(toolbarKey) ?: return
        val sizePreference = findPreference<Preference>(sizeKey) ?: return

        toolbarPreference.summaryProvider = Preference.SummaryProvider<PreferenceScreen> {
            val sizeSummary = sizePreference.summaryProvider?.provideSummary(sizePreference)
                ?: sizePreference.summary
                ?: ""
            "${getString(R.string.pref_toolbar_size)}: $sizeSummary"
        }
    }

    private fun bindIntSummary(key: String, defaultValue: Int, suffix: String) {
        val preference = findPreference<Preference>(key) ?: return
        preference.summaryProvider = Preference.SummaryProvider<Preference> { pref ->
            val value = pref.preferenceManager.sharedPreferences?.getInt(key, defaultValue) ?: defaultValue
            if (suffix.isEmpty()) value.toString() else "$value $suffix"
        }
    }
}
