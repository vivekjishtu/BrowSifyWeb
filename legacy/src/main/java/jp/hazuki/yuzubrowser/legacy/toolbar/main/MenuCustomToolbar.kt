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

package jp.hazuki.yuzubrowser.legacy.toolbar.main

import android.content.Context
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.LinearLayout
import jp.hazuki.yuzubrowser.core.utility.extensions.convertDpToPx
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.action.manager.ActionController
import jp.hazuki.yuzubrowser.legacy.action.manager.ActionIconManager
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import jp.hazuki.yuzubrowser.ui.theme.ThemeData

class MenuCustomToolbar(
    context: Context,
    controller: ActionController,
    iconManager: ActionIconManager,
    onActionRun: () -> Unit
) : CustomToolbarBase(context, AppPrefs.toolbar_custom1, controller, iconManager, AlwaysVisibleRequestCallback, onActionRun) {

    init {
        val buttonMargin = context.convertDpToPx(4)
        val linearLayout = findViewById<LinearLayout>(R.id.linearLayout)
        for (i in 0 until linearLayout.childCount) {
            val child = linearLayout.getChildAt(i)
            val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
            params.marginStart = buttonMargin
            params.marginEnd = buttonMargin
            child.layoutParams = params
        }
    }

    override fun applyTheme(themeData: ThemeData?) {
        val iconColor = themeData?.menuIconColor.orFallback(themeData?.toolbarImageColor)?.orFallback(0xFFE8EAED.toInt()) ?: 0xFFE8EAED.toInt()
        val normalColor = themeData?.menuToolbarButtonColor
            .orFallback(themeData?.menuItemPressedColor)
            .orFallback(themeData?.toolbarButtonBackgroundPress?.paint?.color)
            .orFallback(0xFF3C4043.toInt()) ?: 0xFF3C4043.toInt()
        val pressedColor = themeData?.menuToolbarButtonPressedColor
            .orFallback(themeData?.menuBorderColor)
            .orFallback(themeData?.menuDividerColor)
            .orFallback(themeData?.toolbarButtonBackgroundPress?.paint?.color)
            .orFallback(0xFF4B4C50.toInt()) ?: 0xFF4B4C50.toInt()

        mButtonController.setColorFilter(PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP))
        mButtonController.setBackgroundDrawable(createButtonBackground(normalColor, pressedColor))
    }

    private fun createButtonBackground(normalColor: Int, pressedColor: Int): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), createButtonShape(pressedColor))
            addState(intArrayOf(android.R.attr.state_focused), createButtonShape(pressedColor))
            addState(intArrayOf(), createButtonShape(normalColor))
        }
    }

    private fun createButtonShape(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun Int?.orFallback(fallback: Int?): Int? {
        return if (this != null && this != 0) this else fallback
    }

    private object AlwaysVisibleRequestCallback : RequestCallback {
        override fun shouldShowToolbar(
            visibility: jp.hazuki.yuzubrowser.ui.settings.container.ToolbarVisibilityContainer,
            tabData: jp.hazuki.yuzubrowser.legacy.tab.manager.MainTabData?,
            config: Configuration?
        ) = true
    }
}
