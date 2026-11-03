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

package jp.hazuki.yuzubrowser.legacy.speeddial

import android.content.Context
import android.graphics.Color
import android.webkit.WebResourceResponse
import jp.hazuki.yuzubrowser.core.utility.extensions.getNoCacheResponse
import jp.hazuki.yuzubrowser.core.utility.utils.FileUtils
import jp.hazuki.yuzubrowser.core.utility.utils.IOUtils
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.legacy.utils.HtmlUtils
import jp.hazuki.yuzubrowser.ui.settings.AppPrefs
import jp.hazuki.yuzubrowser.ui.theme.ThemeData
import jp.hazuki.yuzubrowser.ui.theme.ThemeDataResolver
import java.io.*
import java.nio.charset.StandardCharsets

class SpeedDialHtml(context: Context) {

    private val cache: File
    private val cacheIndex: File

    private val manager: SpeedDialManager
    private val context: Context = context.applicationContext

    init {
        val cacheFolder = File(context.cacheDir, FOLDER)

        cache = File(cacheFolder, "cache")
        cacheIndex = File(cacheFolder, "index")

        manager = SpeedDialManager(context.applicationContext)
    }

    val baseCss: WebResourceResponse
        get() = WebResourceResponse("text/css", "UTF-8", context.resources.openRawResource(R.raw.speeddial_css))

    val customCss: WebResourceResponse
        get() {
            val builder = StringBuilder(400)

            if (!AppPrefs.speeddial_show_header.get())
                builder.append(".browserName { display:none }")

            if (!AppPrefs.speeddial_show_search.get())
                builder.append("#searchBox { display:none }")

            if (!AppPrefs.speeddial_show_icon.get())
                builder.append(".grid-item img {display:none;}")

            // portrait
            val pColumn = AppPrefs.speeddial_column.get()
            builder.append("@media screen and (orientation:portrait){.linkBox{max-width:")
                .append(AppPrefs.speeddial_column_width.get() * pColumn).append("px}")
            // = (100 - marginSize * (column + 1) * 2 + 2 * marginSize) / column
            builder.append(".box{width:").append((100f - 2f * pColumn) / pColumn).append("%}")
            builder.append(".box:nth-child(n+").append(pColumn + 1).append("){order:1}}")

            // landscape
            val lColumn = AppPrefs.speeddial_column_landscape.get()
            builder.append("@media screen and (orientation:landscape){.linkBox{max-width:")
                .append(AppPrefs.speeddial_column_width.get() * lColumn).append("px}")
            // = (100 - marginSize * (column + 1) * 2 + 2 * marginSize) / column
            builder.append(".box{width:").append((100f - 2f * lColumn) / lColumn).append("%}")
            builder.append(".box:nth-child(n+").append(lColumn + 1).append("){order:1}}")

            val fontSize = AppPrefs.font_size.speeddial_item.get()
            if (fontSize >= 0) {
                builder.append(".name{font-size:").append(getFontSize(fontSize)).append("}")
            }

            val themeData = ThemeDataResolver.resolve(context)
            if (themeData != null) {
                builder.append(buildThemeCss(themeData))
            } else if (AppPrefs.speeddial_dark_theme.get()) {
                builder.append(DARK_THEME)
            }

            when (AppPrefs.speeddial_layout.get()) {
                1 -> builder.append(SMALL_ICON)
            }

            return getNoCacheResponse("text/css", builder)
        }

