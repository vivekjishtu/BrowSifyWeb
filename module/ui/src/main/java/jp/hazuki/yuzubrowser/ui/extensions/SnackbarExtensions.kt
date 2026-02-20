package jp.hazuki.yuzubrowser.ui.extensions

import android.content.res.Configuration
import android.graphics.Color
import com.google.android.material.snackbar.Snackbar

fun Snackbar.applyAppTheme(): Snackbar {
    val mode = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    val isNight = mode == Configuration.UI_MODE_NIGHT_YES

    val backgroundColor = if (isNight) 0xFF323232.toInt() else 0xFFF3F3F3.toInt()
    val textColor = if (isNight) Color.WHITE else 0xFF1D1D1D.toInt()
    val actionColor = if (isNight) 0xFF84FFFF.toInt() else 0xFF00639A.toInt()

    setBackgroundTint(backgroundColor)
    setTextColor(textColor)
    setActionTextColor(actionColor)
    return this
}

