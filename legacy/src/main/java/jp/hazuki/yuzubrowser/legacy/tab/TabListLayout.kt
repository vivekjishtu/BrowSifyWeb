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

package jp.hazuki.yuzubrowser.legacy.tab

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.action.item.TabListSingleAction
import jp.hazuki.yuzubrowser.legacy.tab.adapter.TabListRecyclerAdapterFactory
import jp.hazuki.yuzubrowser.legacy.tab.adapter.TabListRecyclerBaseAdapter
import jp.hazuki.yuzubrowser.legacy.tab.manager.MainTabData
import jp.hazuki.yuzubrowser.legacy.tab.manager.TabManager
import jp.hazuki.yuzubrowser.legacy.utils.view.templatepreserving.TemplatePreservingSnackBar
import jp.hazuki.yuzubrowser.ui.widget.recycler.DividerItemDecoration
import jp.hazuki.yuzubrowser.ui.theme.ThemeData

class TabListLayout @SuppressLint("RtlHardcoded")
constructor(context: Context, attrs: AttributeSet?, mode: Int, left: Boolean, val lastTabMode: Int) : LinearLayout(context, attrs) {
    private lateinit var adapter: TabListRecyclerBaseAdapter
    private lateinit var tabManager: TabManager
    private lateinit var callback: Callback
    private var snackbar: TemplatePreservingSnackBar? = null
    private val bottomBar: LinearLayout
    private val reverse = mode == TabListSingleAction.MODE_REVERSE
    private val horizontal = mode == TabListSingleAction.MODE_HORIZONTAL

    private var removedTab: RemovedTab? = null

    constructor(context: Context, mode: Int, left: Boolean, lastTabMode: Int) : this(context, null, mode, left, lastTabMode)

    @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, mode: Int = TabListSingleAction.MODE_NORMAL, lastTabMode: Int = TabListSingleAction.LAST_TAB_MODE_NONE) : this(context, attrs, mode, false, lastTabMode)

    init {
        val mLayoutInflater = LayoutInflater.from(context)
        when {
            horizontal -> {
                mLayoutInflater.inflate(R.layout.tab_list_horizontal, this)
                setOnClickListener { close() }
            }
            reverse -> mLayoutInflater.inflate(R.layout.tab_list_reverse, this)
            else -> mLayoutInflater.inflate(R.layout.tab_list, this)
        }

        bottomBar = findViewById(R.id.bottomBar)

        if (left) {
            bottomBar.gravity = Gravity.LEFT
        }

        applyTheme()

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (reverse || horizontal) {
                updatePadding(left = insets.left, right = insets.right, bottom = insets.bottom)
                bottomBar.updatePadding(top = 0)
            } else {
                updatePadding(left = insets.left, right = insets.right, top = insets.top)
                bottomBar.updatePadding(bottom = 0)
            }
            windowInsets
        }
    }

    fun setTabManager(manager: TabManager) {
        tabManager = manager

        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)

        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)

        if (horizontal) {
            layoutManager.orientation = androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL
        } else if (reverse) {
            layoutManager.stackFromEnd = true
        }

        recyclerView.layoutManager = layoutManager

        val helper = ItemTouchHelper(ListTouch())
        helper.attachToRecyclerView(recyclerView)
        recyclerView.addItemDecoration(helper)

        if (!horizontal) {
            recyclerView.addItemDecoration(DividerItemDecoration(context.applicationContext))
        }

        adapter = TabListRecyclerAdapterFactory.create(context, tabManager, horizontal, object : TabListRecyclerBaseAdapter.OnRecyclerListener {
            override fun onRecyclerItemClicked(v: View, position: Int) {
                snackbar?.dismiss()

                callback.requestSelectTab(position)
                close()
            }

            override fun onCloseButtonClicked(v: View, position: Int) {
                snackbar?.dismiss()

                val size = tabManager.size()
                if (size == 1) {
                    closeLastTab()
                } else {
                    val current = position == tabManager.currentTabNo
                    callback.requestRemoveTab(position, true)
                    if (size != tabManager.size()) {
                        adapter.notifyItemRemoved(position)
                        if (current) {
                            adapter.notifyItemChanged(tabManager.currentTabNo)
                        }
                    }
                }
            }

            override fun onHistoryButtonClicked(v: View, position: Int) {
                snackbar?.dismiss()

                callback.requestShowTabHistory(position)
            }
        })
        recyclerView.adapter = adapter

        layoutManager.scrollToPosition(tabManager.currentTabNo)

        setOnClickListener { close() }

        findViewById<View>(R.id.newTabButton).setOnClickListener {
            callback.requestAddTab()
            close()
        }
    }

    private fun applyTheme() {
        val themeData = ThemeData.getInstance()
        val backgroundColor = when {
            themeData != null && themeData.tabListBackgroundColor != 0 -> themeData.tabListBackgroundColor
            themeData != null && themeData.toolbarBackgroundColor != 0 -> themeData.toolbarBackgroundColor
            themeData != null && themeData.menuBackgroundColor != 0 -> themeData.menuBackgroundColor
            else -> 0xFF222222.toInt()
        }
        val dividerColor = themeData?.tabListDividerColor?.takeIf { it != 0 } ?: themeData?.tabDividerColor ?: themeData?.menuDividerColor ?: 0x1FFFFFFF
        val buttonColor = themeData?.menuToolbarButtonColor ?: themeData?.tabAccentColor ?: themeData?.toolbarImageColor ?: 0xFFFFFFFF.toInt()
        val buttonIconColor = themeData?.toolbarTextColor ?: themeData?.toolbarImageColor ?: themeData?.menuTextColor ?: 0xFFFFFFFF.toInt()
        val buttonPressed = themeData?.menuToolbarButtonPressedColor ?: themeData?.menuItemPressedColor ?: 0x33FFFFFF

        setBackgroundColor(backgroundColor)
        bottomBar.setBackgroundColor(backgroundColor)

        findViewById<View>(R.id.divider)?.setBackgroundColor(dividerColor)

        findViewById<RecyclerView>(R.id.recyclerView).setBackgroundColor(backgroundColor)

        val newTabButton = findViewById<ImageButton>(R.id.newTabButton)
        newTabButton.background = createButtonBackground(buttonColor, buttonPressed)
        ImageViewCompat.setImageTintList(newTabButton, android.content.res.ColorStateList.valueOf(buttonIconColor))
    }

    private fun createButtonBackground(normalColor: Int, pressedColor: Int): android.graphics.drawable.Drawable {
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(pressedColor)
            })
            addState(intArrayOf(), android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(normalColor)
            })
        }
    }

    fun close() {
        snackbar?.dismiss()
        callback.requestTabListClose()
    }

    fun closeSnackBar() {
        snackbar?.run {
            if (isShown) dismiss()
        }
    }

    private fun closeLastTab() {
        when (lastTabMode) {
            TabListSingleAction.LAST_TAB_MODE_NEW_TAB -> {
                callback.requestCloseAllTab()
                postDelayed({ adapter.notifyDataSetChanged() }, 100)
            }
            TabListSingleAction.LAST_TAB_MODE_FINISH -> callback.requestFinish(false)
            TabListSingleAction.LAST_TAB_MODE_FINISH_WITH_ALERT -> callback.requestFinish(true)
        }
    }

    private inner class ListTouch : ItemTouchHelper.Callback() {

        override fun getMovementFlags(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder): Int {
            return if ((viewHolder as TabListRecyclerBaseAdapter.ViewHolder).indexData.isPinning) {
                if (horizontal) {
                    ItemTouchHelper.Callback.makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                } else {
                    ItemTouchHelper.Callback.makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, ItemTouchHelper.DOWN or ItemTouchHelper.UP)
                }
            } else {
                if (horizontal) {
                    ItemTouchHelper.Callback.makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.DOWN or ItemTouchHelper.UP) or ItemTouchHelper.Callback.makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                } else {
                    ItemTouchHelper.Callback.makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) or ItemTouchHelper.Callback.makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, ItemTouchHelper.DOWN or ItemTouchHelper.UP)
                }
            }
        }

        override fun onMove(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
            snackbar?.run {
                if (isShown) dismiss()
            }
            callback.requestMoveTab(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            adapter.notifyItemMoved(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
            if (adapter.itemCount > 1) {
                snackbar?.run {
                    if (isShown) dismiss()
                }
                val position = viewHolder.bindingAdapterPosition

                val current = position == tabManager.currentTabNo

                removedTab = RemovedTab(position, tabManager.get(position))

                callback.requestRemoveTab(position, false)

                adapter.notifyItemRemoved(position)

                if (current) {
                    adapter.notifyItemChanged(tabManager.currentTabNo)
                }
                snackbar = TemplatePreservingSnackBar.make(bottomBar, context.getString(R.string.closed_tab),
                        (viewHolder as TabListRecyclerBaseAdapter.ViewHolder).title, Snackbar.LENGTH_SHORT)
                        .setAction(R.string.undo, OnClickListener {
                            removedTab?.run {
                                callback.requestAddTab(index, data)
                                adapter.notifyItemInserted(index)
                                removedTab = null
                            }
                        })
                        .addCallback(object : BaseTransientBottomBar.BaseCallback<TemplatePreservingSnackBar>() {
                            override fun onDismissed(transientBottomBar: TemplatePreservingSnackBar?, event: Int) {
                                removedTab?.run {
                                    destroy()
                                    removedTab = null
                                }
                                snackbar = null
                            }
                        }).apply { show() }
            } else {
                adapter.notifyDataSetChanged()
                closeLastTab()
            }
        }

        override fun isItemViewSwipeEnabled(): Boolean {
            return adapter.itemCount > 1 || lastTabMode != 0
        }
    }

    interface Callback {
        fun requestTabListClose()

        fun requestMoveTab(positionFrom: Int, positionTo: Int)

        fun requestRemoveTab(no: Int, destroy: Boolean)

        fun requestAddTab()

        fun requestSelectTab(no: Int)

        fun requestShowTabHistory(no: Int)

        fun requestAddTab(index: Int, data: MainTabData)

        fun requestCloseAllTab()

        fun requestFinish(alert: Boolean)
    }

    fun setCallback(l: Callback) {
        callback = l
    }

    private class RemovedTab internal constructor(internal val index: Int, internal val data: MainTabData) {

        internal fun destroy() {
            data.mWebView.destroy()
        }
    }
}
