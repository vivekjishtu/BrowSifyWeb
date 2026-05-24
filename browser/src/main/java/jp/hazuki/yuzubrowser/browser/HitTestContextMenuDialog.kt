package jp.hazuki.yuzubrowser.browser

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.action.Action
import jp.hazuki.yuzubrowser.legacy.action.ActionList
import jp.hazuki.yuzubrowser.legacy.action.SingleAction
import jp.hazuki.yuzubrowser.legacy.action.manager.ActionController
import jp.hazuki.yuzubrowser.legacy.browser.BrowserController
import jp.hazuki.yuzubrowser.ui.extensions.decodePunyCodeUrl
import jp.hazuki.yuzubrowser.ui.theme.ThemeData

internal class HitTestContextMenuDialog(
    private val controller: BrowserController,
    private val target: ActionController.HitTestResultTargetInfo,
    private val actionList: ActionList,
    private val onActionClick: (Action) -> Unit
) {
    private val activity = controller.activity
    private val themeData = ThemeData.getInstance()
    private val menuBackgroundColor = themeData?.menuBackgroundColor.orFallback(themeData?.toolbarBackgroundColor ?: 0xFF303134.toInt())
    private val menuBorderColor = themeData?.menuBorderColor.orFallback(themeData?.menuDividerColor ?: adjustColor(menuBackgroundColor, if (themeData?.lightTheme == true) 0.78f else 1.22f))
    private val menuTextColor = themeData?.menuTextColor.orFallback(themeData?.toolbarTextColor ?: 0xFFE8EAED.toInt())
    private val menuIconColor = themeData?.menuIconColor.orFallback(themeData?.toolbarImageColor ?: menuTextColor)
    private val menuDividerColor = themeData?.menuDividerColor.orFallback(adjustColor(menuBackgroundColor, if (themeData?.lightTheme == true) 0.82f else 1.18f))
    private val menuItemPressedColor = themeData?.menuItemPressedColor.orFallback(adjustColor(menuBackgroundColor, if (themeData?.lightTheme == true) 0.94f else 1.10f))
    private val cornerRadius = activity.resources.displayMetrics.density * 18f
    private val titleText = buildTitleText()

    fun show() {
        val sections = splitActions(actionList)
        if (sections.primary.isEmpty() && sections.advanced.isNotEmpty()) {
            showDialog(sections.advanced, emptyList(), true)
        } else {
            showDialog(sections.primary, sections.advanced, false)
        }
    }

    private fun showDialog(primaryActions: List<Action>, advancedActions: List<Action>, advancedMode: Boolean) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_hit_test_context_menu, null, false)
        val root = view.findViewById<View>(R.id.rootContainer)
        val kindView = view.findViewById<TextView>(R.id.kindTextView)
        val titleView = view.findViewById<TextView>(R.id.titleTextView)
        val subtitleView = view.findViewById<TextView>(R.id.subtitleTextView)
        val divider = view.findViewById<View>(R.id.headerDivider)
        val itemsContainer = view.findViewById<LinearLayout>(R.id.itemsContainer)
        val scrollView = view.findViewById<ScrollView>(R.id.itemsScrollView)

        root.background = createSurfaceBackground()
        divider.background = createDividerDrawable()
        styleHeader(kindView, titleView, subtitleView, advancedMode)
        val header = buildHeader(advancedMode)
        kindView.text = header.kind
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

        var dialog: AlertDialog? = null
        val allRows = primaryActions.toMutableList()
        val hasAdvancedShortcut = !advancedMode && advancedActions.isNotEmpty()
        val rowCount = allRows.size + if (hasAdvancedShortcut) 1 else 0

        allRows.forEachIndexed { index, action ->
            val previous = allRows.getOrNull(index - 1)
            val showDivider = previous != null && groupForAction(previous) != groupForAction(action)
            itemsContainer.addView(createActionRow(action, showDivider) {
                dialog?.dismiss()
                onActionClick(action)
            })
        }

        if (hasAdvancedShortcut) {
            itemsContainer.addView(createAdvancedRow(advancedActions, allRows.isNotEmpty()) {
                dialog?.dismiss()
                showDialog(advancedActions, emptyList(), true)
            })
        }

        dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        val maxHeight = (activity.resources.displayMetrics.heightPixels * 0.56f).toInt()
        scrollView.layoutParams = scrollView.layoutParams.apply {
            height = if (rowCount > 7) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT
        }
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

    private fun styleHeader(kindView: TextView, titleView: TextView, subtitleView: TextView, advancedMode: Boolean) {
        kindView.setTextColor(menuTextColor)
        kindView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = activity.resources.displayMetrics.density * 999f
            setColor(if (advancedMode) adjustColor(menuBackgroundColor, if (themeData?.lightTheme == true) 0.9f else 1.12f) else menuItemPressedColor)
            setStroke(1, menuBorderColor)
        }
        titleView.setTextColor(adjustAlpha(menuTextColor, 0.92f))
        subtitleView.setTextColor(adjustAlpha(menuTextColor, 0.68f))
    }

    private fun buildHeader(advancedMode: Boolean): HeaderInfo {
        val kind = activity.getString(
            when (target.result.type) {
                android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE -> R.string.context_menu_kind_link
                android.webkit.WebView.HitTestResult.IMAGE_TYPE -> R.string.context_menu_kind_image
                else -> R.string.context_menu_kind_linked_image
            }
        )

        return if (advancedMode) {
            HeaderInfo(
                kind = kind,
                title = activity.getString(R.string.context_menu_advanced_actions),
                subtitle = titleText
            )
        } else {
            HeaderInfo(
                kind = kind,
                title = null,
                subtitle = titleText
            )
        }
    }

    private fun splitActions(actions: ActionList): MenuSections {
        val primary = ArrayList<Action>()
        val advanced = ArrayList<Action>()
        for (action in actions) {
            if (isAdvancedAction(action)) {
                advanced.add(action)
            } else {
                primary.add(action)
            }
        }
        return MenuSections(primary, advanced)
    }

    private fun isAdvancedAction(action: Action): Boolean {
        if (action.isEmpty()) return false
        return action[0].id in advancedActionIds
    }

    private fun iconResForAction(action: Action): Int? {
        if (action.isEmpty()) return null
        return when (action[0].id) {
            SingleAction.LPRESS_OPEN,
            SingleAction.LPRESS_OPEN_OTHERS,
            SingleAction.LPRESS_OPEN_IMAGE,
            SingleAction.LPRESS_OPEN_IMAGE_OTHERS -> R.drawable.ic_arrow_forward_white_24dp
            SingleAction.LPRESS_OPEN_NEW,
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

    private fun compactUrl(url: String): String {
        return when {
            url.isBlank() -> ""
            url.length <= 70 -> url
            else -> url.take(67) + "\u2026"
        }
    }

    private fun buildTitleText(): String {
        val decodedUrl = (target.result.extra ?: "").decodePunyCodeUrl().orEmpty()
        val uri = runCatching { Uri.parse(decodedUrl) }.getOrNull()
        val host = uri?.host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        return host ?: compactUrl(decodedUrl)
    }

    private fun labelForAction(action: Action): String {
        if (action.isEmpty()) return ""
        return when (action[0].id) {
            SingleAction.LPRESS_OPEN -> activity.getString(R.string.context_menu_open_here)
            SingleAction.LPRESS_OPEN_NEW -> activity.getString(R.string.context_menu_open_new_tab)
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
        return when (action[0].id) {
            SingleAction.LPRESS_OPEN,
            SingleAction.LPRESS_OPEN_NEW,
            SingleAction.LPRESS_OPEN_BG,
            SingleAction.LPRESS_OPEN_OTHERS,
            SingleAction.LPRESS_OPEN_NEW_RIGHT,
            SingleAction.LPRESS_OPEN_BG_RIGHT,
            SingleAction.LPRESS_OPEN_IMAGE,
            SingleAction.LPRESS_OPEN_IMAGE_NEW,
            SingleAction.LPRESS_OPEN_IMAGE_BG,
            SingleAction.LPRESS_OPEN_IMAGE_OTHERS,
            SingleAction.LPRESS_OPEN_IMAGE_NEW_RIGHT,
            SingleAction.LPRESS_OPEN_IMAGE_BG_RIGHT -> 0

            SingleAction.LPRESS_SHARE,
            SingleAction.LPRESS_SHARE_IMAGE,
            SingleAction.LPRESS_SHARE_IMAGE_URL,
            SingleAction.LPRESS_COPY_URL,
            SingleAction.LPRESS_COPY_LINK_TEXT,
            SingleAction.LPRESS_COPY_IMAGE_URL -> 1

            SingleAction.LPRESS_SAVE_PAGE,
            SingleAction.LPRESS_SAVE_PAGE_AS,
            SingleAction.LPRESS_SAVE_IMAGE,
            SingleAction.LPRESS_SAVE_IMAGE_AS,
            SingleAction.LPRESS_GOOGLE_IMAGE_SEARCH -> 2

            else -> 3
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
        val kind: String,
        val title: String?,
        val subtitle: String?
    )

    companion object {
        private val advancedActionIds = setOf(
            SingleAction.LPRESS_PATTERN_MATCH,
            SingleAction.LPRESS_ADD_BLACK_LIST,
            SingleAction.LPRESS_ADD_WHITE_LIST,
            SingleAction.LPRESS_ADD_IMAGE_BLACK_LIST,
            SingleAction.LPRESS_ADD_IMAGE_WHITE_LIST,
            SingleAction.LPRESS_IMAGE_RES_BLOCK
        )
    }
}