    private fun createCache(context: Context): CharSequence {
        val time = System.currentTimeMillis()
        val index = manager.indexData
        val builder = StringBuilder(8000)
        // Keep the vendor's brand split independent of the debug app name.
        val brandName = HtmlUtils.sanitize(context.getString(R.string.speed_dial_brand_name))
        val brandAccent = HtmlUtils.sanitize(context.getString(R.string.speed_dial_brand_accent))
        val start = getResourceString(context, R.raw.speeddial_start)
            .replace(BRAND_NAME_TOKEN, brandName)
            .replace(BRAND_ACCENT_TOKEN, brandAccent)
        builder.append(start)

        for ((id, url, title, updateTime) in index) {
            builder.append("<div class=\"grid-item\"><a href=\"")
                .append(url)
                .append("\"><img class=\"favicon\" src=\"bsw:speeddial/img/")
                .append(id)
                .append("?")
                .append(updateTime)
                .append("\" /><span>")
                .append(HtmlUtils.sanitize(title))
                .append("</span></a></div>")
        }

        builder.append(getResourceString(context, R.raw.speeddial_end))

        if (!cache.parentFile!!.exists())
            cache.parentFile!!.mkdirs()

        try {
            FileOutputStream(cache).use { fos -> fos.write(builder.toString().toByteArray(StandardCharsets.UTF_8)) }
        } catch (e: IOException) {
            e.printStackTrace()
            return builder
        }

        try {
            val cacheInfo = "$CACHE_VERSION:$time"
            FileOutputStream(cacheIndex).use { fos -> fos.write(cacheInfo.toByteArray(StandardCharsets.UTF_8)) }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return builder
    }

    fun createResponse(): WebResourceResponse {

        if (cacheIndex.exists() && cache.exists()) {
            try {
                val cacheInfo = IOUtils.readFile(cacheIndex, "UTF-8").split(':')
                val cacheVersion = cacheInfo.getOrNull(0)?.toIntOrNull()
                val time = cacheInfo.getOrNull(1)?.toLongOrNull()
                if (cacheVersion == CACHE_VERSION && time != null && manager.listUpdateTime < time) {
                    return getNoCacheResponse("text/html", FileInputStream(cache))
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
        return getNoCacheResponse("text/html", createCache(context))
    }

    fun getImage(path: String): WebResourceResponse? {
        val index = path.indexOf('?')
        if (index < 14) return null

        val id = path.substring(14, index)
        val image = manager.getImage(id) ?: return null

        return WebResourceResponse("image/png", null, ByteArrayInputStream(image))
    }

    private fun getResourceString(context: Context, id: Int): String {
        return IOUtils.readString(context.resources.openRawResource(id))
    }

    private fun getFontSize(size: Int): String {
        return when (size) {
            0 -> "42%"
            1 -> "50%"
            2 -> "60%"
            3 -> "75%"
            4 -> "88.8%"
            5 -> "100%"
            6 -> "120%"
            else -> "75%"
        }
    }

    private fun buildThemeCss(themeData: ThemeData): String {
        val light = themeData.lightTheme
        val background = themeData.tabListBackgroundColor.takeIf { it != 0 }
            ?: themeData.contentBackgroundColor.takeIf { it != 0 }
            ?: themeData.settingsBackgroundColor.takeIf { it != 0 }
            ?: if (light) 0xFFF1F3F4.toInt() else 0xFF202124.toInt()
        val text = themeData.contentTextColor.takeIf { it != 0 }
            ?: themeData.settingsTextColor.takeIf { it != 0 }
            ?: themeData.toolbarTextColor.takeIf { it != 0 }
            ?: if (light) 0xFF202124.toInt() else 0xFFE8EAED.toInt()
        val summary = themeData.contentSummaryColor.takeIf { it != 0 }
            ?: themeData.settingsSummaryColor.takeIf { it != 0 }
            ?: adjustAlpha(text, 0.72f)
        val accent = themeData.tabAccentColor.takeIf { it != 0 }
            ?: themeData.toolbarImageColor.takeIf { it != 0 }
            ?: if (light) 0xFF1A73E8.toInt() else 0xFF8AB4F8.toInt()
        val itemBg = themeData.tabListItemBackgroundNormalColor.takeIf { it != 0 }
            ?: themeData.contentBackgroundColor.takeIf { it != 0 }
            ?: adjustColor(background, if (light) 1.04f else 1.10f)
        val searchBg = themeData.tabListItemBackgroundSelectedColor.takeIf { it != 0 }
            ?: themeData.menuToolbarButtonColor.takeIf { it != 0 }
            ?: adjustColor(background, if (light) 1.10f else 1.18f)
        val divider = themeData.contentDividerColor.takeIf { it != 0 }
            ?: themeData.settingsDividerColor.takeIf { it != 0 }
            ?: themeData.tabDividerColor.takeIf { it != 0 }
            ?: adjustAlpha(text, if (light) 0.14f else 0.22f)
        val itemShadow = if (light) {
            "0 1px 3px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.24)"
        } else {
            "none"
        }
        val itemBorder = adjustAlpha(accent, if (light) 0.10f else 0.18f)
        val itemHoverShadow = if (light) {
            "0 6px 18px rgba(0,0,0,0.12)"
        } else {
            "0 0 0 1px rgba(255,255,255,0.10)"
        }
        val searchShadow = if (light) {
            "0 1px 6px rgba(32,33,36,0.28)"
        } else {
            "none"
        }
        val searchFocusShadow = if (light) {
            "0 1px 10px rgba(26,115,232,0.22)"
        } else {
            "0 0 0 1px rgba(255,255,255,0.12)"
        }
        val surfaceLine = adjustAlpha(divider, if (light) 0.75f else 0.9f)
        val footerText = themeData.contentSummaryColor.takeIf { it != 0 }
            ?: themeData.settingsSummaryColor.takeIf { it != 0 }
            ?: summary
        val footerHover = text
        val footerFocusBg = adjustAlpha(accent, if (light) 0.14f else 0.18f)
        val footerFocusBorder = adjustAlpha(accent, if (light) 0.28f else 0.36f)

        return buildString {
            append(":root{")
            append("--bg-color:").append(cssColor(background)).append(';')
            append("--text-color:").append(cssColor(text)).append(';')
            append("--theme-accent-color:").append(cssColor(accent)).append(';')
            append("--item-bg:").append(cssColor(itemBg)).append(';')
            append("--item-shadow:").append(itemShadow).append(';')
            append("--search-bg:").append(cssColor(searchBg)).append(';')
            append("--search-border:1px solid ").append(cssColor(divider)).append(';')
            append("--search-shadow:").append(searchShadow).append(';')
            append("--footer-text:").append(cssColor(footerText)).append(';')
            append("--footer-hover:").append(cssColor(footerHover)).append(';')
            append("--footer-focus-bg:").append(cssColor(footerFocusBg)).append(';')
            append("--footer-focus-border:").append(cssColor(footerFocusBorder)).append(';')
            append("}")
            append(".browserName{color:").append(cssColor(text)).append("}")
            append(".brand-name{color:").append(cssColor(text)).append("}")
            append(".brand-accent{color:").append(cssColor(accent)).append("}")
            append(".grid-item{background-color:").append(cssColor(itemBg))
                .append(";box-shadow:").append(itemShadow)
                .append(";border:1px solid ").append(cssColor(itemBorder)).append("}")
            append(".grid-item:hover,.grid-item:focus-within{box-shadow:").append(itemHoverShadow)
                .append(";border-color:").append(cssColor(accent)).append("}")
            append(".grid-item a{color:").append(cssColor(text)).append("}")
            append("#search-bar{background-color:").append(cssColor(searchBg))
                .append(";color:").append(cssColor(text))
                .append(";border:1px solid ").append(cssColor(surfaceLine))
                .append(";box-shadow:").append(searchShadow).append("}")
            append("#search-bar:focus,#search-bar:focus-visible{border-color:").append(cssColor(accent))
                .append(";box-shadow:").append(searchFocusShadow).append("}")
            append("footer a{color:").append(cssColor(footerText)).append("}")
            append("footer a:hover,footer a:focus,footer a:focus-visible,footer a:active{color:")
                .append(cssColor(footerHover))
                .append(";background-color:").append(cssColor(footerFocusBg))
                .append(";outline:1px solid ").append(cssColor(footerFocusBorder))
                .append("}")
        }
    }

    private fun cssColor(color: Int): String {
        val alpha = Color.alpha(color)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return if (alpha >= 255) {
            String.format("#%02X%02X%02X", red, green, blue)
        } else {
            String.format("rgba(%d,%d,%d,%.3f)", red, green, blue, alpha / 255f)
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

    companion object {
        private const val FOLDER = "speeddial"
        private const val CACHE_VERSION = 3

        /** Tokens in speeddial_start replaced with vendor-defined brand segments. */
        private const val BRAND_NAME_TOKEN = "{{BRAND_NAME}}"
        private const val BRAND_ACCENT_TOKEN = "{{BRAND_ACCENT}}"

        private const val DARK_THEME = "body{background-color:#2a2a2a}.browserName{color:#f0f0f0}" +
                ".search{color:#fff;box-shadow:2px 2px 6px rgba(0,0,0,.4);background-color:#707070}" +
                ".box,.search{border:1px solid #101010}.box{background-color:#404040}" +
                ".name{color:#f5f5f5}footer a{color:#afafaf}"

        private const val SMALL_ICON = ".box{position:relative}img{position:absolute;top:0;bottom:0;" +
                "width:20px;margin:auto 3px}.name{width:auto;-webkit-line-clamp:1;margin:10px 0 10px 26px}"

        @JvmStatic
        fun clearCache(context: Context) {
            FileUtils.deleteFile(File(context.cacheDir, FOLDER))
        }
    }
}
