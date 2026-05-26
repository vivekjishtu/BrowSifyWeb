/*
 * Copyright (C) 2017-2020 Hazuki
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

package jp.hazuki.yuzubrowser.ui.settings.fragment

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import jp.hazuki.yuzubrowser.core.utility.extensions.convertDpToPx
import jp.hazuki.yuzubrowser.ui.PREFERENCE_FILE_NAME
import jp.hazuki.yuzubrowser.ui.R
import jp.hazuki.yuzubrowser.ui.theme.ThemeData

abstract class YuzuBasePreferenceFragment : PreferenceFragmentCompat() {
    var preferenceResId: Int = 0
        private set

    abstract fun onCreateYuzuPreferences(savedInstanceState: Bundle?, rootKey: String?)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = PREFERENCE_FILE_NAME
        onCreateYuzuPreferences(savedInstanceState, rootKey)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return try {
            super.onCreateView(inflater, container, savedInstanceState)
        } finally {
            activity?.let {
                preferenceManager.sharedPreferencesName = PREFERENCE_FILE_NAME
                setDivider(SettingsDividerDrawable(it, resolveSettingsDividerColor()))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setBackgroundColor(resolveSettingsBackgroundColor())

        val recyclerView = view.findViewById<RecyclerView>(androidx.preference.R.id.recycler_view) ?: return
        tintPreferenceIcons(recyclerView)
        recyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                tintPreferenceIcon(view)
            }

            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        val activity = activity ?: return

        preferenceScreen?.run {
            val key = arguments?.getString(ARG_PREFERENCE_ROOT)
            val title = if (!key.isNullOrEmpty()) findPreference<Preference>(key)?.title
                ?: title else title
            activity.title = if (TextUtils.isEmpty(title)) getText(R.string.pref_settings) else title
        }
    }

    override fun addPreferencesFromResource(preferencesResId: Int) {
        super.addPreferencesFromResource(preferencesResId)
        this.preferenceResId = preferencesResId
    }

    override fun setPreferencesFromResource(preferencesResId: Int, key: String?) {
        super.setPreferencesFromResource(preferencesResId, key)
        this.preferenceResId = preferencesResId
    }

    open fun onPreferenceStartScreen(pref: PreferenceScreen): Boolean = false

    protected fun openFragment(fragment: PreferenceFragmentCompat) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun tintPreferenceIcons(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            tintPreferenceIcon(recyclerView.getChildAt(i))
        }
    }

    private fun tintPreferenceIcon(itemView: View) {
        val themeData = ThemeData.getInstance()
        val recyclerView = itemView.parent as? RecyclerView
        val preference = recyclerView?.let { getPreferenceForItemView(it, itemView) }
        val categoryColor = resolveSettingsCategoryColor(themeData)
        itemView.findViewById<ImageView>(android.R.id.icon)?.let { icon ->
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(resolveSettingsIconColor(themeData)))
        }
        itemView.findViewById<SwitchCompat>(androidx.preference.R.id.switchWidget)?.let { switch ->
            applySwitchTint(switch, themeData)
        }
        itemView.findViewById<TextView>(android.R.id.title)?.setTextColor(
            if (preference is PreferenceCategory) categoryColor else resolveSettingsTextColor(themeData)
        )
        itemView.findViewById<TextView>(android.R.id.summary)?.setTextColor(resolveSettingsSummaryColor(themeData))
    }

    private fun resolveSettingsBackgroundColor(): Int {
        val themeData = ThemeData.getInstance()
        return when {
            themeData != null && themeData.settingsBackgroundColor != 0 -> themeData.settingsBackgroundColor
            themeData != null && themeData.menuBackgroundColor != 0 -> themeData.menuBackgroundColor
            themeData != null && themeData.toolbarBackgroundColor != 0 -> themeData.toolbarBackgroundColor
            themeData?.lightTheme == true -> 0xFFFFFFFF.toInt()
            else -> 0xFF303030.toInt()
        }
    }

    private fun resolveSettingsTextColor(themeData: ThemeData?): Int {
        return when {
            themeData != null && themeData.settingsTextColor != 0 -> themeData.settingsTextColor
            themeData != null && themeData.menuTextColor != 0 -> themeData.menuTextColor
            themeData != null && themeData.toolbarTextColor != 0 -> themeData.toolbarTextColor
            themeData?.lightTheme == true -> 0xFF222222.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }

    private fun resolveSettingsSummaryColor(themeData: ThemeData?): Int {
        return when {
            themeData != null && themeData.settingsSummaryColor != 0 -> themeData.settingsSummaryColor
            themeData != null && themeData.menuTextColor != 0 -> applyAlpha(themeData.menuTextColor, 0.72f)
            themeData?.lightTheme == true -> 0x8A000000.toInt()
            else -> 0xB3FFFFFF.toInt()
        }
    }

    private fun resolveSettingsIconColor(themeData: ThemeData?): Int {
        return when {
            themeData != null && themeData.settingsIconColor != 0 -> themeData.settingsIconColor
            themeData != null && themeData.menuIconColor != 0 -> themeData.menuIconColor
            themeData != null && themeData.toolbarImageColor != 0 -> themeData.toolbarImageColor
            themeData?.lightTheme == true -> 0xFF666666.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }

    private fun resolveSettingsDividerColor(): Int {
        val themeData = ThemeData.getInstance()
        return when {
            themeData != null && themeData.settingsDividerColor != 0 -> themeData.settingsDividerColor
            themeData != null && themeData.menuDividerColor != 0 -> themeData.menuDividerColor
            themeData != null && themeData.tabListDividerColor != 0 -> themeData.tabListDividerColor
            themeData?.lightTheme == true -> 0xFFE0E0E0.toInt()
            else -> 0x33FFFFFF
        }
    }

    private fun resolveSettingsCategoryColor(themeData: ThemeData?): Int {
        return when {
            themeData != null && themeData.settingsCategoryColor != 0 -> themeData.settingsCategoryColor
            themeData != null && themeData.settingsSummaryColor != 0 -> themeData.settingsSummaryColor
            themeData?.lightTheme == true -> 0xFF5F6368.toInt()
            else -> 0xFFE8EAED.toInt()
        }
    }

    private fun applySwitchTint(switch: SwitchCompat, themeData: ThemeData?) {
        val thumbColor = when {
            themeData != null && themeData.settingsSwitchThumbColor != 0 -> themeData.settingsSwitchThumbColor
            themeData?.lightTheme == true -> 0xFF1A73E8.toInt()
            else -> 0xFF8AB4F8.toInt()
        }
        val trackColor = when {
            themeData != null && themeData.settingsSwitchTrackColor != 0 -> themeData.settingsSwitchTrackColor
            themeData?.lightTheme == true -> 0x66000000
            else -> 0x66FFFFFF
        }

        switch.thumbTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                thumbColor,
                applyAlpha(thumbColor, 0.45f)
            )
        )
        switch.trackTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                trackColor,
                applyAlpha(trackColor, 0.38f)
            )
        )
    }

    private fun getPreferenceForItemView(recyclerView: RecyclerView, itemView: View): Preference? {
        val position = recyclerView.getChildAdapterPosition(itemView)
        if (position < 0) {
            return null
        }
        val adapter = recyclerView.adapter ?: return null
        return try {
            val method = adapter.javaClass.getDeclaredMethod("getItem", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(adapter, position) as? Preference
        } catch (_: Exception) {
            null
        }
    }

    private fun applyAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = minOf(255, maxOf(0, Math.round(Color.alpha(color) * alphaFactor)))
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private class SettingsDividerDrawable(context: Context, color: Int) : Drawable() {
        private val height = context.convertDpToPx(1)
        private val padding = context.convertDpToPx(4)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }

        override fun draw(canvas: Canvas) {
            val rect: Rect = bounds
            canvas.drawRect(
                rect.left.toFloat(),
                (rect.top + padding).toFloat(),
                rect.right.toFloat(),
                (rect.bottom - padding).toFloat(),
                paint
            )
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getIntrinsicHeight(): Int = height
    }
}
