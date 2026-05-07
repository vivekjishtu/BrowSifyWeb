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
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import jp.hazuki.yuzubrowser.core.utility.extensions.convertDpToPx
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.action.SingleAction
import jp.hazuki.yuzubrowser.legacy.action.manager.ActionController
import jp.hazuki.yuzubrowser.legacy.action.manager.ActionIconManager
import jp.hazuki.yuzubrowser.legacy.action.manager.SoftButtonActionArrayManager
import jp.hazuki.yuzubrowser.legacy.action.manager.SoftButtonActionManager
import jp.hazuki.yuzubrowser.legacy.tab.manager.MainTabData
import jp.hazuki.yuzubrowser.legacy.toolbar.ButtonToolbarController
import jp.hazuki.yuzubrowser.legacy.utils.view.swipebutton.SwipeTextButton
import jp.hazuki.yuzubrowser.ui.extensions.decodePunyCodeUrl
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import jp.hazuki.yuzubrowser.ui.theme.ThemeData

abstract class UrlBarBase(context: Context, controller: ActionController, iconManager: ActionIconManager, layout: Int, request_callback: RequestCallback) : ToolbarBase(context, AppPrefs.toolbar_url, request_callback) {
    private val mLeftButtonController: ButtonToolbarController
    private val mRightButtonController: ButtonToolbarController
    private val actionController: ActionController = controller
    protected val centerUrlButton: SwipeTextButton

    init {
        LayoutInflater.from(context).inflate(layout, this)
        val toolbarSizeY = context.convertDpToPx(AppPrefs.toolbar_url.size.get())

        val softbtnManager = SoftButtonActionManager.getInstance(context)

        mLeftButtonController = ButtonToolbarController(findViewById(R.id.leftLinearLayout), controller, iconManager, toolbarSizeY)
        mRightButtonController = ButtonToolbarController(findViewById(R.id.rightLinearLayout), controller, iconManager, toolbarSizeY)

        centerUrlButton = findViewById(R.id.centerUrlButton)

        centerUrlButton.setActionData(softbtnManager.btn_url_center, controller, iconManager)
        ButtonToolbarController.settingButtonSize(centerUrlButton, toolbarSizeY)
        centerUrlButton.setOnTouchListener { v, event ->
            if (isStartDrawableHit(centerUrlButton, event)) {
                if (event.action == MotionEvent.ACTION_UP) {
                    actionController.run(SingleAction.makeInstance(SingleAction.PAGE_INFO), null, v)
                }
                return@setOnTouchListener true
            }
            false
        }

        addButtons()
    }

    override fun onPreferenceReset() {
        super.onPreferenceReset()
        addButtons()

        centerUrlButton.notifyChangeState()
        centerUrlButton.setSense(AppPrefs.swipebtn_sensitivity.get())
        centerUrlButton.textSize = AppPrefs.toolbar_text_size_url.get().toFloat()
    }

    override fun applyTheme(themeData: ThemeData?) {
        super.applyTheme(themeData)
        applyTheme(mLeftButtonController)
        applyTheme(mRightButtonController)
        applyUrlBoxTheme(themeData)
    }

    private fun addButtons() {
        val manager = SoftButtonActionArrayManager.getInstance(context)
        mLeftButtonController.addButtons(manager.btn_url_left.list)
        mRightButtonController.addButtons(manager.btn_url_right.list)
        onThemeChanged(ThemeData.getInstance())// TODO
    }

    override fun notifyChangeWebState(data: MainTabData?) {
        super.notifyChangeWebState(data)
        mLeftButtonController.notifyChangeState()
        mRightButtonController.notifyChangeState()
        centerUrlButton.notifyChangeState()

        if (data != null)
            changeTitle(data)
    }

    override fun resetToolBar() {
        mLeftButtonController.resetIcon()
        mRightButtonController.resetIcon()
    }

