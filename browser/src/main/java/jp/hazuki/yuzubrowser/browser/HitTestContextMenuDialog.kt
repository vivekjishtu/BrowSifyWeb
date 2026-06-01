package jp.hazuki.yuzubrowser.browser

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.action.Action
import jp.hazuki.yuzubrowser.legacy.action.ActionList
import jp.hazuki.yuzubrowser.legacy.action.SingleAction
import jp.hazuki.yuzubrowser.legacy.action.manager.ActionController
import jp.hazuki.yuzubrowser.legacy.browser.BrowserController
import jp.hazuki.yuzubrowser.legacy.webkit.TabType
import jp.hazuki.yuzubrowser.legacy.webkit.handler.WebSrcOpenPrivateTabHandler
import jp.hazuki.yuzubrowser.ui.theme.ThemeData

internal class HitTestContextMenuDialog(
    private val controller: BrowserController,
    private val target: ActionController.HitTestResultTargetInfo,
    private val actionList: ActionList,
    private val onActionClick: (Action) -> Unit
) {
    private val menuVariant = resolveMenuVariant()
    private val activity = controller.activity
    private val themeData = ThemeData.getInstance()
    private val menuBackgroundColor = themeData?.menuBackgroundColor.orFallback(themeData?.toolbarBackgroundColor ?: 0xFF303134.toInt())
    private val menuBorderColor = themeData?.menuBorderColor.orFallback(themeData?.menuDividerColor ?: adjustColor(menuBackgroundColor, if (themeData?.lightTheme == true) 0.78f else 1.22f))
    private val menuTextColor = themeData?.menuTextColor.orFallback(themeData?.toolbarTextColor ?: 0xFFE8EAED.toInt())
    private val menuIconColor = themeData?.menuIconColor.orFallback(themeData?.toolbarImageColor ?: menuTextColor)
    private val menuDividerColor = themeData?.menuDividerColor.orFallback(adjustColor(menuBackgroundColor, if (themeData?.lightTheme == true) 0.82f else 1.18f))
    private val menuItemPressedColor = themeData?.menuItemPressedColor.orFallback(adjustColor(menuBackgroundColor, if (themeData?.lightTheme == true) 0.94f else 1.10f))
    private val cornerRadius = activity.resources.displayMetrics.density * 18f
    private val sourceTabType = controller.getTabOrNull(target.webView)?.tabType ?: TabType.WINDOW

    fun show() {
        val sections = splitActions(augmentActions(actionList), menuVariant)
        if (sections.primary.isEmpty() && sections.advanced.isNotEmpty()) {
            showDialog(sections.advanced, emptyList(), true)
        } else {
            showDialog(sections.primary, sections.advanced, false)
        }
    }

    private fun augmentActions(actions: ActionList): ActionList {
        val copy = ActionList()
        copy.addAll(actions)
        if (sourceTabType != TabType.PRIVATE &&
            (menuVariant == HitTestMenuVariant.LINK || menuVariant == HitTestMenuVariant.IMAGE || menuVariant == HitTestMenuVariant.LINKED_IMAGE)
        ) {
            val hasPrivateTab = copy.any { !it.isEmpty() && it[0].id == SingleAction.PRIVATE }
            if (!hasPrivateTab) {
                copy.add(SingleAction.makeInstance(SingleAction.PRIVATE))
            }
        }
        return copy
    }

    private fun showDialog(primaryActions: List<Action>, advancedActions: List<Action>, advancedMode: Boolean) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_hit_test_context_menu, null, false)
        val root = view.findViewById<View>(R.id.rootContainer)
        val titleView = view.findViewById<TextView>(R.id.titleTextView)
        val subtitleView = view.findViewById<TextView>(R.id.subtitleTextView)
        val divider = view.findViewById<View>(R.id.headerDivider)
        val itemsContainer = view.findViewById<LinearLayout>(R.id.itemsContainer)
        val scrollView = view.findViewById<ScrollView>(R.id.itemsScrollView)

        root.background = createSurfaceBackground()
        divider.background = createDividerDrawable()
        val header = buildHeader(advancedMode)
        if (header.title.isNullOrBlank()) {
            titleView.visibility = View.GONE
        } else {
            titleView.visibility = View.VISIBLE
            titleView.text = header.title
        }
        if (header.subtitle.isNullOrBlank()) {
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.visibility = View.VISIBLE
            subtitleView.text = header.subtitle
        }
        divider.visibility = if (header.title.isNullOrBlank() && header.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        styleHeader(titleView, subtitleView)

        var dialog: AlertDialog? = null
        val allRows = sortActions(primaryActions, advancedMode)
        val hasAdvancedShortcut = !advancedMode && advancedActions.isNotEmpty()

        if (advancedMode) {
            addAdvancedSections(itemsContainer, allRows) { action ->
                handleActionClick(action, dialog)
            }
        } else {
            allRows.forEachIndexed { index, action ->
                val previous = allRows.getOrNull(index - 1)
                val showDivider = previous != null && groupForAction(previous) != groupForAction(action)
                itemsContainer.addView(createActionRow(action, showDivider) {
                    handleActionClick(action, dialog)
                })
            }

            if (hasAdvancedShortcut) {
                itemsContainer.addView(createAdvancedRow(sortActions(advancedActions, true), allRows.isNotEmpty()) {
                    dialog?.dismiss()
                    showDialog(advancedActions, emptyList(), true)
                })
            }
        }

        dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        view.post {
            val density = activity.resources.displayMetrics.density
            val screenHeight = activity.resources.displayMetrics.heightPixels
            val maxHeight = (screenHeight - (density * 32f).toInt()).coerceAtLeast((density * 160f).toInt())
            val headerHeight = (root.height - scrollView.height).coerceAtLeast(0)
            val availableForList = (maxHeight - headerHeight).coerceAtLeast((density * 160f).toInt())
            val contentHeight = itemsContainer.height
            scrollView.layoutParams = scrollView.layoutParams.apply {
                height = minOf(contentHeight, availableForList)
            }
            scrollView.isFillViewport = contentHeight < availableForList
            scrollView.requestLayout()
            root.requestLayout()
        }
    }

    private fun handleActionClick(action: Action, dialog: AlertDialog?) {
        if (!action.isEmpty() && action[0].id == SingleAction.PRIVATE) {
            dialog?.dismiss()
            when (menuVariant) {
                HitTestMenuVariant.LINKED_IMAGE -> {
                    target.webView.requestFocusNodeHref(WebSrcOpenPrivateTabHandler(controller).obtainMessage())
                }
                else -> {
                    val url = target.result.extra ?: return
                    controller.openInNewTab(url, TabType.PRIVATE)
                }
            }
            return
        }
        dialog?.dismiss()
        onActionClick(action)
    }

    private fun createActionRow(action: Action, showDividerAbove: Boolean, onClick: () -> Unit): View {
        val row = LayoutInflater.from(activity).inflate(R.layout.item_hit_test_context_menu, null, false)
        val iconView = row.findViewById<ImageView>(R.id.iconImageView)
        val titleView = row.findViewById<TextView>(R.id.titleTextView)
        val chevronView = row.findViewById<ImageView>(R.id.chevronImageView)
        val dividerView = row.findViewById<View>(R.id.dividerView)
        val clickable = row.findViewById<View>(R.id.rowClickable)

        titleView.text = labelForAction(action)
        titleView.setTextColor(menuTextColor)
        clickable.background = createRowBackground()
        dividerView.background = createDividerDrawable()
        dividerView.visibility = if (showDividerAbove) View.VISIBLE else View.GONE
        chevronView.visibility = View.GONE

        val iconRes = iconResForAction(action)
        if (iconRes != null) {
            iconView.visibility = View.VISIBLE
            iconView.setImageResource(iconRes)
            iconView.imageTintList = ColorStateList.valueOf(menuIconColor)
        } else {
            iconView.visibility = View.GONE
        }

        clickable.setOnClickListener { onClick() }

        return row
    }

    private fun createAdvancedRow(advancedActions: List<Action>, showDividerAbove: Boolean, onClick: () -> Unit): View {
        val row = LayoutInflater.from(activity).inflate(R.layout.item_hit_test_context_menu, null, false)
        val iconView = row.findViewById<ImageView>(R.id.iconImageView)
        val titleView = row.findViewById<TextView>(R.id.titleTextView)
        val chevronView = row.findViewById<ImageView>(R.id.chevronImageView)
        val dividerView = row.findViewById<View>(R.id.dividerView)
        val clickable = row.findViewById<View>(R.id.rowClickable)

        titleView.text = activity.getString(R.string.context_menu_advanced_actions)
        titleView.setTextColor(menuTextColor)
        clickable.background = createRowBackground()
        dividerView.visibility = if (showDividerAbove) View.VISIBLE else View.GONE
        dividerView.background = createDividerDrawable()

        iconView.visibility = View.VISIBLE
        iconView.setImageResource(R.drawable.ic_settings_white_24dp)
        iconView.imageTintList = ColorStateList.valueOf(menuIconColor)

        chevronView.visibility = View.VISIBLE
        chevronView.setImageResource(R.drawable.ic_arrow_forward_white_24dp)
        chevronView.imageTintList = ColorStateList.valueOf(adjustAlpha(menuIconColor, 0.86f))

        clickable.setOnClickListener { onClick() }

        return row
    }

    private fun addAdvancedSections(
        container: LinearLayout,
        actions: List<Action>,
        onActionClick: (Action) -> Unit
    ) {
        val buckets = linkedMapOf(
            AdvancedSection.OPEN_MORE to ArrayList<Action>(),
            AdvancedSection.SHARE_COPY to ArrayList<Action>(),
            AdvancedSection.DOWNLOAD to ArrayList<Action>(),
            AdvancedSection.SAFETY to ArrayList<Action>()
        )
        for (action in actions) {
            buckets[advancedSectionForAction(action)]?.add(action)
        }

        var firstSection = true
        buckets.forEach { (section, bucketActions) ->
            if (bucketActions.isEmpty()) return@forEach
            if (!firstSection) {
                addSectionSpacer(container)
            }
            firstSection = false
            container.addView(createSectionHeader(activity.getString(section.title)))
            bucketActions.forEachIndexed { index, action ->
                val previous = bucketActions.getOrNull(index - 1)
                val showDivider = previous != null && groupForAction(previous) != groupForAction(action)
                container.addView(createActionRow(action, showDivider) {
                    onActionClick(action)
                })
            }
        }
    }

    private fun addSectionSpacer(container: LinearLayout) {
        val spacer = View(activity)
        spacer.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (activity.resources.displayMetrics.density * 6f).toInt()
        )
        container.addView(spacer)
    }

    private fun createSectionHeader(title: String): View {
        return TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (activity.resources.displayMetrics.density * 6f).toInt()
                bottomMargin = (activity.resources.displayMetrics.density * 4f).toInt()
            }
            text = title.uppercase()
            setTextColor(adjustAlpha(menuTextColor, 0.58f))
            textSize = 11f
            letterSpacing = 0.08f
            setPadding(
                (activity.resources.displayMetrics.density * 10f).toInt(),
                (activity.resources.displayMetrics.density * 2f).toInt(),
                (activity.resources.displayMetrics.density * 10f).toInt(),
                (activity.resources.displayMetrics.density * 2f).toInt()
            )
        }
    }

    private fun styleHeader(titleView: TextView, subtitleView: TextView) {
        titleView.setTextColor(adjustAlpha(menuTextColor, 0.92f))
        subtitleView.setTextColor(adjustAlpha(menuTextColor, 0.68f))
    }

    private fun buildHeader(advancedMode: Boolean): HeaderInfo {
        return if (advancedMode) {
            HeaderInfo(
                title = activity.getString(R.string.context_menu_advanced_actions),
                subtitle = null
            )
        } else {
            HeaderInfo(
                title = null,
                subtitle = null
            )
        }
    }

    private fun splitActions(actions: ActionList, variant: HitTestMenuVariant): MenuSections {
        val primary = ArrayList<Action>()
        val advanced = ArrayList<Action>()
        for (action in actions) {
            if (isAdvancedAction(action, variant)) {
                advanced.add(action)
            } else {
                primary.add(action)
            }
        }
        return MenuSections(primary, advanced)
    }

    private fun isAdvancedAction(action: Action, variant: HitTestMenuVariant): Boolean {
        if (action.isEmpty()) return false
        return action[0].id in advancedActionIds(variant)
    }

    private fun sortActions(actions: List<Action>, advancedMode: Boolean): List<Action> {
        val indexed = actions.withIndex().sortedWith(compareBy({ actionPriority(advancedMode, it.value) }, { it.index }))
        return indexed.map { it.value }
    }

    private fun iconResForAction(action: Action): Int? {
        if (action.isEmpty()) return null
        return when (action[0].id) {
            SingleAction.LPRESS_OPEN,
            SingleAction.LPRESS_OPEN_OTHERS,
            SingleAction.LPRESS_OPEN_IMAGE,
            SingleAction.LPRESS_OPEN_IMAGE_OTHERS -> R.drawable.ic_arrow_forward_white_24dp
            SingleAction.LPRESS_OPEN_NEW,
            SingleAction.PRIVATE,
            SingleAction.LPRESS_OPEN_IMAGE_NEW,
            SingleAction.LPRESS_OPEN_NEW_RIGHT,
            SingleAction.LPRESS_OPEN_IMAGE_NEW_RIGHT -> R.drawable.ic_link_white_24dp
            SingleAction.LPRESS_OPEN_BG,
            SingleAction.LPRESS_OPEN_IMAGE_BG,
            SingleAction.LPRESS_OPEN_BG_RIGHT,
            SingleAction.LPRESS_OPEN_IMAGE_BG_RIGHT -> R.drawable.ic_link_white_24dp
            SingleAction.LPRESS_SHARE,
            SingleAction.LPRESS_SHARE_IMAGE,
            SingleAction.LPRESS_SHARE_IMAGE_URL -> R.drawable.ic_share_white_24dp
            SingleAction.LPRESS_COPY_URL,
            SingleAction.LPRESS_COPY_IMAGE_URL,
            SingleAction.LPRESS_COPY_LINK_TEXT -> R.drawable.ic_content_copy_white_24dp
            SingleAction.LPRESS_SAVE_PAGE,
            SingleAction.LPRESS_SAVE_PAGE_AS,
            SingleAction.LPRESS_SAVE_IMAGE,
            SingleAction.LPRESS_SAVE_IMAGE_AS -> R.drawable.ic_save_white_24dp
            SingleAction.LPRESS_GOOGLE_IMAGE_SEARCH -> R.drawable.ic_link_white_24dp
            SingleAction.LPRESS_PATTERN_MATCH -> R.drawable.ic_pattern_add_white_24dp
            SingleAction.LPRESS_ADD_BLACK_LIST,
            SingleAction.LPRESS_ADD_IMAGE_BLACK_LIST -> R.drawable.ic_open_ad_block_black_24dp
            SingleAction.LPRESS_ADD_WHITE_LIST,
            SingleAction.LPRESS_ADD_IMAGE_WHITE_LIST -> R.drawable.ic_open_ad_block_white_24dp
            SingleAction.LPRESS_IMAGE_RES_BLOCK -> R.drawable.ic_open_ad_block_white_page_24dp
            else -> null
        }
    }

    private fun labelForAction(action: Action): String {
        if (action.isEmpty()) return ""
        return when (action[0].id) {
            SingleAction.LPRESS_OPEN -> activity.getString(R.string.context_menu_open_here)
            SingleAction.LPRESS_OPEN_NEW -> activity.getString(R.string.context_menu_open_new_tab)
            SingleAction.PRIVATE -> activity.getString(R.string.open_link_in_private_tab)
            SingleAction.LPRESS_OPEN_BG -> activity.getString(R.string.context_menu_open_background)
            SingleAction.LPRESS_OPEN_NEW_RIGHT -> activity.getString(R.string.context_menu_open_new_tab_right)
            SingleAction.LPRESS_OPEN_BG_RIGHT -> activity.getString(R.string.context_menu_open_background_right)
            SingleAction.LPRESS_SHARE -> activity.getString(R.string.context_menu_share_link)
            SingleAction.LPRESS_OPEN_OTHERS -> activity.getString(R.string.context_menu_open_other_app)
            SingleAction.LPRESS_COPY_URL -> activity.getString(R.string.context_menu_copy_link)
            SingleAction.LPRESS_COPY_LINK_TEXT -> activity.getString(R.string.context_menu_copy_text)
            SingleAction.LPRESS_SAVE_PAGE -> activity.getString(R.string.context_menu_download_link)
            SingleAction.LPRESS_SAVE_PAGE_AS -> activity.getString(R.string.context_menu_download_link_as)
            SingleAction.LPRESS_OPEN_IMAGE -> activity.getString(R.string.context_menu_open_image)
            SingleAction.LPRESS_OPEN_IMAGE_NEW -> activity.getString(R.string.context_menu_open_image_new_tab)
            SingleAction.LPRESS_OPEN_IMAGE_BG -> activity.getString(R.string.context_menu_open_image_background)
            SingleAction.LPRESS_OPEN_IMAGE_NEW_RIGHT -> activity.getString(R.string.context_menu_open_image_new_tab_right)
            SingleAction.LPRESS_OPEN_IMAGE_BG_RIGHT -> activity.getString(R.string.context_menu_open_image_background_right)
            SingleAction.LPRESS_SHARE_IMAGE -> activity.getString(R.string.context_menu_share_image)
            SingleAction.LPRESS_SHARE_IMAGE_URL -> activity.getString(R.string.context_menu_share_image_link)
            SingleAction.LPRESS_OPEN_IMAGE_OTHERS -> activity.getString(R.string.context_menu_open_image_other_app)
            SingleAction.LPRESS_COPY_IMAGE_URL -> activity.getString(R.string.context_menu_copy_image_link)
            SingleAction.LPRESS_SAVE_IMAGE -> activity.getString(R.string.context_menu_download_image)
            SingleAction.LPRESS_SAVE_IMAGE_AS -> activity.getString(R.string.context_menu_download_image_as)
            SingleAction.LPRESS_GOOGLE_IMAGE_SEARCH -> activity.getString(R.string.context_menu_search_image)
            else -> action.toString(target.actionNameArray).orEmpty()
        }
    }

    private fun groupForAction(action: Action): Int {
        if (action.isEmpty()) return 99
        return when (menuVariant) {
            HitTestMenuVariant.LINK -> when (action[0].id) {
                SingleAction.LPRESS_OPEN,
                SingleAction.LPRESS_OPEN_NEW,
                SingleAction.PRIVATE -> 0

                SingleAction.LPRESS_COPY_URL,
                SingleAction.LPRESS_COPY_LINK_TEXT,
                SingleAction.LPRESS_SHARE -> 1

                SingleAction.LPRESS_SAVE_PAGE -> 2

                else -> 3
            }

            HitTestMenuVariant.IMAGE -> when (action[0].id) {
                SingleAction.LPRESS_OPEN_IMAGE,
                SingleAction.LPRESS_OPEN_IMAGE_NEW,
                SingleAction.PRIVATE -> 0

                SingleAction.LPRESS_COPY_IMAGE_URL,
                SingleAction.LPRESS_SHARE_IMAGE -> 1

                SingleAction.LPRESS_SAVE_IMAGE,
                SingleAction.LPRESS_GOOGLE_IMAGE_SEARCH -> 2

                else -> 3
            }

            HitTestMenuVariant.LINKED_IMAGE -> when (action[0].id) {
                SingleAction.LPRESS_OPEN,
                SingleAction.LPRESS_OPEN_NEW,
                SingleAction.PRIVATE -> 0

                SingleAction.LPRESS_OPEN_IMAGE,
                SingleAction.LPRESS_OPEN_IMAGE_NEW -> 1

                SingleAction.LPRESS_COPY_IMAGE_URL,
                SingleAction.LPRESS_COPY_URL,
                SingleAction.LPRESS_COPY_LINK_TEXT,
                SingleAction.LPRESS_SHARE_IMAGE,
                SingleAction.LPRESS_SHARE -> 2

                SingleAction.LPRESS_SAVE_IMAGE,
                SingleAction.LPRESS_SAVE_PAGE,
                SingleAction.LPRESS_GOOGLE_IMAGE_SEARCH -> 3

                else -> 3
            }
        }
    }

    private fun createSurfaceBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = this@HitTestContextMenuDialog.cornerRadius
            setColor(menuBackgroundColor)
            setStroke(1, menuBorderColor)
        }
    }

    private fun createRowBackground(): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), createRowShape(menuItemPressedColor))
            addState(intArrayOf(android.R.attr.state_focused), createRowShape(menuItemPressedColor))
            addState(intArrayOf(), createRowShape(Color.TRANSPARENT))
        }
    }

    private fun createRowShape(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
        }
    }

    private fun createDividerDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(menuDividerColor)
        }
    }

    private fun adjustAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = (Color.alpha(color) * alphaFactor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun adjustColor(color: Int, factor: Float): Int {
        val alpha = Color.alpha(color)
        val red = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val green = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val blue = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, red, green, blue)
    }

    private fun Int?.orFallback(fallback: Int): Int {
        return if (this != null && this != 0) this else fallback
    }

    private data class MenuSections(
        val primary: List<Action>,
        val advanced: List<Action>
    )

    private data class HeaderInfo(
        val title: String?,
        val subtitle: String?
    )

    private fun actionPriority(advancedMode: Boolean, action: Action): Int {
        if (action.isEmpty()) return Int.MAX_VALUE
        return when (menuVariant) {
            HitTestMenuVariant.LINK -> when (action[0].id) {
                SingleAction.LPRESS_OPEN -> 0
                SingleAction.LPRESS_OPEN_NEW -> 1
                SingleAction.PRIVATE -> 2
                SingleAction.LPRESS_COPY_URL -> 3
                SingleAction.LPRESS_COPY_LINK_TEXT -> 4
                SingleAction.LPRESS_SHARE -> 5
                SingleAction.LPRESS_SAVE_PAGE -> 6
                SingleAction.LPRESS_OPEN_BG,
                SingleAction.LPRESS_OPEN_OTHERS,
                SingleAction.LPRESS_OPEN_NEW_RIGHT,
                SingleAction.LPRESS_OPEN_BG_RIGHT,
                SingleAction.LPRESS_SAVE_PAGE_AS,
                SingleAction.LPRESS_PATTERN_MATCH,
                SingleAction.LPRESS_ADD_BLACK_LIST,
                SingleAction.LPRESS_ADD_WHITE_LIST -> if (advancedMode) 0 else 100 + action[0].id
                else -> 200 + action[0].id
            }

            HitTestMenuVariant.IMAGE -> when (action[0].id) {
                SingleAction.LPRESS_OPEN_IMAGE -> 0
                SingleAction.LPRESS_OPEN_IMAGE_NEW -> 1
                SingleAction.PRIVATE -> 2
                SingleAction.LPRESS_COPY_IMAGE_URL -> 3
                SingleAction.LPRESS_SHARE_IMAGE -> 4
                SingleAction.LPRESS_SAVE_IMAGE -> 5
                SingleAction.LPRESS_GOOGLE_IMAGE_SEARCH -> 6
                SingleAction.LPRESS_OPEN_IMAGE_BG,
                SingleAction.LPRESS_OPEN_IMAGE_OTHERS,
                SingleAction.LPRESS_OPEN_IMAGE_NEW_RIGHT,
                SingleAction.LPRESS_OPEN_IMAGE_BG_RIGHT,
                SingleAction.LPRESS_SHARE_IMAGE_URL,
                SingleAction.LPRESS_SAVE_IMAGE_AS,
                SingleAction.LPRESS_PATTERN_MATCH,
                SingleAction.LPRESS_ADD_IMAGE_BLACK_LIST,
                SingleAction.LPRESS_ADD_IMAGE_WHITE_LIST,
                SingleAction.LPRESS_IMAGE_RES_BLOCK -> if (advancedMode) 0 else 100 + action[0].id
                else -> 200 + action[0].id
            }

            HitTestMenuVariant.LINKED_IMAGE -> when (action[0].id) {
                SingleAction.LPRESS_OPEN -> 0
                SingleAction.LPRESS_OPEN_NEW -> 1
                SingleAction.PRIVATE -> 2
                SingleAction.LPRESS_OPEN_IMAGE -> 3
                SingleAction.LPRESS_OPEN_IMAGE_NEW -> 4
                SingleAction.LPRESS_COPY_URL -> 5
                SingleAction.LPRESS_COPY_LINK_TEXT -> 6
                SingleAction.LPRESS_COPY_IMAGE_URL -> 7
                SingleAction.LPRESS_SHARE -> 8
                SingleAction.LPRESS_SHARE_IMAGE -> 9
                SingleAction.LPRESS_SAVE_PAGE -> 10
                SingleAction.LPRESS_SAVE_IMAGE -> 11
                SingleAction.LPRESS_GOOGLE_IMAGE_SEARCH -> 12
                SingleAction.LPRESS_OPEN_BG,
                SingleAction.LPRESS_OPEN_OTHERS,
                SingleAction.LPRESS_OPEN_NEW_RIGHT,
                SingleAction.LPRESS_OPEN_BG_RIGHT,
                SingleAction.LPRESS_OPEN_IMAGE_BG,
                SingleAction.LPRESS_OPEN_IMAGE_OTHERS,
                SingleAction.LPRESS_OPEN_IMAGE_NEW_RIGHT,
                SingleAction.LPRESS_OPEN_IMAGE_BG_RIGHT,
                SingleAction.LPRESS_SHARE_IMAGE_URL,
                SingleAction.LPRESS_SAVE_PAGE_AS,
                SingleAction.LPRESS_SAVE_IMAGE_AS,
                SingleAction.LPRESS_PATTERN_MATCH,
                SingleAction.LPRESS_ADD_BLACK_LIST,
                SingleAction.LPRESS_ADD_WHITE_LIST,
                SingleAction.LPRESS_ADD_IMAGE_BLACK_LIST,
                SingleAction.LPRESS_ADD_IMAGE_WHITE_LIST,
                SingleAction.LPRESS_IMAGE_RES_BLOCK -> if (advancedMode) 0 else 100 + action[0].id
                else -> 200 + action[0].id
            }
        }
    }

    private fun advancedActionIds(variant: HitTestMenuVariant): Set<Int> {
        return when (variant) {
            HitTestMenuVariant.LINK -> linkAdvancedActionIds
            HitTestMenuVariant.IMAGE -> imageAdvancedActionIds
            HitTestMenuVariant.LINKED_IMAGE -> linkedImageAdvancedActionIds
        }
    }

    private fun advancedSectionForAction(action: Action): AdvancedSection {
        if (action.isEmpty()) return AdvancedSection.OPEN_MORE
        return when (action[0].id) {
            SingleAction.LPRESS_OPEN_BG,
            SingleAction.LPRESS_OPEN_OTHERS,
            SingleAction.LPRESS_OPEN_NEW_RIGHT,
            SingleAction.LPRESS_OPEN_BG_RIGHT,
            SingleAction.LPRESS_OPEN_IMAGE_BG,
            SingleAction.LPRESS_OPEN_IMAGE_OTHERS,
            SingleAction.LPRESS_OPEN_IMAGE_NEW_RIGHT,
            SingleAction.LPRESS_OPEN_IMAGE_BG_RIGHT -> AdvancedSection.OPEN_MORE

            SingleAction.LPRESS_SHARE_IMAGE_URL,
            SingleAction.LPRESS_COPY_URL,
            SingleAction.LPRESS_COPY_LINK_TEXT,
            SingleAction.LPRESS_COPY_IMAGE_URL -> AdvancedSection.SHARE_COPY

            SingleAction.LPRESS_SAVE_PAGE_AS,
            SingleAction.LPRESS_SAVE_IMAGE_AS,
            SingleAction.LPRESS_SAVE_PAGE,
            SingleAction.LPRESS_SAVE_IMAGE,
            SingleAction.LPRESS_GOOGLE_IMAGE_SEARCH -> AdvancedSection.DOWNLOAD

            else -> AdvancedSection.SAFETY
        }
    }

    private fun resolveMenuVariant(): HitTestMenuVariant {
        return when (target.result.type) {
            android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE -> HitTestMenuVariant.LINK
            android.webkit.WebView.HitTestResult.IMAGE_TYPE -> HitTestMenuVariant.IMAGE
            else -> HitTestMenuVariant.LINKED_IMAGE
        }
    }

    companion object {
        private val linkAdvancedActionIds = setOf(
            SingleAction.LPRESS_OPEN_BG,
            SingleAction.LPRESS_OPEN_OTHERS,
            SingleAction.LPRESS_OPEN_NEW_RIGHT,
            SingleAction.LPRESS_OPEN_BG_RIGHT,
            SingleAction.LPRESS_SAVE_PAGE_AS,
            SingleAction.LPRESS_PATTERN_MATCH,
            SingleAction.LPRESS_ADD_BLACK_LIST,
            SingleAction.LPRESS_ADD_WHITE_LIST
        )

        private val imageAdvancedActionIds = setOf(
            SingleAction.LPRESS_OPEN_IMAGE_BG,
            SingleAction.LPRESS_OPEN_IMAGE_OTHERS,
            SingleAction.LPRESS_OPEN_IMAGE_NEW_RIGHT,
            SingleAction.LPRESS_OPEN_IMAGE_BG_RIGHT,
            SingleAction.LPRESS_SHARE_IMAGE_URL,
            SingleAction.LPRESS_SAVE_IMAGE_AS,
            SingleAction.LPRESS_PATTERN_MATCH,
            SingleAction.LPRESS_ADD_IMAGE_BLACK_LIST,
            SingleAction.LPRESS_ADD_IMAGE_WHITE_LIST,
            SingleAction.LPRESS_IMAGE_RES_BLOCK
        )

        private val linkedImageAdvancedActionIds = setOf(
            SingleAction.LPRESS_OPEN_BG,
            SingleAction.LPRESS_OPEN_OTHERS,
            SingleAction.LPRESS_OPEN_NEW_RIGHT,
            SingleAction.LPRESS_OPEN_BG_RIGHT,
            SingleAction.LPRESS_OPEN_IMAGE_BG,
            SingleAction.LPRESS_OPEN_IMAGE_OTHERS,
            SingleAction.LPRESS_OPEN_IMAGE_NEW_RIGHT,
            SingleAction.LPRESS_OPEN_IMAGE_BG_RIGHT,
            SingleAction.LPRESS_SHARE_IMAGE_URL,
            SingleAction.LPRESS_SAVE_PAGE_AS,
            SingleAction.LPRESS_SAVE_IMAGE_AS,
            SingleAction.LPRESS_PATTERN_MATCH,
            SingleAction.LPRESS_ADD_BLACK_LIST,
            SingleAction.LPRESS_ADD_WHITE_LIST,
            SingleAction.LPRESS_ADD_IMAGE_BLACK_LIST,
            SingleAction.LPRESS_ADD_IMAGE_WHITE_LIST,
            SingleAction.LPRESS_IMAGE_RES_BLOCK
        )
    }

    private enum class HitTestMenuVariant {
        LINK,
        IMAGE,
        LINKED_IMAGE
    }

    private enum class AdvancedSection(val title: Int) {
        OPEN_MORE(R.string.context_menu_section_open_more),
        SHARE_COPY(R.string.context_menu_section_share_copy),
        DOWNLOAD(R.string.context_menu_section_download),
        SAFETY(R.string.context_menu_section_safety)
    }
}
