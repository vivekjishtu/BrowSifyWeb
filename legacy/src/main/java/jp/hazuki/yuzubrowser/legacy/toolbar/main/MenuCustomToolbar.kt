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
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
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
        mButtonController.setColorFilter(PorterDuffColorFilter(0xFFE8EAED.toInt(), PorterDuff.Mode.SRC_ATOP))
        mButtonController.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.menu_toolbar_button_background))
    }

    private object AlwaysVisibleRequestCallback : RequestCallback {
        override fun shouldShowToolbar(
            visibility: jp.hazuki.yuzubrowser.ui.settings.container.ToolbarVisibilityContainer,
            tabData: jp.hazuki.yuzubrowser.legacy.tab.manager.MainTabData?,
            config: Configuration?
        ) = true
    }
}