    fun changeTitle(data: MainTabData) {
        //need post Runnable?
        post {
            if (data.url != null && (data.url.startsWith("bsw:speeddial", ignoreCase = true) || data.url.startsWith("bsw:private", ignoreCase = true))) {
                centerUrlButton.run {
                    setTypeUrl(true)
                    text = context.getString(R.string.omnibox_placeholder)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setTextColor(getPlaceholderTextColor())
                    setStartIcon(R.drawable.ic_search_white_24dp, null, getPlaceholderTextColor())
                }
                return@post
            }

            if (!AppPrefs.toolbar_always_show_url.get() && data.title != null && !data.isInPageLoad) {
                centerUrlButton.run {
                    setTypeUrl(false)
                    text = data.title
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
                    setTextColor(ContextCompat.getColor(context, R.color.tab_text_color_selected))
                    setCompoundDrawablesRelative(null, null, null, null)
                }
            } else {
                centerUrlButton.run {
                    setTypeUrl(true)
                    text = data.url.decodePunyCodeUrl()
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setTextColor(ContextCompat.getColor(context, R.color.tab_text_color_selected))
                    updateSecurityIcon(data.url)
                }
            }
        }
    }

    private fun updateSecurityIcon(url: String?) {
        val iconRes = when {
            url == null -> null
            url.startsWith("https://", ignoreCase = true) -> R.drawable.ic_lock_outline_white_24px
            url.startsWith("http://", ignoreCase = true) -> R.drawable.ic_lock_open_white_24px
            else -> null
        }

        if (iconRes == null) {
            centerUrlButton.setCompoundDrawablesRelative(null, null, null, null)
            return
        }

        setStartIcon(iconRes, null, centerUrlButton.currentTextColor)
    }

    private fun setStartIcon(drawableRes: Int, tintColorRes: Int?, explicitTint: Int? = null) {
        val sizePx = context.convertDpToPx(16)
        val drawable = ContextCompat.getDrawable(context, drawableRes)?.mutate()
        if (drawable != null) {
            val tintColor = explicitTint ?: tintColorRes?.let { ContextCompat.getColor(context, it) } ?: centerUrlButton.currentTextColor
            DrawableCompat.setTint(drawable, tintColor)
            drawable.setBounds(0, 0, sizePx, sizePx)
        }

        centerUrlButton.compoundDrawablePadding = context.convertDpToPx(6)
        centerUrlButton.setCompoundDrawablesRelative(drawable, null, null, null)
    }

    private fun isStartDrawableHit(view: SwipeTextButton, event: MotionEvent): Boolean {
        val drawable = view.compoundDrawablesRelative[0] ?: return false
        val drawableWidth = drawable.bounds.width()
        if (drawableWidth == 0) return false

        val paddingStart = view.paddingStart
        val paddingEnd = view.paddingEnd
        val drawablePadding = view.compoundDrawablePadding
        val x = event.x

        return if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            val start = view.width - paddingEnd - drawableWidth
            val end = view.width - paddingEnd + drawablePadding
            x >= start && x <= end
        } else {
            val start = paddingStart
            val end = paddingStart + drawableWidth + drawablePadding
            x >= start && x <= end
        }
    }

    private fun applyUrlBoxTheme(themeData: ThemeData?) {
        if (!AppPrefs.toolbar_url_box.get()) return

        val themedDrawable = themeData?.urlBarBackgroundDrawable
        if (themedDrawable != null) {
            centerUrlButton.background = themedDrawable
            return
        }

        val background = centerUrlButton.background?.mutate() ?: return
        val fillColor = themeData?.urlBarBackgroundColor ?: 0
        val borderColor = themeData?.urlBarBorderColor ?: 0
        val strokeFallback = themeData?.toolbarTextColor ?: 0

        if (background is LayerDrawable) {
            val mainShape = background.getDrawable(1) as? GradientDrawable
            if (mainShape != null) {
                if (fillColor != 0) {
                    mainShape.setColor(fillColor)
                }
                if (borderColor != 0) {
                    mainShape.setStroke(context.convertDpToPx(1), borderColor)
                } else if (strokeFallback != 0) {
                    mainShape.setStroke(context.convertDpToPx(1), strokeFallback and 0x55FFFFFF)
                }
            }
            centerUrlButton.background = background
        }
    }

    private fun getPlaceholderTextColor(): Int {
        val theme = ThemeData.getInstance()
        val textColor = theme?.toolbarTextColor ?: 0
        return if (textColor != 0) {
            val alpha = if (theme?.lightTheme == true) 0x88 else 0xAA
            (textColor and 0x00FFFFFF) or (alpha shl 24)
        } else {
            ContextCompat.getColor(context, R.color.omnibox_placeholder_color)
        }
    }

}
