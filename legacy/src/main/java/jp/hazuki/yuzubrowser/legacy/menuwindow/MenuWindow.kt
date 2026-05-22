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

package jp.hazuki.yuzubrowser.legacy.menuwindow

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import jp.hazuki.yuzubrowser.core.utility.extensions.convertDpToFloatPx
import jp.hazuki.yuzubrowser.core.utility.extensions.convertDpToPx
import jp.hazuki.yuzubrowser.core.utility.utils.FontUtils
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.action.Action
import jp.hazuki.yuzubrowser.legacy.action.ActionList
import jp.hazuki.yuzubrowser.legacy.action.ActionNameArray
import jp.hazuki.yuzubrowser.legacy.action.manager.ActionController
import jp.hazuki.yuzubrowser.legacy.action.manager.ActionIconManager
import jp.hazuki.yuzubrowser.legacy.action.manager.SoftButtonActionFile
import jp.hazuki.yuzubrowser.legacy.action.manager.ToolbarActionManager
import jp.hazuki.yuzubrowser.legacy.toolbar.main.MenuCustomToolbar
import jp.hazuki.yuzubrowser.ui.app.ThemeActivity
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import jp.hazuki.yuzubrowser.ui.settings.PreferenceConstants
import jp.hazuki.yuzubrowser.ui.theme.ThemeData

typealias OnMenuCloseListener = () -> Unit

class MenuWindow(context: ThemeActivity, actionList: ActionList, controller: ActionController, private val iconManager: ActionIconManager) : PopupWindow.OnDismissListener {

    private val windowMargin = context.convertDpToPx(4)
    private val window = PopupWindow(context)
    private val handler = Handler(Looper.getMainLooper())
    private val visibleFrame = Rect()
    private val anchorLocation = IntArray(2)
    private var locking = false
    private var mListener: OnMenuCloseListener? = null
    private var menuCustomToolbar: MenuCustomToolbar? = null

