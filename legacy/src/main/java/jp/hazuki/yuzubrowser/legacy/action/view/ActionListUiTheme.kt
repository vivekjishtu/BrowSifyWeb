package jp.hazuki.yuzubrowser.legacy.action.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.card.MaterialCardView
import jp.hazuki.yuzubrowser.ui.theme.ThemeData

internal data class ActionListUiTheme(
    val backgroundColor: Int,
    val surfaceColor: Int,
    val selectedSurfaceColor: Int,
    val textColor: Int,
    val secondaryTextColor: Int,
    val iconColor: Int,
    val dividerColor: Int,
    val accentColor: Int,
    val headerBackgroundColor: Int,
    val headerTextColor: Int,
    val fabTintColor: Int,
    val fabIconTintColor: Int,
    val rippleColor: ColorStateList,
)

internal fun resolveActionListUiTheme(context: Context): ActionListUiTheme {
    val theme = ThemeData.getInstance()
    val light = theme?.lightTheme == true

    val background = theme?.contentBackgroundColor?.takeIf { it != 0 }
        ?: theme?.settingsBackgroundColor?.takeIf { it != 0 }
        ?: if (light) 0xFFF7F7F7.toInt() else 0xFF121212.toInt()
    val text = theme?.contentTextColor?.takeIf { it != 0 }
        ?: theme?.settingsTextColor?.takeIf { it != 0 }
        ?: if (light) 0xFF1F1F1F.toInt() else 0xFFF1F1F1.toInt()
    val summary = theme?.contentSummaryColor?.takeIf { it != 0 }
        ?: theme?.settingsSummaryColor?.takeIf { it != 0 }
        ?: adjustAlpha(text, 0.72f)
    val icon = theme?.contentIconColor?.takeIf { it != 0 }
        ?: theme?.settingsIconColor?.takeIf { it != 0 }
        ?: text
    val divider = theme?.contentDividerColor?.takeIf { it != 0 }
        ?: theme?.settingsDividerColor?.takeIf { it != 0 }
        ?: adjustAlpha(text, if (light) 0.12f else 0.18f)
    val accent = theme?.tabAccentColor?.takeIf { it != 0 }
        ?: theme?.toolbarImageColor?.takeIf { it != 0 }
        ?: if (light) 0xFF1565C0.toInt() else 0xFF90CAF9.toInt()

    val surface = adjustColor(background, if (light) 1.01f else 1.06f)
    val selectedSurface = blend(surface, accent, if (light) 0.08f else 0.14f)
    val headerBackground = blend(background, accent, if (light) 0.12f else 0.18f)
    val headerText = theme?.settingsCategoryColor?.takeIf { it != 0 }
        ?: accent
    val fabTint = accent
    val fabIconTint = if (ThemeData.isColorLight(fabTint)) Color.BLACK else Color.WHITE

    return ActionListUiTheme(
        backgroundColor = background,
        surfaceColor = surface,
        selectedSurfaceColor = selectedSurface,
        textColor = text,
        secondaryTextColor = summary,
        iconColor = icon,
        dividerColor = divider,
        accentColor = accent,
        headerBackgroundColor = headerBackground,
        headerTextColor = headerText,
        fabTintColor = fabTint,
        fabIconTintColor = fabIconTint,
        rippleColor = ColorStateList.valueOf(adjustAlpha(accent, if (light) 0.18f else 0.26f)),
    )
}

internal fun styleActionListHost(
    root: android.view.View,
    recyclerView: androidx.recyclerview.widget.RecyclerView,
    fab: FloatingActionButton,
    theme: ActionListUiTheme,
) {
    root.setBackgroundColor(theme.backgroundColor)
    recyclerView.setBackgroundColor(theme.backgroundColor)
    recyclerView.clipToPadding = false
    fab.backgroundTintList = ColorStateList.valueOf(theme.fabTintColor)
    fab.imageTintList = ColorStateList.valueOf(theme.fabIconTintColor)
}

internal fun styleActionListCard(card: MaterialCardView, selected: Boolean, theme: ActionListUiTheme) {
    card.setCardBackgroundColor(if (selected) theme.selectedSurfaceColor else theme.surfaceColor)
    card.strokeWidth = card.resources.displayMetrics.density.toInt().coerceAtLeast(1)
    card.strokeColor = if (selected) theme.accentColor else theme.dividerColor
    card.rippleColor = theme.rippleColor
}

internal fun styleActionListHeader(card: MaterialCardView, textView: TextView, theme: ActionListUiTheme) {
    card.setCardBackgroundColor(theme.headerBackgroundColor)
    card.strokeWidth = 0
    card.rippleColor = theme.rippleColor
    textView.setTextColor(theme.headerTextColor)
}

internal fun styleActionListItemText(textView: TextView, theme: ActionListUiTheme) {
    textView.setTextColor(theme.textColor)
}

internal fun styleActionListItemIcon(imageView: ImageView, theme: ActionListUiTheme) {
    imageView.imageTintList = ColorStateList.valueOf(theme.iconColor)
}

internal fun styleActionListItemSecondary(view: android.widget.CheckBox, theme: ActionListUiTheme) {
    view.buttonTintList = ColorStateList.valueOf(theme.accentColor)
}

internal fun adjustAlpha(color: Int, alphaFactor: Float): Int {
    val alpha = (Color.alpha(color) * alphaFactor).toInt().coerceIn(0, 255)
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

internal fun adjustColor(color: Int, factor: Float): Int {
    val alpha = Color.alpha(color)
    val red = (Color.red(color) * factor).toInt().coerceIn(0, 255)
    val green = (Color.green(color) * factor).toInt().coerceIn(0, 255)
    val blue = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    return Color.argb(alpha, red, green, blue)
}

internal fun blend(base: Int, overlay: Int, ratio: Float): Int {
    val inverse = 1f - ratio
    val alpha = (Color.alpha(base) * inverse + Color.alpha(overlay) * ratio).toInt().coerceIn(0, 255)
    val red = (Color.red(base) * inverse + Color.red(overlay) * ratio).toInt().coerceIn(0, 255)
    val green = (Color.green(base) * inverse + Color.green(overlay) * ratio).toInt().coerceIn(0, 255)
    val blue = (Color.blue(base) * inverse + Color.blue(overlay) * ratio).toInt().coerceIn(0, 255)
    return Color.argb(alpha, red, green, blue)
}