    init {
        val inflater = LayoutInflater.from(context)
        val v = inflater.inflate(R.layout.drop_down_list, null, false) as ViewGroup
        val customToolbarContainer = v.findViewById<LinearLayout>(R.id.customToolbarContainer)
        val customToolbarDivider = v.findViewById<View>(R.id.customToolbarDivider)
        val layout = v.findViewById<LinearLayout>(R.id.items)
        val array = ActionNameArray(context)

        window.contentView = v
        window.isOutsideTouchable = true
        window.height = LinearLayout.LayoutParams.WRAP_CONTENT
        window.width = LinearLayout.LayoutParams.WRAP_CONTENT
        window.setBackgroundDrawable(context.getDrawable(R.drawable.menu_drop_down_background))
        window.elevation = context.convertDpToFloatPx(10)
        window.setOnDismissListener(this)
        window.contentView.isFocusableInTouchMode = true
        window.contentView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_MENU && event.repeatCount == 0 && event.action == KeyEvent.ACTION_DOWN) {
                dismiss()
                return@setOnKeyListener true
            }
            false
        }

        val fontSize = FontUtils.getTextSize(AppPrefs.font_size.menu.get())
        val customToolbarActions = ToolbarActionManager.getInstance(context).custombar1.list
        if (shouldShowCustomToolbar(customToolbarActions)) {
            val toolbar = MenuCustomToolbar(context, controller, iconManager) {
                window.dismiss()
            }.apply {
                onThemeChanged(ThemeData.getInstance())
            }
            menuCustomToolbar = toolbar
            customToolbarContainer.visibility = View.VISIBLE
            customToolbarDivider.visibility = View.VISIBLE
            customToolbarContainer.addView(
                toolbar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.convertDpToPx(AppPrefs.toolbar_custom1.size.get())
                )
            )
        }

        var lastGroup = Int.MIN_VALUE
        for (action in actionList) {
            val group = getMenuGroup(action)
            if (lastGroup != Int.MIN_VALUE && group != lastGroup) {
                layout.addView(createDivider(v.context))
            }
            lastGroup = group
            val child = inflater.inflate(R.layout.menu_list_item, v, false)
            val icon = child.findViewById<ImageView>(R.id.iconImageView)
            val name = child.findViewById<TextView>(R.id.actionNameTextView)
            if (fontSize >= 0) {
                name.textSize = fontSize.toFloat()
            }
            child.setOnClickListener {
                controller.run(action)
                window.dismiss()
            }
            if (AppPrefs.menu_icon.get()) {
                icon.setImageDrawable(iconManager[action])
            } else {
                icon.visibility = View.GONE
            }
            name.text = action.toString(array)
            layout.addView(child)
        }
        if (AppPrefs.fullscreen.get()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.contentView.windowInsetsController?.apply {
                    hide(
                        WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                    )
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }
        }
    }

    fun show(root: View, gravity: Int) {
        if (!locking) {
            refreshCustomToolbar()

            //This is a magic!
            window.isFocusable = false

            window.showAtLocation(root, gravity, if (gravity and 0x110 != 0) windowMargin else 0, 0)

            //Reset focusable
            window.isFocusable = true
        }
    }

    fun showAsDropDown(anchor: View) {
        if (!locking) {
            refreshCustomToolbar()

            //This is a magic!
            window.isFocusable = false

            showAnchored(anchor)

            //Reset focusable
            window.isFocusable = true
        }
    }

    private fun showAnchored(anchor: View) {
        val popupWidth = measurePopupWidth(anchor)
        val popupHeight = measurePopupHeight(anchor)

        anchor.getWindowVisibleDisplayFrame(visibleFrame)
        anchor.getLocationOnScreen(anchorLocation)

        val anchorLeft = anchorLocation[0]
        val anchorTop = anchorLocation[1]
        val anchorRight = anchorLeft + anchor.width
        val anchorBottom = anchorTop + anchor.height

        val spaceAbove = anchorTop - visibleFrame.top
        val spaceBelow = visibleFrame.bottom - anchorBottom
        val showBelow = spaceBelow >= popupHeight || spaceBelow >= spaceAbove

        val minX = visibleFrame.left + windowMargin
        val maxX = (visibleFrame.right - popupWidth - windowMargin).coerceAtLeast(minX)
        val anchorAlignedX = if (anchor.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            anchorLeft
        } else {
            anchorRight - popupWidth
        }
        val popupX = anchorAlignedX.coerceIn(minX, maxX)

        val desiredY = if (showBelow) {
            anchorBottom
        } else {
            anchorTop - popupHeight
        }
        val minY = visibleFrame.top + windowMargin
        val maxY = (visibleFrame.bottom - popupHeight - windowMargin).coerceAtLeast(minY)
        val popupY = desiredY.coerceIn(minY, maxY)

        window.animationStyle = if (showBelow) {
            R.style.AnimationMenuWindowAnchoredBelow
        } else {
            R.style.AnimationMenuWindowAnchoredAbove
        }
        window.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY)
    }

    private fun measurePopupWidth(anchor: View): Int {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(
            (anchor.resources.displayMetrics.widthPixels - windowMargin * 2).coerceAtLeast(0),
            View.MeasureSpec.AT_MOST
        )
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        window.contentView.measure(widthSpec, heightSpec)
        return window.contentView.measuredWidth
    }

    private fun measurePopupHeight(anchor: View): Int {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(
            (anchor.resources.displayMetrics.widthPixels - windowMargin * 2).coerceAtLeast(0),
            View.MeasureSpec.AT_MOST
        )
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        window.contentView.measure(widthSpec, heightSpec)
        return window.contentView.measuredHeight
    }

    private fun getMenuGroup(action: Action): Int {
        if (action.isEmpty()) return 999
        val id = action[0].id
        return when {
            id in 1000..1999 -> 0 // navigation
            id in 5000..5999 -> 1 // page info / page actions
            id in 10000..10999 -> 2 // tab actions
            id in 35000..35999 -> 3 // app screens
            id in 35300..35399 -> 4 // settings/tools
            else -> 5
        }
    }

    private fun shouldShowCustomToolbar(actions: List<SoftButtonActionFile>): Boolean {
        return AppPrefs.toolbar_custom1_placement.get() == PreferenceConstants.CUSTOM_TOOLBAR_PLACEMENT_MAIN_MENU &&
            actions.any { it.hasAnyAction() }
    }

    private fun SoftButtonActionFile.hasAnyAction(): Boolean {
        return !press.isEmpty() ||
            !lpress.isEmpty() ||
            !up.isEmpty() ||
            !down.isEmpty() ||
            !left.isEmpty() ||
            !right.isEmpty()
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            background = context.getDrawable(R.drawable.menu_divider)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.convertDpToPx(1)
            ).apply {
                marginStart = context.convertDpToPx(16)
                marginEnd = context.convertDpToPx(16)
                topMargin = context.convertDpToPx(6)
                bottomMargin = context.convertDpToPx(6)
            }
        }
    }

    fun setSystemUiVisibility(flags: Int) {
        @Suppress("DEPRECATION")
        window.contentView.systemUiVisibility = flags
    }

    private fun refreshCustomToolbar() {
        menuCustomToolbar?.notifyChangeWebState(iconManager.info.currentTabData)
    }

    val isShowing: Boolean
        get() = window.isShowing

    fun dismiss() {
        window.dismiss()
    }

    fun setListener(listener: OnMenuCloseListener) {
        mListener = listener
    }

    override fun onDismiss() {
        locking = true
        handler.postDelayed(lock, 50)

        mListener?.invoke()
    }

    private val lock = { locking = false }
}
